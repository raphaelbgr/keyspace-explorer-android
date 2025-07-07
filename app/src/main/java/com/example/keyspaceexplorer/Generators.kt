package com.example.keyspaceexplorer

import org.bitcoinj.core.Base58
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import java.math.BigInteger
import java.security.MessageDigest
import org.bitcoinj.core.ECKey
import org.bitcoinj.params.MainNetParams

object LegacyUncompressedUtils {

    fun deriveP2PKH(privKey: BigInteger, netVersion: Byte): String {
        val pubKey = deriveUncompressedPrivKey(privKey.toString(16))
        return pubKeyToP2PKH(pubKey, netVersion)
    }

    fun pubKeyToP2PKH(pubKey: ByteArray, netVersion: Byte = 0x00): String {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(pubKey)
        val ripemd160 = RIPEMD160Digest().apply { update(sha256, 0, sha256.size) }
        val out = ByteArray(20)
        ripemd160.doFinal(out, 0)

        val payload = byteArrayOf(netVersion) + out
        val checksum = MessageDigest.getInstance("SHA-256").digest(
            MessageDigest.getInstance("SHA-256").digest(payload)
        ).take(4).toByteArray()

        return Base58.encode(payload + checksum)
    }

    fun deriveUncompressedPrivKey(hex: String): ByteArray {
        val pk = BigInteger(hex, 16)
        val curve = org.bouncycastle.crypto.ec.CustomNamedCurves.getByName("secp256k1")
        val point = curve.g.multiply(pk).normalize()
        return byteArrayOf(0x04) + point.xCoord.encoded + point.yCoord.encoded
    }
}

interface BipDeriver {
    fun derive(hex: String, coinType: String): String
}

object Bip44 : BipDeriver {
    override fun derive(hex: String, coinType: String): String {
        return BipDeriverHelper.deriveFromHex(hex, coinType, DerivationType.BIP44)
    }
}

object Bip49 : BipDeriver {
    override fun derive(hex: String, coinType: String): String {
        return BipDeriverHelper.deriveFromHex(hex, coinType, DerivationType.BIP49)
    }
}

object Bip84 : BipDeriver {
    override fun derive(hex: String, coinType: String): String {
        return BipDeriverHelper.deriveFromHex(hex, coinType, DerivationType.BIP84)
    }
}

object Bip86 : BipDeriver {
    override fun derive(hex: String, coinType: String): String {
        return BipDeriverHelper.deriveFromHex(hex, coinType, DerivationType.BIP86)
    }
}

enum class DerivationType { BIP44, BIP49, BIP84, BIP86 }

object BipDeriverHelper {
    fun deriveFromHex(hex: String, coinType: String, type: DerivationType): String {
        // Você pode usar bitcoinj ou derivação real BIP com seed se quiser
        val key = ECKey.fromPrivate(BigInteger(hex, 16))
        return when (coinType) {
            "BITCOIN", "LITECOIN", "DOGECOIN", "DASH", "ZCASH", "BITCOIN_CASH" -> {
                // Simplesmente gera um endereço legado (P2PKH) como placeholder
                org.bitcoinj.core.LegacyAddress.fromKey(MainNetParams.get(), key).toString()
            }
            "ETHEREUM" -> {
                "0x" + run {
                    val uncompressedPubKey = key.decompress().pubKey
                    val pubKeyNoPrefix = uncompressedPubKey.copyOfRange(1, uncompressedPubKey.size) // remove 0x04 prefix
                    val digest = MessageDigest.getInstance("KECCAK-256").digest(pubKeyNoPrefix)
                    digest.takeLast(20).joinToString("") { "%02x".format(it) }
                }
            }
            else -> "unknown"
        }
    }
}

object Bip44Coins {
    const val BITCOIN = "BITCOIN"
    const val ETHEREUM = "ETHEREUM"
    const val LITECOIN = "LITECOIN"
    const val DOGECOIN = "DOGECOIN"
    const val DASH = "DASH"
    const val ZCASH = "ZCASH"
    const val BITCOIN_CASH = "BITCOIN_CASH"
}