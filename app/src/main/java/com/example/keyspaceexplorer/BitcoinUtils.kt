package com.example.keyspaceexplorer

import org.bouncycastle.crypto.ec.CustomNamedCurves
import java.math.BigInteger
import java.math.RoundingMode

object BitcoinUtils {

    val MIN_KEYSPACE: BigInteger = BigInteger.ONE
    val MAX_KEYSPACE: BigInteger = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364140", 16)
    private val SECP256K1_PARAMS = CustomNamedCurves.getByName("secp256k1")
    private val G = SECP256K1_PARAMS.g
    private val n = SECP256K1_PARAMS.n

    fun calculateBitLength(index: BigInteger): Int = index.bitLength()

    fun calculateProgress(index: BigInteger): Double {
        val max = MAX_KEYSPACE
        return index.toBigDecimal().divide(max.toBigDecimal(), 10, RoundingMode.HALF_UP).toDouble()
    }
}

// Extension function to format Float with given decimals
fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)