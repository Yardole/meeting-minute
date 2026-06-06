package com.meetingminute.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetingminute.app.domain.model.Meeting
import com.meetingminute.app.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository
) : ViewModel() {

    val meetings: StateFlow<List<Meeting>> = meetingRepository.observeMeetings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteMeeting(id: UUID) {
        viewModelScope.launch {
            meetingRepository.deleteMeeting(id)
        }
    }
}
