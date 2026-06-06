package com.meetingminute.app.data.remote

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseStorageClient @Inject constructor(
    private val edgeFunctionClient: SupabaseEdgeFunctionClient
) {
    companion object {
        private const val TAG = "SupabaseStorage"
    }

    suspend fun uploadAudio(file: File, meetingId: String): Result<String> {
        Log.d(TAG, "Uploading ${file.length()} bytes for meeting $meetingId")

        val audioBase64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)

        val payload = JSONObject().apply {
            put("meetingId", meetingId)
            put("audioBase64", audioBase64)
        }

        return edgeFunctionClient.call("upload-audio", payload)
            .map { json ->
                val audioUrl = json.getString("audioUrl")
                Log.d(TAG, "Upload success: $audioUrl")
                audioUrl
            }.onFailure { e ->
                Log.e(TAG, "Upload error", e)
            }
    }
}
