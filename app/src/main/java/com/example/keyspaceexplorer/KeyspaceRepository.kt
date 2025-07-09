package com.example.keyspaceexplorer

import com.example.keyspaceexplorer.TokenConfig.TOKENS
import java.math.BigInteger

class KeyspaceRepository {

    fun generateBatch(startIndex: BigInteger, count: Int): List<PrivateKeyItem> {
        val list = mutableListOf<PrivateKeyItem>()

        for (i in 0 until count) {
            val index = startIndex + BigInteger.valueOf(i.toLong())
            val hex = index.toString(16).padStart(64, '0')
            val privKey = BigInteger(hex, 16)
            val allAddresses = mutableListOf<CryptoAddress>()

            for ((token, variants) in TOKENS) {
                for ((variant, coinVariant) in variants) {
                    val address = when (coinVariant) {
                        is CoinVariant.UncompressedVariant -> {
                            try {
                                LegacyUncompressedUtils.deriveP2PKH(privKey, netVersions[token]!![0])
                            } catch (e: Exception) {
                                null
                            }
                        }

                        is CoinVariant.BipVariant -> {
                            try {
                                val bipClass = when (coinVariant.bip) {
                                    "Bip44" -> Bip44
                                    "Bip49" -> Bip49
                                    "Bip84" -> Bip84
                                    "Bip86" -> Bip86
                                    else -> null
                                }
                                bipClass?.derive(hex, coinVariant.coin)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                null
                            }
                        }
                    }

                    if (address != null) {
                        allAddresses.add(
                            CryptoAddress(
                                token = token,
                                variant = variant,
                                address = address,
                                balanceToken = 0.0,
                                balanceUsd = 0.0,
                            )
                        )
                    }
                }
            }

            list.add(PrivateKeyItem(index, hex, allAddresses))
        }

        return list
    }
}