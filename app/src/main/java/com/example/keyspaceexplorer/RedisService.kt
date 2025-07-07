package com.example.keyspaceexplorer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import okhttp3.logging.HttpLoggingInterceptor

class RedisService {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val url = "http://192.168.7.101:5000/check"
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    suspend fun checkMatches(addresses: List<String>): List<String> {
        val json = JSONObject().put("addresses", JSONArray(addresses))
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        while (true) {
            try {
                _isConnecting.value = true
                return withContext(Dispatchers.IO) {
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val data = JSONObject(response.body?.string() ?: "{}")
                    val matches = data.optJSONArray("matches") ?: JSONArray()
                    List(matches.length()) { matches.getString(it) }
                }.also {
                    _isConnecting.value = false
                }
            } catch (e: Exception) {
                println("❌ Redis check failed: ${e.message}")
                _isConnecting.value = true
                delay(3000)
            }
        }
    }
}