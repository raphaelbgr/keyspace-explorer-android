package com.example.keyspaceexplorer

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.keyspaceexplorer.AddressUtils.normalize
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

    private val redisService = RedisService()
    val isConnecting =
        redisService.isConnecting.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        loadNextBatch()
    }

    private fun loadNextBatch() {
        viewModelScope.launch {
            try {
                if (_loading.value) return@launch
                _loading.value = true

                val batchSize = MainActivity.Instance.batchSize
                val batch = repository.generateBatch(currentIndex, batchSize)

                val allAddresses = batch.flatMap { it.addresses }
                val matches = redisService.checkMatches(allAddresses)

                val updatedBatch = batch.map { item ->
                    val hasHit = item.addresses.any { addr ->
                        normalize(addr.address, addr.token) in matches
                    }
                    item.dbHit = hasHit
                    item
                }

                val found = updatedBatch.filter { it.dbHit == true }
                found.forEach {
                    AlertHelper.alertMatch(it)
                    StorageHelper.saveMatch(it)
                    TelegramHelper.sendAlert(it)
                    LogHelper.logMatch(it)
                    ToastHelper.showToast(it)
                }

                _items.value = (_items.value + updatedBatch).takeLast(batchSize * 3)
                currentIndex += BigInteger.valueOf(batchSize.toLong())

                _progress.value = BitcoinUtils.calculateProgress(currentIndex)
                _bitLength.value = BitcoinUtils.calculateBitLength(currentIndex)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
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
        // Suponha que o total de chaves seja 2^bitLength
        val totalKeys = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            BigInteger.TWO.pow(bitLength.value)
        } else {
            TODO("VERSION.SDK_INT < TIRAMISU")
        }
        return (totalKeys.toBigDecimal() * progress.toBigDecimal())
            .toBigInteger()
            .divide(BigInteger.valueOf(MainActivity.Instance.batchSize.toLong()))
    }
}
