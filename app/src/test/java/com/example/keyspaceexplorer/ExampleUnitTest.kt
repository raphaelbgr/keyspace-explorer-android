package com.example.keyspaceexplorer

import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

    private val testPrivKeys = listOf(
        "0000000000000000000000000000000000000000000000000000000000000001",
        "00000000000000000000000000000000000000000000000000000000000000FF",
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364140"
    )

    @Test
    fun testLegacyUncompressedP2PKH() {
        for (hex in testPrivKeys) {
            val priv = BigInteger(hex, 16)
            val address = LegacyUncompressedUtils.deriveP2PKH(priv, 0x00)
            println("Legacy P2PKH: $address")
            assertTrue(address.startsWith("1"))
        }
    }

    @Test
    fun testAllBipDerivations() {
        val derivations = listOf(Bip44, Bip49, Bip84, Bip86)
        val coins = listOf(
            Bip44Coins.BITCOIN,
            Bip44Coins.ETHEREUM,
            Bip44Coins.LITECOIN,
            Bip44Coins.DOGECOIN,
            Bip44Coins.DASH,
            Bip44Coins.ZCASH,
            Bip44Coins.BITCOIN_CASH
        )

        for (hex in testPrivKeys) {
            for (derivation in derivations) {
                for (coin in coins) {
                    val result = derivation.derive(hex, coin)
                    println("${derivation::class.simpleName} - $coin: $result")
                    assertTrue(result.isNotBlank())
                }
            }
        }
    }

    @Test
    fun testAllDerivations() {
//        val hex = "0000000000000000000000000000000000000000000000000000000000000001"
        val hex = "0C28FCA386C7A2276AC919E0EAE2B0E0E4A89A2B"

        val derivations = listOf(
            "BITCOIN" to listOf(
                "P2PKH" to { LegacyUncompressedUtils.deriveP2PKH(hex.toBigInteger(16), 0x00) },
                "UNCOMPRESSED" to { LegacyUncompressedUtils.deriveUncompressedPrivKey(hex).joinToString("") { "%02x".format(it) } },
                "BIP44" to { Bip44.derive(hex, "BITCOIN") },
                "BIP49" to { Bip49.derive(hex, "BITCOIN") },
                "BIP84" to { Bip84.derive(hex, "BITCOIN") },
                "BIP86" to { Bip86.derive(hex, "BITCOIN") },
            ),
            "ETHEREUM" to listOf(
                "BIP44" to { Bip44.derive(hex, "ETHEREUM") },
                "BIP49" to { Bip49.derive(hex, "ETHEREUM") },
                "BIP84" to { Bip84.derive(hex, "ETHEREUM") },
                "BIP86" to { Bip86.derive(hex, "ETHEREUM") },
            ),
            "LITECOIN" to listOf(
                "BIP44" to { Bip44.derive(hex, "LITECOIN") },
                "BIP49" to { Bip49.derive(hex, "LITECOIN") },
                "BIP84" to { Bip84.derive(hex, "LITECOIN") },
                "BIP86" to { Bip86.derive(hex, "LITECOIN") },
            ),
            "DOGECOIN" to listOf(
                "BIP44" to { Bip44.derive(hex, "DOGECOIN") },
                "BIP49" to { Bip49.derive(hex, "DOGECOIN") },
                "BIP84" to { Bip84.derive(hex, "DOGECOIN") },
                "BIP86" to { Bip86.derive(hex, "DOGECOIN") },
            ),
            "DASH" to listOf(
                "BIP44" to { Bip44.derive(hex, "DASH") },
                "BIP49" to { Bip49.derive(hex, "DASH") },
                "BIP84" to { Bip84.derive(hex, "DASH") },
                "BIP86" to { Bip86.derive(hex, "DASH") },
            ),
            "ZCASH" to listOf(
                "BIP44" to { Bip44.derive(hex, "ZCASH") },
                "BIP49" to { Bip49.derive(hex, "ZCASH") },
                "BIP84" to { Bip84.derive(hex, "ZCASH") },
                "BIP86" to { Bip86.derive(hex, "ZCASH") },
            ),
            "BITCOIN_CASH" to listOf(
                "BIP44" to { Bip44.derive(hex, "BITCOIN_CASH") },
                "BIP49" to { Bip49.derive(hex, "BITCOIN_CASH") },
                "BIP84" to { Bip84.derive(hex, "BITCOIN_CASH") },
                "BIP86" to { Bip86.derive(hex, "BITCOIN_CASH") },
            ),
        )

        for ((token, variants) in derivations) {
            for ((variant, func) in variants) {
                val result = try {
                    func()
                } catch (e: Exception) {
                    println("❌ ERRO [$token / $variant] → ${e.message}")
                    null
                }
                println("[$token / $variant] → Resultado: $result")
                assertTrue("Derivação inválida para $token $variant com hex $hex", !result.isNullOrBlank())
            }
        }
    }
}