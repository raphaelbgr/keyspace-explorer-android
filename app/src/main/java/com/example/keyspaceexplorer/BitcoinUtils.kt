package com.example.keyspaceexplorer

import org.bitcoinj.core.Base58
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.params.MainNetParams
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.math.RoundingMode
import java.security.MessageDigest

object BitcoinUtils {

    private val SECP256K1_PARAMS = CustomNamedCurves.getByName("secp256k1")
    private val G = SECP256K1_PARAMS.g
    private val n = SECP256K1_PARAMS.n

    fun deriveAddresses(hex: String): List<String> {
        val pkBytes = BigInteger(hex, 16).toByteArray().takeLast(32).toByteArray()
        val addresses = mutableListOf<String>()

        try {
            val compressedKey = ECKey.fromPrivate(pkBytes, true)
            val uncompressedKey = ECKey.fromPrivate(pkBytes, false)

            addresses.add(LegacyAddress.fromKey(MainNetParams.get(), compressedKey).toString())
            addresses.add(LegacyAddress.fromKey(MainNetParams.get(), uncompressedKey).toString())
        } catch (e: IllegalArgumentException) {
            // Derivação manual com BouncyCastle
            val manualAddresses = deriveManually(pkBytes)
            addresses.addAll(manualAddresses)
        }

        return addresses
    }

    private fun isValidPrivateKey(privKey: BigInteger): Boolean {
        return privKey > BigInteger.ZERO && privKey < n
    }

    private fun getCompressedPublicKey(privKey: BigInteger): ByteArray? {
        return try {
            val point: ECPoint = G.multiply(privKey).normalize()
            point.getEncoded(true) // compressed by default
        } catch (e: Exception) {
            null
        }
    }

    private fun deriveManually(bytes: ByteArray): List<String> {
        val privKey = BigInteger(1, bytes) // 1 = sinal positivo
        if (!isValidPrivateKey(privKey)) return emptyList()

        val pubKeyBytes = getCompressedPublicKey(privKey) ?: return emptyList()
        val address = pubKeyToP2PKHAddress(pubKeyBytes)
        return listOf(address)
    }

    private fun pubKeyToP2PKHAddress(pubKey: ByteArray): String {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(pubKey)
        val ripemd160 = RIPEMD160Digest().apply { update(sha256, 0, sha256.size) }
        val out = ByteArray(20)
        ripemd160.doFinal(out, 0)

        val networkPrefix = byteArrayOf(0x00) // MainNet prefix
        val payload = networkPrefix + out
        val checksum = MessageDigest.getInstance("SHA-256").digest(
            MessageDigest.getInstance("SHA-256").digest(payload)
        ).take(4).toByteArray()

        val full = payload + checksum
        return Base58.encode(full)
    }

    fun calculateBitLength(index: BigInteger): Int = index.bitLength()

    fun calculateProgress(index: BigInteger): Double {
        val max = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFE", 16)
        return index.toBigDecimal().divide(max.toBigDecimal(), 10, RoundingMode.HALF_UP).toDouble()
    }
}