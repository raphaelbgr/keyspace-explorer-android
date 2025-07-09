package com.example.keyspaceexplorer

import android.util.Log
import com.example.keyspaceexplorer.TokenConfig.TOKENS
import java.math.BigInteger

class KeyspaceRepository {

    fun generateBatch(startIndex: BigInteger, count: Int): List<PrivateKeyItem> {
        val list = mutableListOf<PrivateKeyItem>()

        for (i in 0 until count) {
            val index = startIndex + BigInteger.valueOf(i.toLong())
            val hex = index.toString(16).padStart(64, '0')

            val allAddresses = deriveAllFromHex(hex)
            list.add(PrivateKeyItem(index, hex, allAddresses))
        }

        return list
    }

    fun deriveAllFromHex(hex: String, normalize: Boolean = false): List<CryptoAddress> {
        val privKey = BigInteger(hex, 16)
        return deriveAllAddresses(privKey, hex, normalize)
    }

    fun deriveAllAddresses(privKey: BigInteger, hex: String, normalize: Boolean = false): List<CryptoAddress> {
        val allAddresses = mutableListOf<CryptoAddress>()

        for ((token, variants) in TOKENS) {
            for ((variant, coinVariant) in variants) {
                val address = when (coinVariant) {
                    is CoinVariant.UncompressedVariant -> {
                        try {
                            LegacyUncompressedUtils.deriveP2PKH(privKey, netVersions[token]!![0])
                        } catch (_: Exception) {
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
                            Log.e("Derive", "Erro ao derivar $token/$variant: $hex", e)
                            null
                        }
                    }
                }

                if (address != null) {
                    allAddresses.add(
                        CryptoAddress(
                            token = token,
                            variant = variant,
                            address = if (normalize) normalizeAddress(address, token) else address,
                            balanceToken = 0.0,
                            balanceTokenFormatted = "0.00000000",
                            balanceUsd = 0.0,
                        )
                    )
                }
            }
        }

        return allAddresses
    }

    fun normalizeAddress(address: String, coin: String): String {
        return when (coin.lowercase()) {
            "ETH".lowercase() -> address.lowercase().removePrefix("0x")
            "BCH".lowercase() -> address.removePrefix("bitcoincash:")
            else -> address
        }
    }
}