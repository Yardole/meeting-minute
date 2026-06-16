package com.oliva.notes.app

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
import com.oliva.notes.app.ui.auth.AuthViewModel
import com.oliva.notes.app.ui.navigation.AppNavigation
import com.oliva.notes.app.ui.theme.MeetingMinuteTheme
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(ExperimentalSharedTransitionApi::class)
class MainActivity : ComponentActivity() {

    @Inject lateinit var themePreferences: ThemePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
                        )
                    }
                }
            }
        }
    }
}
