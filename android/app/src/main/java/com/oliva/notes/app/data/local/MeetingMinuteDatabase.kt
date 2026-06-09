package com.oliva.notes.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.oliva.notes.app.data.local.dao.ChatMessageDao
import com.oliva.notes.app.data.local.dao.MeetingDao
import com.oliva.notes.app.data.local.dao.ProfileDao
import com.oliva.notes.app.data.local.dao.SpeakerDao
import com.oliva.notes.app.data.local.dao.SummaryDao
import com.oliva.notes.app.data.local.dao.TranscriptSegmentDao
import com.oliva.notes.app.data.local.entity.ChatMessageEntity
import com.oliva.notes.app.data.local.entity.MeetingEntity
import com.oliva.notes.app.data.local.entity.ProfileEntity
import com.oliva.notes.app.data.local.entity.SpeakerEntity
import com.oliva.notes.app.data.local.entity.SummaryEntity
import com.oliva.notes.app.data.local.entity.TranscriptSegmentEntity

@Database(
    entities = [
        ProfileEntity::class,
        MeetingEntity::class,
        SpeakerEntity::class,
        TranscriptSegmentEntity::class,
        SummaryEntity::class,
        ChatMessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MeetingMinuteDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun meetingDao(): MeetingDao
    abstract fun speakerDao(): SpeakerDao
    abstract fun transcriptSegmentDao(): TranscriptSegmentDao
    abstract fun summaryDao(): SummaryDao
    abstract fun chatMessageDao(): ChatMessageDao
}
