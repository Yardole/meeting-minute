package com.oliva.notes.app.domain.repository

import com.oliva.notes.app.data.local.entity.ChatRole
import com.oliva.notes.app.domain.model.Meeting
import com.oliva.notes.app.domain.model.Speaker
import com.oliva.notes.app.domain.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface MeetingRepository {
    fun observeMeetings(): Flow<List<Meeting>>
    fun observeMeeting(id: UUID): Flow<Meeting?>
    fun observeTranscriptSegments(meetingId: UUID): Flow<List<TranscriptSegment>>
    fun observeSpeakers(meetingId: UUID): Flow<List<Speaker>>
    fun observeSummary(meetingId: UUID): Flow<String?>
    fun observeChatMessages(meetingId: UUID): Flow<List<Pair<ChatRole, String>>>
    suspend fun createMeeting(title: String, localAudioPath: String): Meeting
    fun processRecording(localAudioPath: String): Meeting
    suspend fun updateMeeting(meeting: Meeting)
    suspend fun deleteMeeting(id: UUID)
    suspend fun uploadAudio(meetingId: UUID): Result<String>
    suspend fun transcribeMeeting(meetingId: UUID, audioUrl: String): Result<Unit>
    suspend fun summarizeMeeting(meetingId: UUID, transcriptText: String): Result<String>
    suspend fun sendChatMessage(meetingId: UUID, transcriptText: String, message: String): Result<String>
    suspend fun updateSpeaker(speaker: Speaker)
    suspend fun getTranscriptText(meetingId: UUID): String
    fun observeAllTranscriptTexts(): Flow<Map<UUID, String>>
    fun observeAllSummaryTexts(): Flow<Map<UUID, String>>
    suspend fun retryProcessing(meetingId: UUID)
    fun processPendingMeetings()
}
