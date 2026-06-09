package com.oliva.notes.app.data.local

import androidx.room.TypeConverter
import com.oliva.notes.app.data.local.entity.ChatRole
import com.oliva.notes.app.data.local.entity.MeetingStatus
import java.time.Instant
import java.util.UUID

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun toTimestamp(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun fromUuid(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun toUuid(value: String?): UUID? = value?.let { UUID.fromString(it) }

    @TypeConverter
    fun fromMeetingStatus(status: MeetingStatus?): String? = status?.name

    @TypeConverter
    fun toMeetingStatus(value: String?): MeetingStatus? = value?.let { MeetingStatus.valueOf(it) }

    @TypeConverter
    fun fromChatRole(role: ChatRole?): String? = role?.name

    @TypeConverter
    fun toChatRole(value: String?): ChatRole? = value?.let { ChatRole.valueOf(it) }
}
