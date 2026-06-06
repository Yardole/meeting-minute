package com.meetingminute.app.ui.meetingdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetingminute.app.data.audio.AudioPlayer
import com.meetingminute.app.domain.model.Meeting
import com.meetingminute.app.domain.model.TranscriptSegment
import com.meetingminute.app.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MeetingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val meetingRepository: MeetingRepository,
    private val audioPlayer: AudioPlayer
) : ViewModel() {

    val meetingId: String = savedStateHandle.get<String>("meetingId") ?: ""

    val meeting: StateFlow<Meeting?> = meetingRepository.observeMeeting(UUID.fromString(meetingId))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val transcriptSegments: StateFlow<List<TranscriptSegment>> =
        meetingRepository.observeTranscriptSegments(UUID.fromString(meetingId))
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val isPlaying: StateFlow<Boolean> = audioPlayer.isPlaying
    val currentPosition: StateFlow<Int> = audioPlayer.currentPosition
    val duration: StateFlow<Int> = audioPlayer.duration
    val speed: StateFlow<Float> = audioPlayer.speed

    fun prepareAudio(filePath: String) {
        audioPlayer.prepare(filePath)
    }

    fun togglePlayback() {
        if (isPlaying.value) {
            audioPlayer.pause()
        } else {
            audioPlayer.play()
        }
    }

    fun cycleSpeed() {
        val next = when (speed.value) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            2.0f -> 1.0f
            else -> 1.0f
        }
        audioPlayer.setSpeed(next)
    }

    fun seekTo(positionMs: Int) {
        audioPlayer.seekTo(positionMs)
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
