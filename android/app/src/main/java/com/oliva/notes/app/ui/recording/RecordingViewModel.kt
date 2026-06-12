package com.oliva.notes.app.ui.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oliva.notes.app.data.audio.AudioRecorder
import com.oliva.notes.app.data.connectivity.ConnectivityObserver
import com.oliva.notes.app.data.local.entity.MeetingStatus
import com.oliva.notes.app.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository,
    private val audioRecorder: AudioRecorder,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _tooShort = MutableStateFlow(false)
    val tooShort: StateFlow<Boolean> = _tooShort

    private val _processingStatus = MutableStateFlow("")
    val processingStatus: StateFlow<String> = _processingStatus

    private val _processingProgress = MutableStateFlow(0f) // 0..1 for a subtle progress bar
    val processingProgress: StateFlow<Float> = _processingProgress

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError

    private val _isQueued = MutableStateFlow(false)
    val isQueued: StateFlow<Boolean> = _isQueued

    private var currentMeetingId: UUID? = null

    private val _navigateToMeetingId = MutableStateFlow<String?>(null)
    val navigateToMeetingId: StateFlow<String?> = _navigateToMeetingId

    private var timerJob: Job? = null
    private var recordingJob: Job? = null

    fun onNavigated() {
        _navigateToMeetingId.value = null
    }

    /** Stop any in-progress recording without processing — used when the
     *  screen is left before the user explicitly stops. */
    fun cancelRecording() {
        if (!_isRecording.value) return
        _isRecording.value = false
        timerJob?.cancel()
        _elapsedMs.value = 0L
        audioRecorder.stopRecording()
    }

    fun startRecording() {
        if (_isRecording.value) return
        _isRecording.value = true
        _elapsedMs.value = 0L

        audioRecorder.startRecording()

        timerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                _elapsedMs.value = System.currentTimeMillis() - startTime
                delay(50)
            }
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        _isRecording.value = false
        timerJob?.cancel()

        audioRecorder.stopRecording()
        val file = audioRecorder.getOutputFile()

        // Gate: recordings under 10s are too short to process
        val durationMs = _elapsedMs.value
        if (durationMs < 10_000) {
            _tooShort.value = true
            return
        }

        if (file != null) {
            val startedOffline = !connectivityObserver.isOnline.value
            val meeting = meetingRepository.processRecording(file.absolutePath)
            currentMeetingId = meeting.id
            observeProcessingStatus(meeting.id, startedOffline = startedOffline)
        }
    }

    private fun observeProcessingStatus(meetingId: UUID, startedOffline: Boolean = false) {
        _isError.value = false
        _isQueued.value = startedOffline
        _processingStatus.value = if (startedOffline) "Queued — will process when online" else "Uploading audio..."
        _processingProgress.value = if (startedOffline) 0f else 0.1f

        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            // Watch meeting status for pipeline progress
            launch {
                meetingRepository.observeMeeting(meetingId).collect { m ->
                    m?.let { updateProcessingState(it.status, it.id) }
                }
            }
            // If we started offline, also watch connectivity to clear queued state
            if (startedOffline) {
                connectivityObserver.isOnline.collect { online ->
                    if (online) {
                        _isQueued.value = false
                    }
                }
            }
        }
    }

    fun retryProcessing() {
        val meetingId = currentMeetingId ?: return
        viewModelScope.launch {
            meetingRepository.retryProcessing(meetingId)
            observeProcessingStatus(meetingId)
        }
    }

    private fun updateProcessingState(status: MeetingStatus, meetingId: UUID) {
        when (status) {
            MeetingStatus.RECORDED, MeetingStatus.RECORDING -> {
                if (_isQueued.value) {
                    _processingStatus.value = "Queued — waiting for connection"
                    _processingProgress.value = 0f
                } else {
                    _processingStatus.value = "Uploading audio..."
                    _processingProgress.value = 0.15f
                }
            }
            MeetingStatus.UPLOADED -> {
                _processingStatus.value = "Audio uploaded. Starting transcription..."
                _processingProgress.value = 0.25f
            }
            MeetingStatus.TRANSCRIBING -> {
                _processingStatus.value = "Transcribing your meeting..."
                _processingProgress.value = 0.45f
            }
            MeetingStatus.TRANSCRIBED -> {
                _processingStatus.value = "Extracting key points…"
                _processingProgress.value = 0.65f
            }
            MeetingStatus.SUMMARIZING -> {
                _processingStatus.value = "Generating summary…"
                _processingProgress.value = 0.80f
            }
            MeetingStatus.SUMMARIZED -> {
                _processingStatus.value = "Your meeting is ready."
                _processingProgress.value = 1f
                // Brief pause so the user sees "ready" before navigation
                viewModelScope.launch {
                    delay(800)
                    _navigateToMeetingId.value = meetingId.toString()
                }
            }
            MeetingStatus.ERROR -> {
                _isError.value = true
                _processingStatus.value = "Something went wrong"
                _processingProgress.value = 1f
                // Don't auto-navigate — user can retry or view the meeting
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        recordingJob?.cancel()
    }
}
