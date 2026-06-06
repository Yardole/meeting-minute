package com.meetingminute.app.data.remote

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class SupabaseStorageClient constructor(
    private val config: SupabaseConfig
) {
    companion object {
        private const val TAG = "SupabaseStorage"
    }

    suspend fun uploadAudio(file: File, meetingId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val audioBase64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                Log.d(TAG, "Uploading ${file.length()} bytes for meeting $meetingId")

                val url = URL("${config.url}/functions/v1/upload-audio")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("apikey", config.anonKey)
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 30_000
                    readTimeout = 30_000
                }

                val payload = JSONObject().apply {
                    put("meetingId", meetingId)
                    put("audioBase64", audioBase64)
                }

                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray())
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "Upload response: HTTP $responseCode")

                val responseBody = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: ""
                }

                connection.disconnect()

                if (responseCode in 200..299) {
                    val json = JSONObject(responseBody)
                    val audioUrl = json.getString("audioUrl")
                    Log.d(TAG, "Upload success: $audioUrl")
                    audioUrl
                } else {
                    throw Exception("Upload failed: HTTP $responseCode - $responseBody")
                }
            }.onFailure { e ->
                Log.e(TAG, "Upload error", e)
            }
        }
}
