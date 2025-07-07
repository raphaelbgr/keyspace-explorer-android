package com.example.keyspaceexplorer

import java.math.BigInteger

class PrivateKeyItem(
    val index: BigInteger = BigInteger.ZERO,
    val hex: String = "",
    val addresses: List<String> = emptyList(),
    var dbHit: Boolean? = null,
)