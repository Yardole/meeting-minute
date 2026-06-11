package com.oliva.notes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.oliva.notes.app.ui.auth.AuthViewModel
import com.oliva.notes.app.ui.navigation.AppNavigation
import com.oliva.notes.app.ui.theme.MeetingMinuteTheme
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MeetingMinuteTheme {
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
