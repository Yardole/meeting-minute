package com.oliva.notes.app.data.local.entity

enum class MeetingStatus(val progression: Int) {
    RECORDING(1),
    RECORDED(2),
    TRANSCRIBING(4),
    TRANSCRIBED(5),
    SUMMARIZING(6),
    SUMMARIZED(7),
    UPLOADED(3),
    ERROR(0),
}
