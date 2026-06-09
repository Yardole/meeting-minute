package com.oliva.notes.app.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseEdgeFunctionClient @Inject constructor(
    private val config: SupabaseConfig
) {
    companion object {
        private const val TAG = "EdgeFunction"
    }

    suspend fun call(
        functionName: String,
        payload: JSONObject,
        connectTimeout: Int = 30_000,
        readTimeout: Int = 30_000
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("${config.url}/functions/v1/$functionName")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", config.anonKey)
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                this.connectTimeout = connectTimeout
                this.readTimeout = readTimeout
            }

            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray())
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText() ?: ""
            }
            connection.disconnect()

            if (responseCode in 200..299) {
                Log.d(TAG, "$functionName succeeded")
                JSONObject(responseBody)
            } else {
                throw Exception("$functionName failed: HTTP $responseCode - $responseBody")
            }
        }.onFailure { e ->
            Log.e(TAG, "$functionName error", e)
        }
    }
}
