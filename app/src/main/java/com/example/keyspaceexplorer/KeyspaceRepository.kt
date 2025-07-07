package com.example.keyspaceexplorer

import java.math.BigInteger

class KeyspaceRepository(private val redisService: RedisService) {

    fun generateBatch(startIndex: BigInteger, count: Int): List<PrivateKeyItem> {
        val list = mutableListOf<PrivateKeyItem>()
        for (i in 0 until count) {
            val index = startIndex + BigInteger.valueOf(i.toLong())
            val hex = index.toString(16).padStart(64, '0')
            val addresses = BitcoinUtils.deriveAddresses(hex)
            list.add(PrivateKeyItem(index, hex, addresses))
        }
        return list
    }

    suspend fun checkMatches(addresses: List<String>): List<String> {
        return redisService.checkMatches(addresses)
    }

    fun progressToIndex(progress: Double): BigInteger {
        val max = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFE", 16)
        return (max.toBigDecimal() * progress.toBigDecimal()).toBigInteger()
    }
}