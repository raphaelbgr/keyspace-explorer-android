package com.example.keyspaceexplorer

import android.annotation.SuppressLint
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.bitcoinj.core.Base58
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import java.io.IOException
import java.security.MessageDigest

object AlertHelper {
    fun alertMatch(item: PrivateKeyItem) {
        try {
            MainActivity.Instance.context?.application?.let {
                AlertDialog.Builder(it).apply {
                    setTitle("Match encontrado!")
                    setMessage("PrivKey: ${item.hex}\nAddress: ${item.addresses.joinToString()}")
                    setPositiveButton("OK", null)
                }.show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

object TelegramHelper {
    private const val TELEGRAM_URL = "https://api.telegram.org/bot7688830724:AAHnBdSNgwnjNKyq62f_ZjlhQiNFHzm0SIU/sendMessage"
    private const val TELEGRAM_ID = "27196478"

    fun sendAlert(item: PrivateKeyItem) {
        try {
            val body = "Chave privada encontrada!\nPrivKey: ${item.hex}\nAddress: ${item.addresses.joinToString()}"
            val form = FormBody.Builder()
                .add("chat_id", TELEGRAM_ID)
                .add("text", body)
                .build()
            val request = Request.Builder().url(TELEGRAM_URL).post(form).build()
            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) {}
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

object StorageHelper {
    @SuppressLint("MutatingSharedPrefs", "UseKtx")
    fun saveMatch(item: PrivateKeyItem) {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.Instance.context)
            val existing = prefs.getStringSet("matches", mutableSetOf()) ?: mutableSetOf()
            existing.add("${item.hex}|${item.addresses.joinToString()}")
            prefs.edit().putStringSet("matches", existing).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

object LogHelper {
    fun logMatch(item: PrivateKeyItem) {
        try {
            for (i in 1..100) {
                Log.d("MATCH", "MATCH FOUND -> $item")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

object ToastHelper {
    fun showToast(item: PrivateKeyItem) {
        try {
            Toast.makeText(MainActivity.Instance.context, "Match encontrado! -> $item", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

object AddressUtils {

    fun pubKeyToAddress(pubKey: ByteArray): String {
        // 1. SHA-256
        val sha256 = MessageDigest.getInstance("SHA-256").digest(pubKey)

        // 2. RIPEMD-160
        val ripemd160 = RIPEMD160Digest().apply { update(sha256, 0, sha256.size) }
        val hash160 = ByteArray(20)
        ripemd160.doFinal(hash160, 0)

        // 3. Prefix (0x00 para BTC mainnet P2PKH)
        val prefix = byteArrayOf(0x00)

        // 4. Payload
        val payload = prefix + hash160

        // 5. Checksum = SHA256(SHA256(payload)).take(4)
        val checksum = MessageDigest.getInstance("SHA-256").digest(
            MessageDigest.getInstance("SHA-256").digest(payload)
        ).take(4).toByteArray()

        // 6. Base58(Payload + Checksum)
        return Base58.encode(payload + checksum)
    }

    fun normalize(address: String, coin: String): String {
        return when (coin.uppercase()) {
            "ETH" -> address.lowercase().removePrefix("0x")
            "BCH" -> address.removePrefix("bitcoincash:")
            else -> address
        }
    }
}