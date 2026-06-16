package com.oliva.notes.app.data.remote

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class AuthSession(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
)

@Singleton
class SupabaseAuthClient @Inject constructor(
    private val config: SupabaseConfig,
    private val context: Context
) {
    private var currentSession: AuthSession? = null
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    val userId: String?
        get() = currentSession?.userId ?: prefs.getString("user_id", null)

    val userEmail: String?
        get() = prefs.getString("user_email", null)

    val isLoggedIn: Boolean
        get() = currentSession != null || prefs.contains("user_id")

    suspend fun signup(email: String, password: String): Result<AuthSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("${config.url}/auth/v1/signup")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("apikey", config.anonKey)
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = connection.responseCode
                val response = if (code in 200..299)
                    connection.inputStream.bufferedReader().readText()
                else
                    connection.errorStream?.bufferedReader()?.readText() ?: ""
                connection.disconnect()

                Log.d("AuthClient", "Signup HTTP $code: $response")

                if (code in 200..299) {
                    val json = JSONObject(response)
                    val accessToken = json.optString("access_token", null)
                    if (accessToken.isNullOrEmpty()) {
                        throw Exception("Check your email to confirm your account.")
                    }
                    val session = AuthSession(
                        userId = json.getJSONObject("user").getString("id"),
                        accessToken = accessToken,
                        refreshToken = json.getString("refresh_token"),
                    )
                    currentSession = session
                    prefs.edit {
                        putString("user_id", session.userId)
                        putString("user_email", email)
                    }
                    session
                } else {
                    val msg = JSONObject(response).optString("msg", response)
                    throw Exception(msg)
                }
            }
        }

    suspend fun login(email: String, password: String): Result<AuthSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("${config.url}/auth/v1/token?grant_type=password")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("apikey", config.anonKey)
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = connection.responseCode
                val response = if (code in 200..299)
                    connection.inputStream.bufferedReader().readText()
                else
                    connection.errorStream?.bufferedReader()?.readText() ?: ""
                connection.disconnect()

                Log.d("AuthClient", "Login HTTP $code: $response")

                if (code in 200..299) {
                    val json = JSONObject(response)
                    val session = AuthSession(
                        userId = json.getJSONObject("user").getString("id"),
                        accessToken = json.getString("access_token"),
                        refreshToken = json.getString("refresh_token"),
                    )
                    currentSession = session
                    prefs.edit {
                        putString("user_id", session.userId)
                        putString("user_email", email)
                    }
                    session
                } else {
                    val msg = JSONObject(response).optString("error_description", response)
                    throw Exception(msg)
                }
            }
        }

    suspend fun logout() {
        currentSession?.let { session ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val url = URL("${config.url}/auth/v1/logout")
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("apikey", config.anonKey)
                        setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                    }
                    connection.responseCode
                    connection.disconnect()
                }
            }
        }
        currentSession = null
        prefs.edit {
            remove("user_id")
            remove("user_email")
        }
    }
}
