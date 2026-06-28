package com.oliva.notes.app.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Compresses audio to mono AAC at a low bitrate so uploads fit under Supabase
 * free-tier's hard 50MB global upload cap. A transcription app only needs
 * telephony-grade speech, so 32kbps mono (~14MB/hr) is ideal, not a compromise.
 *
 * The transcode is a fully streaming MediaExtractor -> decode -> downmix -> AAC
 * encode -> MediaMuxer pipeline: PCM is never buffered whole, so memory stays
 * flat regardless of input length (the bug we're fixing was exactly an
 * in-memory-whole-file allocation).
 *
 * Sample rate is preserved from the source to avoid needing a resampler; file
 * size is governed by the encode bitrate, not the sample rate.
 */
@Singleton
class AudioCompressor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioCompressor"
        private const val TARGET_BITRATE = 32_000
        private const val OUTPUT_MIME = MediaFormat.MIMETYPE_AUDIO_AAC // audio/mp4a-latm
        private const val TIMEOUT_US = 10_000L

        /**
         * Compress when a file is within striking distance of the 50MB cap.
         * Files already comfortably under (e.g. in-app recordings, already
         * recorded at 32kbps) are uploaded as-is to avoid pointless re-encoding.
         */
        private const val COMPRESS_THRESHOLD_BYTES = 45L * 1024 * 1024
    }

    /**
     * Returns a file small enough to upload. If [file] is already under the
     * threshold it is returned unchanged; otherwise a freshly transcoded temp
     * file is returned (caller owns it and should delete it after upload).
     */
    suspend fun compressIfNeeded(file: File): File = withContext(Dispatchers.Default) {
        if (file.length() <= COMPRESS_THRESHOLD_BYTES) {
            Log.d(TAG, "File ${file.length()} bytes under threshold, no compression")
            return@withContext file
        }
        val output = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.m4a")
        Log.d(TAG, "Compressing ${file.length()} bytes -> mono ${TARGET_BITRATE}bps AAC")
        try {
            transcode(file, output)
            Log.d(TAG, "Compressed to ${output.length()} bytes")
            output
        } catch (e: Exception) {
            output.delete()
            throw e
        }
    }

    private fun transcode(input: File, output: File) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(input.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("No audio track in ${input.name}")
            extractor.selectTrack(trackIndex)

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var srcChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            decoder = MediaCodec.createDecoderByType(inputMime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            val encoderFormat = MediaFormat.createAudioFormat(OUTPUT_MIME, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, TARGET_BITRATE)
            }
            encoder = MediaCodec.createEncoderByType(OUTPUT_MIME).apply {
                configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrack = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            // Downmixed mono PCM produced by the decoder but not yet consumed by
            // the encoder. Stays tiny because decode/encode run in lock-step.
            val pendingPcm = ArrayDeque<ByteArray>()
            var bytesEncoded = 0L

            var extractorDone = false
            var decoderDone = false
            var encoderEosSent = false
            var encoderDone = false

            while (!encoderDone) {
                // 1. Feed compressed samples into the decoder.
                if (!extractorDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buf = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            extractorDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // 2. Drain decoded PCM, downmixing to mono.
                if (!decoderDone) {
                    val outIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            srcChannels = decoder.outputFormat
                                .getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        outIndex >= 0 -> {
                            if (bufferInfo.size > 0) {
                                val outBuf = decoder.getOutputBuffer(outIndex)!!
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                pendingPcm.addLast(downmixToMono(outBuf, srcChannels))
                            }
                            decoder.releaseOutputBuffer(outIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                decoderDone = true
                            }
                        }
                    }
                }

                // 3. Feed mono PCM into the encoder (re-chunked to fit its buffers).
                while (pendingPcm.isNotEmpty()) {
                    val inIndex = encoder.dequeueInputBuffer(0)
                    if (inIndex < 0) break
                    val encBuf = encoder.getInputBuffer(inIndex)!!
                    val chunk = pendingPcm.first()
                    // Keep to whole 16-bit samples so we never split one across buffers.
                    val n = min(chunk.size, encBuf.capacity()) and 1.inv()
                    encBuf.put(chunk, 0, n)
                    val ptsUs = (bytesEncoded / 2) * 1_000_000L / sampleRate
                    encoder.queueInputBuffer(inIndex, 0, n, ptsUs, 0)
                    bytesEncoded += n
                    if (n == chunk.size) {
                        pendingPcm.removeFirst()
                    } else {
                        pendingPcm[0] = chunk.copyOfRange(n, chunk.size)
                    }
                }
                if (decoderDone && pendingPcm.isEmpty() && !encoderEosSent) {
                    val inIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val ptsUs = (bytesEncoded / 2) * 1_000_000L / sampleRate
                        encoder.queueInputBuffer(
                            inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        encoderEosSent = true
                    }
                }

                // 4. Drain encoded AAC into the muxer.
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxerTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        // Codec config is folded into the muxer track format, skip it.
                        val isConfig =
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && bufferInfo.size > 0 && muxerStarted) {
                            val encOut = encoder.getOutputBuffer(outIndex)!!
                            muxer.writeSampleData(muxerTrack, encOut, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderDone = true
                        }
                    }
                }
            }
        } finally {
            runCatching { decoder?.stop() }; runCatching { decoder?.release() }
            runCatching { encoder?.stop() }; runCatching { encoder?.release() }
            runCatching { muxer?.stop() }; runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * Collapses interleaved 16-bit PCM to mono. For stereo, averages L+R; for
     * mono, copies through. Assumes 16-bit PCM, which is the decoder default.
     */
    private fun downmixToMono(buffer: ByteBuffer, channels: Int): ByteArray {
        val shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        if (channels <= 1) {
            val out = ByteArray(shorts.remaining() * 2)
            ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)
            return out
        }
        val frames = shorts.remaining() / channels
        val out = ByteArray(frames * 2)
        val outShorts = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += shorts.get()
            outShorts.put((sum / channels).toShort())
        }
        return out
    }
}
