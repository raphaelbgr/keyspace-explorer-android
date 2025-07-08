package com.example.keyspaceexplorer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigInteger

class KeyspaceViewModel(private val repository: KeyspaceRepository) : ViewModel() {
    private val _items = MutableStateFlow<List<PrivateKeyItem>>(emptyList())
    val items: StateFlow<List<PrivateKeyItem>> = _items

    private val _progress = MutableStateFlow(0.0)
    val progress: StateFlow<Double> = _progress

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _bitLength = MutableStateFlow(0)
    val bitLength: StateFlow<Int> = _bitLength

    private var currentIndex = BigInteger.ONE

    private val _scannedAddressesCount = MutableStateFlow(0)
    val scannedAddressesCount: StateFlow<Int> = _scannedAddressesCount

    private val redisService = RedisService()
    val isConnecting =
        redisService.isConnecting.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        loadNextBatch()
    }

    private fun generateBatch(): List<PrivateKeyItem> {
        val batchSize = MainActivity.Instance.batchSize
        val batch = repository.generateBatch(currentIndex, batchSize)
        currentIndex += BigInteger.valueOf(batchSize.toLong())
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
        viewModelScope.launch {
            try {
                val batch = generateBatch()
                checkMatches(batch)

                _progress.value = BitcoinUtils.calculateProgress(currentIndex)
                _bitLength.value = BitcoinUtils.calculateBitLength(currentIndex)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadNextBatch() {
        viewModelScope.launch {
            try {
                if (_loading.value) return@launch
                _loading.value = true

                val batch = generateBatch()
                val updatedBatch = checkMatches(batch)

                val batchSize = MainActivity.Instance.batchSize
                _items.value = (_items.value + updatedBatch).takeLast(batchSize * 3)

                _progress.value = BitcoinUtils.calculateProgress(currentIndex)
                _bitLength.value = BitcoinUtils.calculateBitLength(currentIndex)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    fun slideToProgress(progress: Float) {
        viewModelScope.launch {
            val target = repository.progressToIndex(progress.toDouble())
            currentIndex = target
            _items.value = emptyList()
            loadSilentNextBatch()
        }
    }

    fun jumpToProgress(progress: Float) {
        viewModelScope.launch {
            val target = repository.progressToIndex(progress.toDouble())
            currentIndex = target
            _items.value = emptyList()
            loadNextBatch()
        }
    }

    fun estimatePage(progress: Float): BigInteger {
        val bitLen = bitLength.value  // Se bitLength for um `State<Int>`
        val totalKeys = BigInteger.TWO.pow(bitLen)

        return (totalKeys.toBigDecimal() * progress.toBigDecimal())
            .toBigInteger()
            .divide(BigInteger.valueOf(MainActivity.Instance.batchSize.toLong()))
    }

    fun setLoading(loading: Boolean) {
        _loading.value = loading
    }
}