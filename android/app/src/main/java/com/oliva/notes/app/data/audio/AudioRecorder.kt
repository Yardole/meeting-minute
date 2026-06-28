package com.oliva.notes.app.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var _session = 0L
    val currentSession: Long get() = _session

    fun startRecording(): File {
        release()
        _session++

        val timestamp = System.currentTimeMillis()
        outputFile = File(context.filesDir, "recordings/meeting_$timestamp.m4a").also {
            it.parentFile?.mkdirs()
        }

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            // Speech-optimized, low-footprint settings so recordings stay well under
            // Supabase free-tier's 50MB upload cap (~14MB/hr). Mono 16kHz @ 32kbps is
            // telephony-grade — ideal for AssemblyAI transcription, no perceptible loss
            // for speech. See AudioTranscoder for the matching import path.
            setAudioChannels(1)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(32_000)
            setOutputFile(outputFile!!.absolutePath)
            prepare()
            start()
        }

        return outputFile!!
    }

    fun stopRecording() {
        release()
    }

    fun stopIfSession(session: Long) {
        if (session != _session) return
        release()
    }

    private fun release() {
        try { recorder?.stop() } catch (_: Exception) { }
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null
    }

    fun getOutputFile(): File? = outputFile
}
