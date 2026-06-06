package com.meetingminute.app.ui.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetingminute.app.data.audio.AudioRecorder
import com.meetingminute.app.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository,
    private val audioRecorder: AudioRecorder
) : ViewModel() {

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs

    private var timerJob: Job? = null
    private var isRecording = false

    fun startRecording() {
        if (isRecording) return
        isRecording = true
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

    fun stopRecording(onNavigateBack: () -> Unit) {
        if (!isRecording) return
        isRecording = false
        timerJob?.cancel()

        audioRecorder.stopRecording()
        val file = audioRecorder.getOutputFile()

        if (file != null) {
            // processRecording handles the full upload→transcribe→summarize pipeline
            // in a repository-scoped coroutine that survives navigation
            meetingRepository.processRecording(file.absolutePath)
        }

        onNavigateBack()
    }
}
