package com.oliva.notes.app.data.remote

import android.util.Log
import com.oliva.notes.app.data.audio.AudioCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseStorageClient @Inject constructor(
    private val config: SupabaseConfig,
    private val audioCompressor: AudioCompressor,
) {
    companion object {
        private const val TAG = "SupabaseStorage"
        private const val BUCKET = "audio"
    }

    /**
     * Uploads audio directly to Supabase Storage, streaming the bytes so memory
     * stays flat regardless of file size (the previous base64-in-memory approach
     * OOM-crashed on large files). Large files are first compressed to mono
     * ~32kbps AAC to fit the free-tier 50MB cap; see [AudioCompressor].
     */
    suspend fun uploadAudio(file: File, meetingId: String): Result<String> =
        withContext(Dispatchers.IO) {
            val objectPath = "recordings/$meetingId.m4a"
            var uploadFile = file
            var compressedTemp: File? = null
            try {
                uploadFile = audioCompressor.compressIfNeeded(file)
                if (uploadFile !== file) compressedTemp = uploadFile

                Log.d(TAG, "Uploading ${uploadFile.length()} bytes for meeting $meetingId")
                runCatching {
                    streamUpload(uploadFile, objectPath)
                    val audioUrl =
                        "${config.url}/storage/v1/object/public/$BUCKET/$objectPath"
                    Log.d(TAG, "Upload success: $audioUrl")
                    audioUrl
                }.onFailure { e -> Log.e(TAG, "Upload error", e) }
            } catch (e: Exception) {
                Log.e(TAG, "Compress/upload error", e)
                Result.failure(e)
            } finally {
                compressedTemp?.delete()
            }
        }

    private fun streamUpload(file: File, objectPath: String) {
        val url = URL("${config.url}/storage/v1/object/$BUCKET/$objectPath")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("apikey", config.anonKey)
            setRequestProperty("Authorization", "Bearer ${config.anonKey}")
            setRequestProperty("Content-Type", "audio/mp4")
            setRequestProperty("x-upsert", "true")
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 120_000
            // Stream in fixed-length mode so the file is never buffered in memory.
            setFixedLengthStreamingMode(file.length())
        }

        try {
            connection.outputStream.use { output ->
                file.inputStream().use { input -> input.copyTo(output, bufferSize = 8 * 1024) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.readText() ?: ""
                throw Exception("Storage upload failed: HTTP $code - $err")
            }
        } finally {
            connection.disconnect()
        }
    }
}
