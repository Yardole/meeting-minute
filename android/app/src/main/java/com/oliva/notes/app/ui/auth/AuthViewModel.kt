package com.oliva.notes.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oliva.notes.app.data.push.FcmTokenManager
import com.oliva.notes.app.data.remote.SupabaseAuthClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val userId: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authClient: SupabaseAuthClient,
    private val fcmTokenManager: FcmTokenManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state

    init {
        if (authClient.isLoggedIn) {
            _state.value = AuthState(isLoggedIn = true, userId = authClient.userId)
            viewModelScope.launch { fcmTokenManager.requestAndStoreToken() }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            authClient.login(email, password)
                .onSuccess { session ->
                    _state.value = AuthState(isLoggedIn = true, userId = session.userId)
                    fcmTokenManager.requestAndStoreToken()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Login failed")
                }
        }
    }

    fun signup(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            authClient.signup(email, password)
                .onSuccess { session ->
                    _state.value = AuthState(isLoggedIn = true, userId = session.userId)
                    fcmTokenManager.requestAndStoreToken()
                }
                .onFailure { e ->
                    val msg = e.message ?: "Signup failed"
                    if (msg.contains("confirmation", ignoreCase = true)) {
                        _state.value = _state.value.copy(isLoading = false, message = "Check your email to confirm your account.")
                    } else {
                        _state.value = _state.value.copy(isLoading = false, error = msg)
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authClient.logout()
            _state.value = AuthState()
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null, message = null)
    }
}
