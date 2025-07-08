package com.example.keyspaceexplorer

import org.bitcoinj.core.Base58
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.crypto.HDUtils
import org.bitcoinj.params.MainNetParams
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.jcajce.provider.digest.Keccak
import java.math.BigInteger
import java.security.MessageDigest

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
        return when (coinType) {
            Bip44Coins.ETHEREUM -> {
                val key = ECKey.fromPrivate(BigInteger(hex, 16)).decompress()
                val pubKey = key.pubKey.copyOfRange(1, key.pubKey.size)
                val digest = Keccak.Digest256().digest(pubKey)
                "0x" + digest.takeLast(20).joinToString("") { "%02x".format(it) }
            }
            else -> {
                val seed = hexToSeed(hex)
                val rootKey = HDKeyDerivation.createMasterPrivateKey(seed)
                val path = when (type) {
                    DerivationType.BIP44 -> "44H/0H/0H/0/0"
                    DerivationType.BIP49 -> "49H/0H/0H/0/0"
                    DerivationType.BIP84 -> "84H/0H/0H/0/0"
                    DerivationType.BIP86 -> "86H/0H/0H/0/0"
                }
                val childKey = HDUtils.parsePath(path).fold(rootKey) { key, child ->
                    HDKeyDerivation.deriveChildKey(key, child)
                }
                LegacyAddress.fromKey(MainNetParams.get(), childKey).toString()
            }
        }
    }

    private fun hexToSeed(hex: String): ByteArray {
        val paddedHex = hex.padStart(64, '0')
        return BigInteger(paddedHex, 16).toByteArray().let {
            if (it.size < 64) ByteArray(64 - it.size) + it else it
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