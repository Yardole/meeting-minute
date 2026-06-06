package com.meetingminute.app.ui.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetingminute.app.data.audio.AudioRecorder
import com.meetingminute.app.data.local.entity.MeetingStatus
import com.meetingminute.app.domain.repository.MeetingRepository
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
    private val audioRecorder: AudioRecorder
) : ViewModel() {

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _processingStatus = MutableStateFlow("")
    val processingStatus: StateFlow<String> = _processingStatus

    private val _processingProgress = MutableStateFlow(0f) // 0..1 for a subtle progress bar
    val processingProgress: StateFlow<Float> = _processingProgress

    private val _navigateToMeetingId = MutableStateFlow<String?>(null)
    val navigateToMeetingId: StateFlow<String?> = _navigateToMeetingId

    private var timerJob: Job? = null
    private var recordingJob: Job? = null

    fun onNavigated() {
        _navigateToMeetingId.value = null
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

        if (file != null) {
            val meeting = meetingRepository.processRecording(file.absolutePath)

            // Switch to processing mode — observe the meeting status changes
            _processingStatus.value = "Uploading audio..."
            _processingProgress.value = 0.1f

            recordingJob = viewModelScope.launch {
                meetingRepository.observeMeeting(meeting.id).collect { m ->
                    m?.let { updateProcessingState(it.status, it.id) }
                }
            }
        }
    }

    private fun updateProcessingState(status: MeetingStatus, meetingId: UUID) {
        when (status) {
            MeetingStatus.RECORDED, MeetingStatus.RECORDING -> {
                _processingStatus.value = "Uploading audio..."
                _processingProgress.value = 0.15f
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
                _processingStatus.value = "Something went wrong. You can still view the meeting."
                _processingProgress.value = 1f
                viewModelScope.launch {
                    delay(1500)
                    _navigateToMeetingId.value = meetingId.toString()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        recordingJob?.cancel()
    }
}
