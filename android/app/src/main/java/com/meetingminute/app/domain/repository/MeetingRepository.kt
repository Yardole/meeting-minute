package com.meetingminute.app.domain.repository

import com.meetingminute.app.domain.model.Meeting
import com.meetingminute.app.domain.model.Speaker
import com.meetingminute.app.domain.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface MeetingRepository {
    fun observeMeetings(): Flow<List<Meeting>>
    fun observeMeeting(id: UUID): Flow<Meeting?>
    fun observeTranscriptSegments(meetingId: UUID): Flow<List<TranscriptSegment>>
    fun observeSpeakers(meetingId: UUID): Flow<List<Speaker>>
    suspend fun createMeeting(title: String, localAudioPath: String): Meeting
    suspend fun updateMeeting(meeting: Meeting)
    suspend fun deleteMeeting(id: UUID)
    suspend fun uploadAudio(meetingId: UUID): Result<String>
    suspend fun transcribeMeeting(meetingId: UUID, audioUrl: String): Result<Unit>
}
