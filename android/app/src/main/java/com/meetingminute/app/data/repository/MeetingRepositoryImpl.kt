package com.meetingminute.app.data.repository

import android.media.MediaMetadataRetriever
import android.util.Log
import com.meetingminute.app.data.local.MeetingMinuteDatabase
import com.meetingminute.app.data.local.entity.MeetingEntity
import com.meetingminute.app.data.local.entity.MeetingStatus
import com.meetingminute.app.data.local.entity.SpeakerEntity
import com.meetingminute.app.data.local.entity.TranscriptSegmentEntity
import org.json.JSONArray
import com.meetingminute.app.data.remote.SupabaseStorageClient
import com.meetingminute.app.domain.model.Meeting
import com.meetingminute.app.domain.model.Speaker
import com.meetingminute.app.domain.model.TranscriptSegment
import com.meetingminute.app.domain.repository.MeetingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingRepositoryImpl @Inject constructor(
    private val database: MeetingMinuteDatabase,
    private val storageClient: SupabaseStorageClient
) : MeetingRepository {

    private val supabaseUrl = "https://fxzddkwvhavwvzttdlnj.supabase.co"
    private val anonKey = "sb_publishable_wUgKgyaWW8uQvHwWSylSAA_HujDKOra"

    override fun observeMeetings(): Flow<List<Meeting>> {
        return database.meetingDao().observeAll()
            .map { entities ->
                entities.map { it.toDomain() }
            }
    }

    override fun observeMeeting(id: UUID): Flow<Meeting?> {
        return database.meetingDao().observeById(id)
            .map { it?.toDomain() }
    }

    override fun observeTranscriptSegments(meetingId: UUID): Flow<List<TranscriptSegment>> {
        val segments = database.transcriptSegmentDao().observeByMeetingId(meetingId)
        val speakers = database.speakerDao().observeByMeetingId(meetingId)
        return segments.combine(speakers) { segs, spks ->
            val speakerMap = spks.associate { it.id to it }
            segs.map { seg ->
                val speaker = seg.speakerId?.let { speakerMap[it] }
                TranscriptSegment(
                    id = seg.id,
                    meetingId = seg.meetingId,
                    speakerId = seg.speakerId,
                    speakerName = speaker?.name ?: speaker?.label ?: "Unknown",
                    text = seg.text,
                    startTimeMs = seg.startTimeMs,
                    endTimeMs = seg.endTimeMs,
                    confidence = seg.confidence
                )
            }
        }
    }

    override fun observeSpeakers(meetingId: UUID): Flow<List<Speaker>> {
        return database.speakerDao().observeByMeetingId(meetingId)
            .map { entities ->
                entities.map { it.toDomain() }
            }
    }

    override suspend fun createMeeting(title: String, localAudioPath: String): Meeting {
        val durationMs = getAudioDuration(localAudioPath)
        val entity = MeetingEntity(
            userId = UUID.randomUUID(),
            title = title,
            localAudioPath = localAudioPath,
            durationMs = durationMs,
            status = MeetingStatus.RECORDED
        )
        database.meetingDao().insert(entity)
        return entity.toDomain()
    }

    override suspend fun updateMeeting(meeting: Meeting) {
        val entity = database.meetingDao().getById(meeting.id) ?: return
        database.meetingDao().update(
            entity.copy(
                title = meeting.title,
                durationMs = meeting.durationMs,
                status = meeting.status,
                audioUrl = meeting.audioUrl
            )
        )
    }

    override suspend fun deleteMeeting(id: UUID) {
        val entity = database.meetingDao().getById(id) ?: return
        database.meetingDao().update(entity.copy(deletedAt = java.time.Instant.now()))
    }

    override suspend fun uploadAudio(meetingId: UUID): Result<String> {
        val entity = database.meetingDao().getById(meetingId)
            ?: return Result.failure(IllegalStateException("Meeting not found"))

        val audioPath = entity.localAudioPath
            ?: return Result.failure(IllegalStateException("Meeting has no local audio path"))

        val localFile = File(audioPath)
        if (!localFile.exists()) {
            return Result.failure(IllegalStateException("Audio file not found at $audioPath"))
        }

        val bucketPath = "recordings/$meetingId.m4a"

        return storageClient.uploadAudio(localFile, meetingId.toString()).map { audioUrl ->
            database.meetingDao().update(
                entity.copy(
                    audioUrl = audioUrl,
                    audioBucketPath = bucketPath,
                    status = MeetingStatus.UPLOADED
                )
            )
            audioUrl
        }
    }

    override suspend fun transcribeMeeting(meetingId: UUID, audioUrl: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                database.meetingDao().getById(meetingId)?.let { entity ->
                    database.meetingDao().update(entity.copy(status = MeetingStatus.TRANSCRIBING))
                }

                val url = URL("$supabaseUrl/functions/v1/transcribe")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("apikey", anonKey)
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 30_000
                    readTimeout = 180_000
                }

                val payload = JSONObject().apply {
                    put("audioUrl", audioUrl)
                    put("meetingId", meetingId.toString())
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
                    val json = JSONObject(responseBody)
                    val segmentsArray = json.optJSONArray("segments")
                    val speakersArray = json.optJSONArray("speakers")

                    if (speakersArray != null) {
                        val speakerEntities = mutableListOf<SpeakerEntity>()
                        for (i in 0 until speakersArray.length()) {
                            val s = speakersArray.getJSONObject(i)
                            speakerEntities.add(
                                SpeakerEntity(
                                    id = UUID.fromString(s.getString("id")),
                                    meetingId = meetingId,
                                    label = s.getString("label"),
                                    name = s.optString("name", null),
                                    displayOrder = i
                                )
                            )
                        }
                        if (speakerEntities.isNotEmpty()) {
                            database.speakerDao().insertAll(speakerEntities)
                        }
                    }

                    if (segmentsArray != null) {
                        val segmentEntities = mutableListOf<TranscriptSegmentEntity>()
                        for (i in 0 until segmentsArray.length()) {
                            val seg = segmentsArray.getJSONObject(i)
                            val speakerIdStr = seg.optString("speakerId", null)
                            segmentEntities.add(
                                TranscriptSegmentEntity(
                                    id = UUID.fromString(seg.getString("id")),
                                    meetingId = meetingId,
                                    speakerId = if (speakerIdStr != null && speakerIdStr != "null") UUID.fromString(speakerIdStr) else null,
                                    text = seg.getString("text"),
                                    startTimeMs = seg.getInt("startTimeMs"),
                                    endTimeMs = seg.getInt("endTimeMs"),
                                    confidence = seg.optDouble("confidence").toFloat().takeIf { !seg.isNull("confidence") }
                                )
                            )
                        }
                        if (segmentEntities.isNotEmpty()) {
                            database.transcriptSegmentDao().insertAll(segmentEntities)
                        }
                    }

                    val count = segmentsArray?.length() ?: 0
                    Log.d("MeetingRepo", "Transcription saved: $count segments locally")

                    database.meetingDao().getById(meetingId)?.let { entity ->
                        database.meetingDao().update(entity.copy(status = MeetingStatus.TRANSCRIBED))
                    }
                    Unit
                } else {
                    database.meetingDao().getById(meetingId)?.let { entity ->
                        database.meetingDao().update(entity.copy(status = MeetingStatus.ERROR))
                    }
                    throw Exception("Transcription failed: HTTP $responseCode - $responseBody")
                }
            }.onFailure { e ->
                Log.e("MeetingRepo", "Transcription error", e)
            }
        }

    private fun getAudioDuration(filePath: String): Int {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toInt() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun MeetingEntity.toDomain(): Meeting {
        return Meeting(
            id = id,
            title = title,
            durationMs = durationMs,
            recordedAt = recordedAt,
            status = status,
            localAudioPath = localAudioPath,
            audioUrl = audioUrl
        )
    }

    private fun SpeakerEntity.toDomain(): Speaker {
        return Speaker(
            id = id,
            meetingId = meetingId,
            label = label,
            name = name,
            displayOrder = displayOrder
        )
    }
}
