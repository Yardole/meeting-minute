package com.meetingminute.app.data.repository

import android.media.MediaMetadataRetriever
import android.util.Log
import com.meetingminute.app.data.local.MeetingMinuteDatabase
import com.meetingminute.app.data.local.entity.ChatMessageEntity
import com.meetingminute.app.data.local.entity.ChatRole
import com.meetingminute.app.data.local.entity.MeetingEntity
import com.meetingminute.app.data.local.entity.MeetingStatus
import com.meetingminute.app.data.local.entity.SpeakerEntity
import com.meetingminute.app.data.local.entity.SummaryEntity
import com.meetingminute.app.data.local.entity.TranscriptSegmentEntity
import com.meetingminute.app.data.remote.SupabaseConfig
import com.meetingminute.app.data.remote.SupabaseEdgeFunctionClient
import com.meetingminute.app.data.remote.SupabaseStorageClient
import com.meetingminute.app.domain.model.Meeting
import com.meetingminute.app.domain.model.Speaker
import com.meetingminute.app.domain.model.TranscriptSegment
import com.meetingminute.app.domain.repository.MeetingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingRepositoryImpl @Inject constructor(
    private val database: MeetingMinuteDatabase,
    private val storageClient: SupabaseStorageClient,
    private val config: SupabaseConfig,
    private val edgeFunctionClient: SupabaseEdgeFunctionClient
) : MeetingRepository {

    // Scope that outlives any individual ViewModel for background processing
    private val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun observeMeetings(): Flow<List<Meeting>> {
        return database.meetingDao().observeAll()
            .map { entities -> entities.map { it.toDomain() } }
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
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeSummary(meetingId: UUID): Flow<String?> {
        return database.summaryDao().observeByMeetingId(meetingId)
            .map { it?.content }
    }

    override fun observeChatMessages(meetingId: UUID): Flow<List<Pair<ChatRole, String>>> {
        return database.chatMessageDao().observeByMeetingId(meetingId)
            .map { entities -> entities.map { it.role to it.content } }
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

    override fun processRecording(localAudioPath: String): Meeting {
        // Quick Room insert on IO — returns immediately
        val meeting = runBlocking(Dispatchers.IO) {
            createMeeting(
                title = "Meeting ${System.currentTimeMillis()}",
                localAudioPath = localAudioPath
            )
        }

        // Run the pipeline in a scope that survives navigation
        processingScope.launch {
            Log.d("MeetingRepo", "Starting pipeline for meeting ${meeting.id}")

            uploadAudio(meeting.id)
                .onSuccess { audioUrl ->
                    Log.d("MeetingRepo", "Upload done, starting transcribe")
                    transcribeMeeting(meeting.id, audioUrl)
                        .onSuccess {
                            Log.d("MeetingRepo", "Transcribe done, starting summarize")
                            val transcriptText = getTranscriptText(meeting.id)
                            if (transcriptText.isNotBlank()) {
                                summarizeMeeting(meeting.id, transcriptText)
                                    .onSuccess { Log.d("MeetingRepo", "Summarize done") }
                                    .onFailure { Log.e("MeetingRepo", "Summarize failed", it) }
                            } else {
                                Log.d("MeetingRepo", "No transcript text, skipping summarize")
                            }
                        }
                        .onFailure { Log.e("MeetingRepo", "Transcribe failed", it) }
                }
                .onFailure { Log.e("MeetingRepo", "Upload failed", it) }
        }

        return meeting
    }

    override suspend fun updateMeeting(meeting: Meeting) {
        val entity = database.meetingDao().getById(meeting.id) ?: return
        database.meetingDao().update(
            entity.copy(
                title = meeting.title,
                durationMs = meeting.durationMs,
                status = meeting.status,
                audioUrl = meeting.audioUrl,
                updatedAt = Instant.now()
            )
        )
    }

    override suspend fun deleteMeeting(id: UUID) {
        val entity = database.meetingDao().getById(id) ?: return
        val now = Instant.now()
        val nowEpoch = now.toEpochMilli()

        // Soft-delete the meeting
        database.meetingDao().update(entity.copy(deletedAt = now, updatedAt = now))

        // Cascade soft-delete to child entities so sync propagates correctly
        database.speakerDao().softDeleteByMeetingId(id, nowEpoch, nowEpoch)
        database.transcriptSegmentDao().softDeleteByMeetingId(id, nowEpoch, nowEpoch)
        database.summaryDao().softDeleteByMeetingId(id, nowEpoch, nowEpoch)
        database.chatMessageDao().softDeleteByMeetingId(id, nowEpoch, nowEpoch)
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
                    status = MeetingStatus.UPLOADED,
                    updatedAt = Instant.now()
                )
            )
            audioUrl
        }
    }

    override suspend fun transcribeMeeting(meetingId: UUID, audioUrl: String): Result<Unit> {
        val entity = database.meetingDao().getById(meetingId) ?: return Result.failure(
            IllegalStateException("Meeting not found")
        )

        database.meetingDao().update(entity.copy(status = MeetingStatus.TRANSCRIBING, updatedAt = Instant.now()))

        val payload = JSONObject().apply {
            put("audioUrl", audioUrl)
            put("meetingId", meetingId.toString())
        }

        return edgeFunctionClient.call("transcribe", payload, readTimeout = 180_000)
            .map { json ->
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
                                speakerId = if (speakerIdStr != null && speakerIdStr != "null") UUID.fromString(
                                    speakerIdStr
                                ) else null,
                                text = seg.getString("text"),
                                startTimeMs = seg.getInt("startTimeMs"),
                                endTimeMs = seg.getInt("endTimeMs"),
                                confidence = seg.optDouble("confidence").toFloat()
                                    .takeIf { !seg.isNull("confidence") }
                            )
                        )
                    }
                    if (segmentEntities.isNotEmpty()) {
                        database.transcriptSegmentDao().insertAll(segmentEntities)
                    }
                }

                val count = segmentsArray?.length() ?: 0
                Log.d("MeetingRepo", "Transcription saved: $count segments locally")

                database.meetingDao().getById(meetingId)?.let { e ->
                    database.meetingDao().update(e.copy(status = MeetingStatus.TRANSCRIBED, updatedAt = Instant.now()))
                }
                Unit
            }.onFailure { e ->
                Log.e("MeetingRepo", "Transcription error", e)
                database.meetingDao().getById(meetingId)?.let { e ->
                    database.meetingDao().update(e.copy(status = MeetingStatus.ERROR, updatedAt = Instant.now()))
                }
                Unit
            }
    }

    override suspend fun summarizeMeeting(meetingId: UUID, transcriptText: String): Result<String> {
        val entity = database.meetingDao().getById(meetingId)
            ?: return Result.failure(IllegalStateException("Meeting not found"))

        database.meetingDao().update(entity.copy(status = MeetingStatus.SUMMARIZING, updatedAt = Instant.now()))

        val payload = JSONObject().apply {
            put("transcript", transcriptText)
            put("meetingId", meetingId.toString())
        }

        return edgeFunctionClient.call("summarize", payload, readTimeout = 120_000)
            .map { json ->
                val content = json.getString("content")
                val title = json.optString("title", null)
                val keyPoints = json.optJSONArray("keyPoints")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList()

                val summaryEntity = SummaryEntity(
                    meetingId = meetingId,
                    content = content,
                    keyPoints = JSONArray(keyPoints).toString(),
                    generatedAt = Instant.now()
                )
                database.summaryDao().insert(summaryEntity)

                val updatedEntity = database.meetingDao().getById(meetingId)
                updatedEntity?.let { e ->
                    database.meetingDao().update(
                        e.copy(
                            status = MeetingStatus.SUMMARIZED,
                            title = if (!title.isNullOrBlank()) title else e.title,
                            updatedAt = Instant.now()
                        )
                    )
                }

                Log.d("MeetingRepo", "Summary saved for meeting $meetingId (title: ${title ?: "unchanged"})")
                content
            }.onFailure { e ->
                Log.e("MeetingRepo", "Summarization error", e)
                database.meetingDao().getById(meetingId)?.let { e ->
                    database.meetingDao().update(e.copy(status = MeetingStatus.ERROR, updatedAt = Instant.now()))
                }
            }
    }

    override suspend fun sendChatMessage(meetingId: UUID, transcriptText: String, message: String): Result<String> {
        val messages = database.chatMessageDao().observeByMeetingId(meetingId)
        // Build chat history from existing messages
        val chatHistory = JSONArray()
        // Collect existing messages for history
        val existingMessages = mutableListOf<ChatMessageEntity>()
        // Use a simple approach: get existing messages and build payload
        val payload = JSONObject().apply {
            put("transcript", transcriptText)
            put("newMessage", message)
            put("meetingId", meetingId.toString())
            put("chatHistory", JSONArray()) // Edge function will look up history from DB
        }

        // Insert user message locally
        val userMessage = ChatMessageEntity(
            meetingId = meetingId,
            role = ChatRole.USER,
            content = message
        )
        database.chatMessageDao().insert(userMessage)

        return edgeFunctionClient.call("chat", payload, readTimeout = 120_000)
            .map { json ->
                val reply = json.getString("reply")
                val assistantMessage = ChatMessageEntity(
                    meetingId = meetingId,
                    role = ChatRole.ASSISTANT,
                    content = reply
                )
                database.chatMessageDao().insert(assistantMessage)
                Log.d("MeetingRepo", "Chat reply saved for meeting $meetingId")
                reply
            }.onFailure { e ->
                Log.e("MeetingRepo", "Chat error", e)
            }
    }

    override suspend fun updateSpeaker(speaker: Speaker) {
        // Get the old label before the rename
        val oldSpeaker = database.speakerDao().getByMeetingId(speaker.meetingId)
            .find { it.id == speaker.id }
        val oldLabel = oldSpeaker?.label ?: return

        // Update the speaker entity
        val speakerEntity = SpeakerEntity(
            id = speaker.id,
            meetingId = speaker.meetingId,
            label = speaker.label,
            name = speaker.name,
            displayOrder = speaker.displayOrder,
            updatedAt = Instant.now()
        )
        database.speakerDao().update(speakerEntity)

        // Update summary text — replace old label with new name (no API call)
        val newName = speaker.name ?: speaker.label
        database.summaryDao().getByMeetingId(speaker.meetingId)?.let { summaryEntity ->
            val updatedContent = summaryEntity.content.replace(oldLabel, newName)
            if (updatedContent != summaryEntity.content) {
                database.summaryDao().update(
                    summaryEntity.copy(content = updatedContent, updatedAt = Instant.now())
                )
                Log.d("MeetingRepo", "Summary updated: $oldLabel → $newName")
            }
        }
    }

    override suspend fun getTranscriptText(meetingId: UUID): String {
        val segments = mutableListOf<TranscriptSegmentEntity>()
        database.transcriptSegmentDao().observeByMeetingId(meetingId).map { segs ->
            segments.clear()
            segments.addAll(segs)
        }
        // We need to get the value synchronously - use a different approach
        return buildTranscriptText(meetingId)
    }

    private suspend fun buildTranscriptText(meetingId: UUID): String {
        val segments = mutableListOf<TranscriptSegmentEntity>()
        val speakers = mutableMapOf<UUID, SpeakerEntity>()

        // Collect via a one-shot query
        val allSegments = database.transcriptSegmentDao().getByMeetingId(meetingId)
        val allSpeakers = database.speakerDao().getByMeetingId(meetingId)

        val speakerMap = allSpeakers.associate { it.id to it }

        return allSegments.joinToString("\n") { seg ->
            val spk = seg.speakerId?.let { speakerMap[it] }
            val name = spk?.name ?: spk?.label ?: "Speaker"
            "$name: ${seg.text}"
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

    override fun observeAllTranscriptTexts(): Flow<Map<UUID, String>> {
        return database.transcriptSegmentDao().observeAllForSearch().map { rows ->
            rows.groupBy({ it.meetingId }, { it.text })
                .mapValues { (_, texts) -> texts.joinToString(" ") }
        }
    }

    override fun observeAllSummaryTexts(): Flow<Map<UUID, String>> {
        return database.summaryDao().observeAllForSearch().map { rows ->
            rows.associate { it.meetingId to it.content }
        }
    }
}
