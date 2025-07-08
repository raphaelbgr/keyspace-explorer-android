package com.example.keyspaceexplorer

import java.math.BigInteger

data class PrivateKeyItem(
    val index: BigInteger,
    val hex: String,
    val addresses: List<CryptoAddress>,
    var dbHit: Boolean? = null,
    var matched: List<CryptoAddress>? = null,
)

data class CryptoAddress(
    val token: String,        // Ex: "BTC", "ETH"
    val variant: String,      // Ex: "P2PKH", "Bech32", etc.
    var address: String,
) {
    fun variantPretty() = when (this.variant) {
        "P2PKH" -> "compressed"
        "P2PKH_UNCOMPRESSED" -> "uncompressed"
        "P2SH" -> "p2sh"
        "Taproot" -> "taproot"
        "Bech32" -> "bech32"
        else -> ""
    }

    fun fullAddressPretty(): String {
        if (variantPretty().isEmpty()) {
            return "[$token] $address"
        } else {
            return "[$token - ${variantPretty()}] $address"
        }
    }
}

sealed class CoinVariant {
    abstract val name: String
    abstract val coin: String

    data class BipVariant(val bip: String, val coinType: String) : CoinVariant() {
        override val name = bip
        override val coin = coinType
    }

    data class UncompressedVariant(val coinType: String) : CoinVariant() {
        override val name = "UNCOMPRESSED"
        override val coin = coinType
    }
}

fun normalizeAddress(address: String, coin: String): String {
    return when (coin) {
        "ETH" -> address.lowercase().removePrefix("0x")
        "BCH" -> address.removePrefix("bitcoincash:")
        else -> address
    }
}

object TokenConfig {
    val TOKENS: Map<String, Map<String, CoinVariant>> = mapOf(
        "BTC" to mapOf(
            "P2PKH" to CoinVariant.BipVariant("Bip44", "BITCOIN"),
            "P2PKH_UNCOMPRESSED" to CoinVariant.UncompressedVariant("BITCOIN"),
            "P2SH" to CoinVariant.BipVariant("Bip49", "BITCOIN"),
            "Bech32" to CoinVariant.BipVariant("Bip84", "BITCOIN"),
            "Taproot" to CoinVariant.BipVariant("Bip86", "BITCOIN")
        ),
        "ETH" to mapOf(
            "ETH" to CoinVariant.BipVariant("Bip44", "ETHEREUM")
        ),
        "LTC" to mapOf(
            "P2PKH" to CoinVariant.BipVariant("Bip44", "LITECOIN"),
            "P2PKH_UNCOMPRESSED" to CoinVariant.UncompressedVariant("LITECOIN"),
            "P2SH" to CoinVariant.BipVariant("Bip49", "LITECOIN"),
            "Bech32" to CoinVariant.BipVariant("Bip84", "LITECOIN")
        ),
        "DOGE" to mapOf(
            "P2PKH" to CoinVariant.BipVariant("Bip44", "DOGECOIN"),
            "P2PKH_UNCOMPRESSED" to CoinVariant.UncompressedVariant("DOGECOIN")
        ),
        "ZEC" to mapOf(
            "ZEC" to CoinVariant.BipVariant("Bip44", "ZCASH")
        ),
        "DASH" to mapOf(
            "P2PKH" to CoinVariant.BipVariant("Bip44", "DASH"),
            "P2PKH_UNCOMPRESSED" to CoinVariant.UncompressedVariant("DASH")
        ),
        "BCH" to mapOf(
            "BCH" to CoinVariant.BipVariant("Bip44", "BITCOIN_CASH"),
            "BCH_UNCOMPRESSED" to CoinVariant.UncompressedVariant("BITCOIN_CASH")
        )
    )
}

val netVersions: Map<String, ByteArray> = mapOf(
    "BTC" to byteArrayOf(0x00),
    "LTC" to byteArrayOf(0x30),
    "DOGE" to byteArrayOf(0x1e),
    "DASH" to byteArrayOf(0x4c),
    "ZEC" to byteArrayOf(0x1c),
    "BCH" to byteArrayOf(0x00)
)

