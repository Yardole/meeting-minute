package com.oliva.notes.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.oliva.notes.app.data.preferences.ThemePreferences
import com.oliva.notes.app.data.push.OlivaFirebaseMessagingService
import com.oliva.notes.app.ui.auth.AuthViewModel
import com.oliva.notes.app.ui.navigation.AppNavigation
import com.oliva.notes.app.ui.theme.MeetingMinuteTheme
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(ExperimentalSharedTransitionApi::class)
class MainActivity : ComponentActivity() {

    @Inject lateinit var themePreferences: ThemePreferences

    private val _pendingMeetingId = MutableStateFlow<String?>(null)
    val pendingMeetingId = _pendingMeetingId.asStateFlow()

    fun consumePendingMeetingId() {
        _pendingMeetingId.value = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleMeetingIntent(intent)
        setContent {
            val themeMode by themePreferences.themeMode.collectAsState()
            MeetingMinuteTheme(themeMode = themeMode) {
                val authViewModel: AuthViewModel = hiltViewModel()
                SharedTransitionLayout(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation(
                            sharedTransitionScope = this@SharedTransitionLayout,
                            authViewModel = authViewModel,
                            pendingMeetingId = pendingMeetingId,
                            onMeetingIdConsumed = ::consumePendingMeetingId,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleMeetingIntent(intent)
    }

    private fun handleMeetingIntent(intent: Intent?) {
        val meetingId = intent?.getStringExtra(OlivaFirebaseMessagingService.EXTRA_MEETING_ID)
            ?: intent?.getStringExtra("meeting_id")
        if (meetingId != null) {
            _pendingMeetingId.value = meetingId
        }
    }
}
