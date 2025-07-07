package com.example.keyspaceexplorer

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

    private val _bitLength = MutableStateFlow(0)
    val bitLength: StateFlow<Int> = _bitLength

    private var currentIndex = BigInteger.ONE

    private val redisService = RedisService()
    val isConnecting =
        redisService.isConnecting.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun loadNextBatch() {
        viewModelScope.launch {
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
            }

            _items.value = (_items.value + updatedBatch).takeLast(batchSize * 3)
            currentIndex += BigInteger.valueOf(batchSize.toLong())

            _progress.value = BitcoinUtils.calculateProgress(currentIndex)
            _bitLength.value = BitcoinUtils.calculateBitLength(currentIndex)
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
}
