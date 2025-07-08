package com.example.keyspaceexplorer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

class KeyspaceViewModel(private val repository: KeyspaceRepository) : ViewModel() {

    private val _items = MutableStateFlow<List<PrivateKeyItem>>(emptyList())
    val items: StateFlow<List<PrivateKeyItem>> = _items

    private val _currentBackgroundScanCount = MutableStateFlow(0)
    val currentBackgroundScanCount: StateFlow<Int> = _currentBackgroundScanCount

    private val _totalBackgroundToScan = MutableStateFlow(0)
    val totalBackgroundToScan: StateFlow<Int> = _totalBackgroundToScan

    private val _summary = MutableStateFlow("\ud83d\udcca Match com DB: \u274c Nenhum")
    val summary: StateFlow<String> = _summary

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _bitLength = MutableStateFlow(0)
    val bitLength: StateFlow<Int> = _bitLength

    private var currentIndex = BigInteger.ONE
    private var rangeStart: BigInteger = BitcoinUtils.MIN_KEYSPACE
    private var rangeEnd: BigInteger = BitcoinUtils.MAX_KEYSPACE

    private val _scannedAddressesCount = MutableStateFlow(0)
    val scannedAddressesCount: StateFlow<Int> = _scannedAddressesCount

    private val redisService = RedisService()
    val isConnecting = redisService.isConnecting.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val slideQueue = mutableListOf<Float>()

    data class ScanParams(
        val currentIndex: BigInteger,
        val rangeStart: BigInteger,
        val rangeEnd: BigInteger,
        val batchSize: Int
    )

    init {
        loadNextBatch()
    }

    fun updateKeyspaceRange(start: BigInteger, end: BigInteger, retainProgress: Boolean = true) {
        val previousProgress = if (retainProgress) calculateRelativeProgress().toBigDecimal() else BigDecimal.ZERO

        rangeStart = start
        rangeEnd = end

        currentIndex = if (retainProgress) {
            val range = rangeEnd - rangeStart
            rangeStart + (range.toBigDecimal().multiply(previousProgress)).toBigInteger()
        } else {
            rangeStart
        }

        loadNextBatch()
    }

    private fun progressInRange(progress: Float): BigInteger {
        val percent = progress.toBigDecimal()
        val range = rangeEnd - rangeStart
        return rangeStart + (range.toBigDecimal().multiply(percent)).toBigInteger()
    }

    private fun generateBatch(params: ScanParams): List<PrivateKeyItem> {
        var index = params.currentIndex
        if (index < params.rangeStart) index = params.rangeStart
        if (index >= params.rangeEnd) {
            index = (params.rangeEnd - BigInteger.ONE).subtract(BigInteger.valueOf(params.batchSize.toLong() - 1))
                .coerceAtLeast(params.rangeStart)
        }

        val upperLimit = (index + BigInteger.valueOf(params.batchSize.toLong())).coerceAtMost(params.rangeEnd)
        val actualBatchSize = (upperLimit - index).toInt()
        if (actualBatchSize <= 0) return emptyList()

        currentIndex = index + BigInteger.valueOf(actualBatchSize.toLong())
        return repository.generateBatch(index, actualBatchSize)
    }

    private suspend fun checkMatches(batch: List<PrivateKeyItem>): List<PrivateKeyItem> {
        val allAddresses = batch.flatMap { it.addresses }
        allAddresses.forEach { it.address = normalizeAddress(it.address, it.token) }

        val matchedSet = redisService.checkMatches(allAddresses)
        _currentBackgroundScanCount.value += allAddresses.size

        val updatedBatch = batch.map { item ->
            val matched = item.addresses.filter { normalizeAddress(it.address, it.token) in matchedSet }
            item.dbHit = matched.isNotEmpty()
            item.matched = matched
            item
        }

        updatedBatch.filter { it.dbHit == true }.forEach {
            if (!StorageHelper.alreadySaved(it)) {
                AlertHelper.alertMatch(it)
                StorageHelper.saveMatch(it)
                TelegramHelper.sendAlert(it)
                LogHelper.logMatch(it)
                ToastHelper.showToast(it)
            } else {
                Log.d("MATCH", "\ud83d\udd01 Match duplicado ignorado: ${it.hex}")
            }
        }

        _scannedAddressesCount.value += allAddresses.size
        return updatedBatch
    }

    private fun loadSilentNextBatch(params: ScanParams) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val batch = generateBatch(params)
                checkMatches(batch)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadNextBatch() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                if (_loading.value) return@launch
                _loading.value = true

                val params = ScanParams(
                    currentIndex = currentIndex,
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd,
                    batchSize = MainActivity.Instance.batchSize
                )

                val batch = generateBatch(params)
                val updatedBatch = checkMatches(batch)

                _items.value = updatedBatch
                _summary.value = if (updatedBatch.count { it.dbHit == true } == 0) {
                    "\ud83d\udcca Match com DB: \u274c Nenhum"
                } else {
                    "\ud83d\udcca Match com DB: \u2705 ${updatedBatch.count { it.dbHit == true }} encontrados"
                }

                _bitLength.value = BitcoinUtils.calculateBitLength(currentIndex)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                processSlideQueue()
                _loading.value = false
            }
        }
    }

    fun processSlideQueue() {
        val paramsSnapshot = ScanParams(
            currentIndex = currentIndex,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            batchSize = MainActivity.Instance.batchSize
        )

        viewModelScope.launch(Dispatchers.IO) {
            val queueSnapshot = synchronized(slideQueue) {
                slideQueue.toList().also { slideQueue.clear() }
            }

            for (progress in queueSnapshot) {
                val newIndex = progressInRange(progress)
                val updatedParams = paramsSnapshot.copy(currentIndex = newIndex)
                loadSilentNextBatch(updatedParams)
            }
        }
    }

    fun slideToProgressInRange(normalizedProgress: Float) {
        synchronized(slideQueue) {
            slideQueue.add(normalizedProgress)
            val newBatchCount = MainActivity.Instance.batchSize * 17
            if (_currentBackgroundScanCount.value < _totalBackgroundToScan.value) {
                _totalBackgroundToScan.value += newBatchCount
            } else {
                _totalBackgroundToScan.value = newBatchCount
                _currentBackgroundScanCount.value = 0
            }
        }
    }

    fun jumpToProgressInRange(normalizedProgress: Float) {
        viewModelScope.launch {
            val newIndex = progressInRange(normalizedProgress)
            currentIndex = newIndex
            loadNextBatch()
        }
    }

    fun estimatePage(progress: Float): BigInteger {
        val totalKeys = rangeEnd - rangeStart
        return (totalKeys.toBigDecimal().multiply(progress.toBigDecimal()))
            .divide(BigDecimal.valueOf(MainActivity.Instance.batchSize.toLong()), RoundingMode.HALF_UP)
            .toBigInteger()
    }

    fun setLoading(loading: Boolean) {
        _loading.value = loading
    }

    fun calculateRelativeProgress(): Double {
        val range = rangeEnd - rangeStart
        if (range == BigInteger.ZERO) return 0.0
        val position = currentIndex - rangeStart
        return position.toBigDecimal()
            .divide(range.toBigDecimal(), 10, RoundingMode.HALF_UP)
            .toDouble()
    }
}