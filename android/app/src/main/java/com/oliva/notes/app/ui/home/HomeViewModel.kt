package com.oliva.notes.app.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oliva.notes.app.data.sync.SyncManager
import com.oliva.notes.app.domain.model.Meeting
import com.oliva.notes.app.domain.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredMeetings: StateFlow<List<Meeting>> = combine(
        meetings,
        _searchQuery,
        meetingRepository.observeAllTranscriptTexts(),
        meetingRepository.observeAllSummaryTexts()
    ) { allMeetings, query, transcripts, summaries ->
        if (query.isBlank()) allMeetings
        else allMeetings.filter { m ->
            m.title.contains(query, ignoreCase = true) ||
            transcripts[m.id]?.contains(query, ignoreCase = true) == true ||
            summaries[m.id]?.contains(query, ignoreCase = true) == true
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isFabExpanded = MutableStateFlow(false)
    val isFabExpanded: StateFlow<Boolean> = _isFabExpanded

    init {
        triggerSync()
        meetingRepository.processPendingMeetings()
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

    fun importAudio(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@launch
                val fileName = "imported_${System.currentTimeMillis()}.m4a"
                val destFile = java.io.File(context.filesDir, "recordings/$fileName").also {
                    it.parentFile?.mkdirs()
                }
                destFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
                meetingRepository.processRecording(destFile.absolutePath)
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "Import failed", e)
            }
        }
    }

    fun toggleFab() {
        _isFabExpanded.value = !_isFabExpanded.value
    }

    fun collapseFab() {
        _isFabExpanded.value = false
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun deleteMeeting(id: UUID) {
        viewModelScope.launch {
            meetingRepository.deleteMeeting(id)
        }
    }
}
