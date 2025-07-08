package com.example.keyspaceexplorer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
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

//    private val _progress = MutableStateFlow(0.0)
//    val progress: StateFlow<Double> = _progress

    private val _summary = MutableStateFlow("📊 Match com DB: ❌ Nenhum")
    val summary: StateFlow<String> = _summary

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _bitLength = MutableStateFlow(0)
    val bitLength: StateFlow<Int> = _bitLength

    private var currentIndex = BigInteger.ONE

    // Intervalo configurável do keyspace
    private var rangeStart: BigInteger = BitcoinUtils.MIN_KEYSPACE
    private var rangeEnd: BigInteger = BitcoinUtils.MAX_KEYSPACE

    private val _scannedAddressesCount = MutableStateFlow(0)
    val scannedAddressesCount: StateFlow<Int> = _scannedAddressesCount

    private val redisService = RedisService()
    val isConnecting =
        redisService.isConnecting.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val slideChannel = Channel<Float>(Channel.UNLIMITED)
    private val slideQueue = mutableListOf<Float>()

    init {
        // Inicia consumidor que processa 1 por vez
        viewModelScope.launch(Dispatchers.IO) {
            slideChannel.consumeEach { normalizedProgress ->
                val newIndex = progressInRange(normalizedProgress)
                currentIndex = newIndex
                _items.value = emptyList()
                loadSilentNextBatch()
            }
        }

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

//        _items.value = emptyList()
        loadNextBatch()
    }

    private fun progressInRange(progress: Float): BigInteger {
        val percent = progress.toBigDecimal()
        val range = rangeEnd - rangeStart
        return rangeStart + (range.toBigDecimal().multiply(percent)).toBigInteger()
    }

    private fun generateBatch(): List<PrivateKeyItem> {
        val batchSize = MainActivity.Instance.batchSize

        if (currentIndex < rangeStart) {
            currentIndex = rangeStart
        }

        if (currentIndex >= rangeEnd) {
            // Volta para a última página válida possível dentro do range
            currentIndex = (rangeEnd - BigInteger.ONE).subtract(BigInteger.valueOf(batchSize.toLong() - 1))
                .coerceAtLeast(rangeStart)
        }

        val upperLimit = (currentIndex + BigInteger.valueOf(batchSize.toLong())).coerceAtMost(rangeEnd)
        val actualBatchSize = (upperLimit - currentIndex).toInt()
        if (actualBatchSize <= 0) return emptyList()

        val batch = repository.generateBatch(currentIndex, actualBatchSize)
        currentIndex += BigInteger.valueOf(actualBatchSize.toLong())
        return batch
    }

    private suspend fun checkMatches(batch: List<PrivateKeyItem>): List<PrivateKeyItem> {
        val allAddresses = batch.flatMap { it.addresses }
        allAddresses.map { it.address = normalizeAddress(it.address, it.token) }

        // Obtem os endereços normalizados que deram match
        val matchedSet = redisService.checkMatches(allAddresses)

        // Atualiza cada item com matched e dbHit
        val updatedBatch = batch.map { item ->
            val matched = item.addresses.filter {
                normalizeAddress(it.address, it.token) in matchedSet
            }
            item.dbHit = matched.isNotEmpty()
            item.matched = matched
            item
        }

        // Executa alertas e persistência para os que tiveram hit
        val found = updatedBatch.filter { it.dbHit == true }
        found.forEach {
            val alreadyExists = StorageHelper.alreadySaved(it)
            if (!alreadyExists) {
                AlertHelper.alertMatch(it)
                StorageHelper.saveMatch(it)
                TelegramHelper.sendAlert(it)
                LogHelper.logMatch(it)
                ToastHelper.showToast(it)
            } else {
                Log.d("MATCH", "🔁 Match duplicado ignorado: ${it.hex}")
            }
        }

        _scannedAddressesCount.value += allAddresses.size

        return updatedBatch
    }

    private fun loadSilentNextBatch() {
        viewModelScope.launch(Dispatchers.IO)  {
            try {
                val batch = generateBatch()
                checkMatches(batch)

//                _progress.value = BitcoinUtils.calculateProgress(currentIndex)
//                _bitLength.value = BitcoinUtils.calculateBitLength(currentIndex)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadNextBatch() {
        viewModelScope.launch(Dispatchers.Main)  {
            try {
                if (_loading.value) return@launch
                _loading.value = true

                val batch = generateBatch()
                val updatedBatch = checkMatches(batch)

//                val batchSize = MainActivity.Instance.batchSize
//                _items.value = (_items.value + updatedBatch).takeLast(batchSize)
                _items.value = updatedBatch

                val hits = _items.value.count { it.dbHit == true }
                _summary.value = if (hits == 0) {
                    "📊 Match com DB: ❌ Nenhum"
                } else {
                    "📊 Match com DB: ✅ $hits encontrados"
                }

//                _progress.value = BitcoinUtils.calculateProgress(currentIndex)
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
        viewModelScope.launch(Dispatchers.IO) {
            val queueSnapshot: List<Float>

            synchronized(slideQueue) {
                queueSnapshot = slideQueue.toList()
                slideQueue.clear()
            }

            for (progress in queueSnapshot) {
//                val newIndex = progressInRange(progress)
//                currentIndex = newIndex
//                _items.value = emptyList()
                loadSilentNextBatch()
            }
        }
    }

    fun slideToProgressInRange(normalizedProgress: Float) {
//        viewModelScope.launch {
//            val newIndex = progressInRange(normalizedProgress)
//            currentIndex = newIndex
//            _items.value = emptyList()
//            loadSilentNextBatch()
//        }
//        slideChannel.trySend(normalizedProgress)
        synchronized(slideQueue) {
            slideQueue.add(normalizedProgress)
        }
    }

    fun jumpToProgressInRange(normalizedProgress: Float) {
        // minimum precision = progressInRange(0.000000000000000000000000000000000000000000001f)
        viewModelScope.launch {
            val newIndex = progressInRange(normalizedProgress)
            currentIndex = newIndex
//            _items.value = emptyList()
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
            .divide(range.toBigDecimal(), 10, java.math.RoundingMode.HALF_UP)
            .toDouble()
    }
}