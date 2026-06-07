package com.meetingminute.app.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meetingminute.app.data.sync.SyncManager
import com.meetingminute.app.domain.model.Meeting
import com.meetingminute.app.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val meetingRepository: MeetingRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    val meetings: StateFlow<List<Meeting>> = meetingRepository.observeMeetings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        triggerSync()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                triggerSync()
                delay(800) // keep spinner visible long enough to feel responsive
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            try {
                // Use a placeholder user ID — in production this comes from Supabase Auth
                val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
                syncManager.syncAll(userId)
                    .onSuccess { Log.d("HomeVM", "Sync completed") }
                    .onFailure { Log.e("HomeVM", "Sync failed", it) }
            } catch (e: Exception) {
                Log.e("HomeVM", "Sync error", e)
            }
        }
    }

    fun deleteMeeting(id: UUID) {
        viewModelScope.launch {
            meetingRepository.deleteMeeting(id)
        }
    }
}
