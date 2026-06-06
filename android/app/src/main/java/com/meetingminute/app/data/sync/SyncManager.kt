package com.meetingminute.app.data.sync

import android.util.Log
import com.meetingminute.app.data.local.MeetingMinuteDatabase
import com.meetingminute.app.data.local.entity.ChatMessageEntity
import com.meetingminute.app.data.local.entity.ChatRole
import com.meetingminute.app.data.local.entity.MeetingEntity
import com.meetingminute.app.data.local.entity.MeetingStatus
import com.meetingminute.app.data.local.entity.ProfileEntity
import com.meetingminute.app.data.local.entity.SpeakerEntity
import com.meetingminute.app.data.local.entity.SummaryEntity
import com.meetingminute.app.data.local.entity.TranscriptSegmentEntity
import com.meetingminute.app.data.remote.SupabaseEdgeFunctionClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val database: MeetingMinuteDatabase,
    private val edgeFunctionClient: SupabaseEdgeFunctionClient
) {
    companion object {
        private const val TAG = "SyncManager"
        private const val MAX_RETRIES = 3
        private const val BASE_DELAY_MS = 1000L
    }

    suspend fun syncAll(userId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "Starting full sync for user $userId")

            // 1. Collect all local data
            val localData = collectLocalData()

            // 2. Build sync payload
            val payload = JSONObject().apply {
                put("userId", userId.toString())
                put("tables", localData)
            }

            // 3. Call sync Edge Function with retry
            val result = callSyncWithRetry(payload)

            // 4. Apply merged data to local database
            val tables = result.getJSONObject("tables")
            applyMergedData(tables)

            Log.d(TAG, "Sync completed successfully")
            Unit
        }.onFailure { e ->
            Log.e(TAG, "Sync failed", e)
        }
    }

    private suspend fun callSyncWithRetry(payload: JSONObject): JSONObject {
        var lastError: Throwable? = null
        for (attempt in 1..MAX_RETRIES) {
            val result = edgeFunctionClient.call("sync", payload, readTimeout = 60_000)
            if (result.isSuccess) return result.getOrThrow()
            lastError = result.exceptionOrNull()
            Log.w(TAG, "Sync attempt $attempt failed, retrying...", lastError)
            if (attempt < MAX_RETRIES) {
                kotlinx.coroutines.delay(BASE_DELAY_MS * attempt)
            }
        }
        throw lastError ?: Exception("Sync failed after $MAX_RETRIES attempts")
    }

    private suspend fun collectLocalData(): JSONObject {
        val tables = JSONObject()

        // Meetings
        val meetings = database.meetingDao().getAllForSync()
        tables.put("meetings", meetingsToJson(meetings))

        // Speakers
        val speakers = database.speakerDao().getAllForSync()
        tables.put("speakers", speakersToJson(speakers))

        // Transcript segments
        val segments = database.transcriptSegmentDao().getAllForSync()
        tables.put("transcript_segments", segmentsToJson(segments))

        // Summaries
        val summaries = database.summaryDao().getAllForSync()
        tables.put("summaries", summariesToJson(summaries))

        // Chat messages
        val messages = database.chatMessageDao().getAllForSync()
        tables.put("chat_messages", messagesToJson(messages))

        // Profiles
        val profiles = database.profileDao().getAllForSync()
        tables.put("profiles", profilesToJson(profiles))

        return tables
    }

    private suspend fun applyMergedData(tables: JSONObject) {
        // Apply in dependency order: meetings → speakers → segments → summaries → chat_messages → profiles

        // Meetings
        val meetingsJson = tables.optJSONArray("meetings")
        if (meetingsJson != null) {
            database.meetingDao().deleteAll()
            database.meetingDao().insertAll(jsonToMeetings(meetingsJson))
            Log.d(TAG, "Synced ${meetingsJson.length()} meetings")
        }

        // Speakers
        val speakersJson = tables.optJSONArray("speakers")
        if (speakersJson != null) {
            database.speakerDao().deleteAll()
            database.speakerDao().insertAll(jsonToSpeakers(speakersJson))
            Log.d(TAG, "Synced ${speakersJson.length()} speakers")
        }

        // Transcript segments
        val segmentsJson = tables.optJSONArray("transcript_segments")
        if (segmentsJson != null) {
            database.transcriptSegmentDao().deleteAll()
            database.transcriptSegmentDao().insertAll(jsonToSegments(segmentsJson))
            Log.d(TAG, "Synced ${segmentsJson.length()} transcript segments")
        }

        // Summaries
        val summariesJson = tables.optJSONArray("summaries")
        if (summariesJson != null) {
            database.summaryDao().deleteAll()
            val summaryEntities = jsonToSummaries(summariesJson)
            for (entity in summaryEntities) {
                database.summaryDao().insert(entity)
            }
            Log.d(TAG, "Synced ${summariesJson.length()} summaries")
        }

        // Chat messages
        val messagesJson = tables.optJSONArray("chat_messages")
        if (messagesJson != null) {
            database.chatMessageDao().deleteAll()
            database.chatMessageDao().insertAll(jsonToMessages(messagesJson))
            Log.d(TAG, "Synced ${messagesJson.length()} chat messages")
        }

        // Profiles
        val profilesJson = tables.optJSONArray("profiles")
        if (profilesJson != null) {
            database.profileDao().deleteAll()
            val profileEntities = jsonToProfiles(profilesJson)
            for (entity in profileEntities) {
                database.profileDao().insert(entity)
            }
            Log.d(TAG, "Synced ${profilesJson.length()} profiles")
        }
    }

    // --- JSON serialization helpers ---

    private fun meetingsToJson(meetings: List<MeetingEntity>): JSONArray {
        val arr = JSONArray()
        for (m in meetings) {
            arr.put(JSONObject().apply {
                put("id", m.id.toString())
                put("userId", m.userId.toString())
                put("title", m.title)
                put("durationMs", m.durationMs)
                put("recordedAt", m.recordedAt.toString())
                put("localAudioPath", m.localAudioPath ?: JSONObject.NULL)
                put("audioUrl", m.audioUrl ?: JSONObject.NULL)
                put("audioBucketPath", m.audioBucketPath ?: JSONObject.NULL)
                put("status", m.status.name)
                put("language", m.language)
                put("createdAt", m.createdAt.toString())
                put("updatedAt", m.updatedAt.toString())
                put("deletedAt", m.deletedAt?.toString() ?: JSONObject.NULL)
            })
        }
        return arr
    }

    private fun speakersToJson(speakers: List<SpeakerEntity>): JSONArray {
        val arr = JSONArray()
        for (s in speakers) {
            arr.put(JSONObject().apply {
                put("id", s.id.toString())
                put("meetingId", s.meetingId.toString())
                put("label", s.label)
                put("name", s.name ?: JSONObject.NULL)
                put("displayOrder", s.displayOrder)
                put("createdAt", s.createdAt.toString())
                put("updatedAt", s.updatedAt.toString())
                put("deletedAt", s.deletedAt?.toString() ?: JSONObject.NULL)
            })
        }
        return arr
    }

    private fun segmentsToJson(segments: List<TranscriptSegmentEntity>): JSONArray {
        val arr = JSONArray()
        for (s in segments) {
            arr.put(JSONObject().apply {
                put("id", s.id.toString())
                put("meetingId", s.meetingId.toString())
                put("speakerId", s.speakerId?.toString() ?: JSONObject.NULL)
                put("text", s.text)
                put("startTimeMs", s.startTimeMs)
                put("endTimeMs", s.endTimeMs)
                put("confidence", s.confidence ?: JSONObject.NULL)
                put("createdAt", s.createdAt.toString())
                put("updatedAt", s.updatedAt.toString())
                put("deletedAt", s.deletedAt?.toString() ?: JSONObject.NULL)
            })
        }
        return arr
    }

    private fun summariesToJson(summaries: List<SummaryEntity>): JSONArray {
        val arr = JSONArray()
        for (s in summaries) {
            arr.put(JSONObject().apply {
                put("id", s.id.toString())
                put("meetingId", s.meetingId.toString())
                put("content", s.content)
                put("keyPoints", s.keyPoints)
                put("generatedAt", s.generatedAt.toString())
                put("createdAt", s.createdAt.toString())
                put("updatedAt", s.updatedAt.toString())
                put("deletedAt", s.deletedAt?.toString() ?: JSONObject.NULL)
            })
        }
        return arr
    }

    private fun messagesToJson(messages: List<ChatMessageEntity>): JSONArray {
        val arr = JSONArray()
        for (m in messages) {
            arr.put(JSONObject().apply {
                put("id", m.id.toString())
                put("meetingId", m.meetingId.toString())
                put("role", m.role.name.lowercase())
                put("content", m.content)
                put("createdAt", m.createdAt.toString())
                put("updatedAt", m.updatedAt.toString())
                put("deletedAt", m.deletedAt?.toString() ?: JSONObject.NULL)
            })
        }
        return arr
    }

    private fun profilesToJson(profiles: List<ProfileEntity>): JSONArray {
        val arr = JSONArray()
        for (p in profiles) {
            arr.put(JSONObject().apply {
                put("id", p.id.toString())
                put("displayName", p.displayName ?: JSONObject.NULL)
                put("avatarUrl", p.avatarUrl ?: JSONObject.NULL)
                put("createdAt", p.createdAt.toString())
                put("updatedAt", p.updatedAt.toString())
                put("deletedAt", p.deletedAt?.toString() ?: JSONObject.NULL)
            })
        }
        return arr
    }

    // --- JSON deserialization helpers ---

    private fun jsonToMeetings(json: JSONArray): List<MeetingEntity> {
        val list = mutableListOf<MeetingEntity>()
        for (i in 0 until json.length()) {
            val o = json.getJSONObject(i)
            list.add(MeetingEntity(
                id = UUID.fromString(o.getString("id")),
                userId = UUID.fromString(o.getString("userId")),
                title = o.optString("title", ""),
                durationMs = o.optInt("durationMs", 0),
                recordedAt = parseInstant(o.optString("recordedAt", null)),
                localAudioPath = o.optString("localAudioPath", null).takeIf { it != "null" },
                audioUrl = o.optString("audioUrl", null).takeIf { it != "null" },
                audioBucketPath = o.optString("audioBucketPath", null).takeIf { it != "null" },
                status = try { MeetingStatus.valueOf(o.optString("status", "RECORDED")) } catch (_: Exception) { MeetingStatus.RECORDED },
                language = o.optString("language", "en"),
                createdAt = parseInstant(o.optString("createdAt", null)),
                updatedAt = parseInstant(o.optString("updatedAt", null)),
                deletedAt = o.optString("deletedAt", null).takeIf { it != "null" }?.let { parseInstant(it) }
            ))
        }
        return list
    }

    private fun jsonToSpeakers(json: JSONArray): List<SpeakerEntity> {
        val list = mutableListOf<SpeakerEntity>()
        for (i in 0 until json.length()) {
            val o = json.getJSONObject(i)
            list.add(SpeakerEntity(
                id = UUID.fromString(o.getString("id")),
                meetingId = UUID.fromString(o.getString("meetingId")),
                label = o.optString("label", ""),
                name = o.optString("name", null).takeIf { it != "null" },
                displayOrder = o.optInt("displayOrder", 0),
                createdAt = parseInstant(o.optString("createdAt", null)),
                updatedAt = parseInstant(o.optString("updatedAt", null)),
                deletedAt = o.optString("deletedAt", null).takeIf { it != "null" }?.let { parseInstant(it) }
            ))
        }
        return list
    }

    private fun jsonToSegments(json: JSONArray): List<TranscriptSegmentEntity> {
        val list = mutableListOf<TranscriptSegmentEntity>()
        for (i in 0 until json.length()) {
            val o = json.getJSONObject(i)
            list.add(TranscriptSegmentEntity(
                id = UUID.fromString(o.getString("id")),
                meetingId = UUID.fromString(o.getString("meetingId")),
                speakerId = o.optString("speakerId", null).takeIf { it != "null" }?.let { UUID.fromString(it) },
                text = o.optString("text", ""),
                startTimeMs = o.optInt("startTimeMs", 0),
                endTimeMs = o.optInt("endTimeMs", 0),
                confidence = if (o.has("confidence") && !o.isNull("confidence")) o.optDouble("confidence").toFloat() else null,
                createdAt = parseInstant(o.optString("createdAt", null)),
                updatedAt = parseInstant(o.optString("updatedAt", null)),
                deletedAt = o.optString("deletedAt", null).takeIf { it != "null" }?.let { parseInstant(it) }
            ))
        }
        return list
    }

    private fun jsonToSummaries(json: JSONArray): List<SummaryEntity> {
        val list = mutableListOf<SummaryEntity>()
        for (i in 0 until json.length()) {
            val o = json.getJSONObject(i)
            list.add(SummaryEntity(
                id = UUID.fromString(o.getString("id")),
                meetingId = UUID.fromString(o.getString("meetingId")),
                content = o.optString("content", ""),
                keyPoints = o.optString("keyPoints", "[]"),
                generatedAt = parseInstant(o.optString("generatedAt", null)),
                createdAt = parseInstant(o.optString("createdAt", null)),
                updatedAt = parseInstant(o.optString("updatedAt", null)),
                deletedAt = o.optString("deletedAt", null).takeIf { it != "null" }?.let { parseInstant(it) }
            ))
        }
        return list
    }

    private fun jsonToMessages(json: JSONArray): List<ChatMessageEntity> {
        val list = mutableListOf<ChatMessageEntity>()
        for (i in 0 until json.length()) {
            val o = json.getJSONObject(i)
            list.add(ChatMessageEntity(
                id = UUID.fromString(o.getString("id")),
                meetingId = UUID.fromString(o.getString("meetingId")),
                role = try {
                    ChatRole.valueOf(o.optString("role", "USER").uppercase())
                } catch (_: Exception) { ChatRole.USER },
                content = o.optString("content", ""),
                createdAt = parseInstant(o.optString("createdAt", null)),
                updatedAt = parseInstant(o.optString("updatedAt", null)),
                deletedAt = o.optString("deletedAt", null).takeIf { it != "null" }?.let { parseInstant(it) }
            ))
        }
        return list
    }

    private fun jsonToProfiles(json: JSONArray): List<ProfileEntity> {
        val list = mutableListOf<ProfileEntity>()
        for (i in 0 until json.length()) {
            val o = json.getJSONObject(i)
            list.add(ProfileEntity(
                id = UUID.fromString(o.getString("id")),
                displayName = o.optString("displayName", null).takeIf { it != "null" },
                avatarUrl = o.optString("avatarUrl", null).takeIf { it != "null" },
                createdAt = parseInstant(o.optString("createdAt", null)),
                updatedAt = parseInstant(o.optString("updatedAt", null)),
                deletedAt = o.optString("deletedAt", null).takeIf { it != "null" }?.let { parseInstant(it) }
            ))
        }
        return list
    }

    private fun parseInstant(value: String?): Instant {
        if (value == null || value == "null") return Instant.now()
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            Instant.now()
        }
    }
}
