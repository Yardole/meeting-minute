package com.oliva.notes.app.ui.settings

import androidx.lifecycle.ViewModel
import com.oliva.notes.app.data.preferences.ThemeMode
import com.oliva.notes.app.data.preferences.ThemePreferences
import com.oliva.notes.app.data.remote.SupabaseAuthClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authClient: SupabaseAuthClient,
    private val themePreferences: ThemePreferences,
) : ViewModel() {

    val userEmail: String? get() = authClient.userEmail

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode

    fun setThemeMode(mode: ThemeMode) {
        themePreferences.setThemeMode(mode)
    }
}
