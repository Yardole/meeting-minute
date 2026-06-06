package com.meetingminute.app.ui.meetingdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetingminute.app.data.audio.AudioPlayer
import com.meetingminute.app.data.local.entity.ChatRole
import com.meetingminute.app.domain.model.Meeting
import com.meetingminute.app.domain.model.Speaker
import com.meetingminute.app.domain.model.TranscriptSegment
import com.meetingminute.app.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MeetingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val meetingRepository: MeetingRepository,
    private val audioPlayer: AudioPlayer
) : ViewModel() {

    val meetingId: String = savedStateHandle.get<String>("meetingId") ?: ""

    private val mid = UUID.fromString(meetingId)

    val meeting: StateFlow<Meeting?> = meetingRepository.observeMeeting(mid)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val transcriptSegments: StateFlow<List<TranscriptSegment>> =
        meetingRepository.observeTranscriptSegments(mid)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val summary: StateFlow<String?> = meetingRepository.observeSummary(mid)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val chatMessages: StateFlow<List<Pair<ChatRole, String>>> =
        meetingRepository.observeChatMessages(mid)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val speakers: StateFlow<List<Speaker>> = meetingRepository.observeSpeakers(mid)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isPlaying: StateFlow<Boolean> = audioPlayer.isPlaying
    val currentPosition: StateFlow<Int> = audioPlayer.currentPosition
    val duration: StateFlow<Int> = audioPlayer.duration
    val speed: StateFlow<Float> = audioPlayer.speed

    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    fun onChatInputChanged(value: String) {
        _chatInput.value = value
    }

    fun sendMessage() {
        val message = _chatInput.value.trim()
        if (message.isEmpty() || _isSending.value) return

        _isSending.value = true
        _chatInput.value = ""

        viewModelScope.launch {
            val transcriptText = meetingRepository.getTranscriptText(mid)
            meetingRepository.sendChatMessage(mid, transcriptText, message)
                .onSuccess { _isSending.value = false }
                .onFailure { _isSending.value = false }
        }
    }

    fun renameSpeaker(speakerId: UUID, newName: String) {
        viewModelScope.launch {
            val speaker = speakers.value.find { it.id == speakerId } ?: return@launch
            meetingRepository.updateSpeaker(
                speaker.copy(name = newName)
            )
        }
    }

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
