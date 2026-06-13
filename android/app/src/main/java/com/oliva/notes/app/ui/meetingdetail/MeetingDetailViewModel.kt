package com.oliva.notes.app.ui.meetingdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oliva.notes.app.data.audio.AudioPlayer
import com.oliva.notes.app.data.local.entity.ChatRole
import com.oliva.notes.app.domain.model.Meeting
import com.oliva.notes.app.domain.model.Speaker
import com.oliva.notes.app.domain.model.TranscriptSegment
import com.oliva.notes.app.domain.repository.MeetingRepository
import com.oliva.notes.app.ui.navigation.MeetingDetailRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import com.oliva.notes.app.data.local.entity.MeetingStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel(assistedFactory = MeetingDetailViewModel.Factory::class)
class MeetingDetailViewModel @AssistedInject constructor(
    @Assisted private val navKey: MeetingDetailRoute,
    private val meetingRepository: MeetingRepository,
    private val audioPlayer: AudioPlayer
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(navKey: MeetingDetailRoute): MeetingDetailViewModel
    }

    private val mid = UUID.fromString(navKey.meetingId)

    companion object {
        val greeting = Pair(
            ChatRole.ASSISTANT,
            "Hey there! What would you like to know about this meeting? I can summarize, pull out key points, or answer any questions you have."
        )
    }

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
            .map { listOf(greeting) + it }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = listOf(greeting)
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

    private val _chatError = MutableStateFlow(false)
    val chatError: StateFlow<Boolean> = _chatError

    private val _isRetrying = MutableStateFlow(false)
    val isRetrying: StateFlow<Boolean> = _isRetrying
    private var retryTimeoutJob: Job? = null

    init {
        viewModelScope.launch {
            meeting.collect { m ->
                if (_isRetrying.value && m != null && m.status != MeetingStatus.ERROR) {
                    _isRetrying.value = false
                    retryTimeoutJob?.cancel()
                }
            }
        }
    }

    fun onChatInputChanged(value: String) {
        _chatInput.value = value
    }

    fun sendMessage() {
        val message = _chatInput.value.trim()
        if (message.isEmpty() || _isSending.value) return

        _isSending.value = true
        _chatError.value = false
        _chatInput.value = ""

        viewModelScope.launch {
            val transcriptText = meetingRepository.getTranscriptText(mid)
            meetingRepository.sendChatMessage(mid, transcriptText, message)
                .onSuccess { _isSending.value = false }
                .onFailure {
                    _isSending.value = false
                    _chatError.value = true
                    // Restore the message so user can retry
                    _chatInput.value = message
                }
        }
    }

    fun dismissChatError() {
        _chatError.value = false
    }

    fun retryProcessing() {
        if (_isRetrying.value) return
        _isRetrying.value = true
        retryTimeoutJob?.cancel()
        retryTimeoutJob = viewModelScope.launch {
            delay(8_000)
            _isRetrying.value = false
        }
        viewModelScope.launch {
            meetingRepository.retryProcessing(mid)
        }
    }

    fun updateTitle(newTitle: String) {
        viewModelScope.launch {
            meeting.value?.let { m ->
                meetingRepository.updateMeeting(m.copy(title = newTitle))
            }
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
