package com.oliva.notes.app.data.remote

import android.util.Log
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
    private val config: SupabaseConfig
) {
    private var currentSession: AuthSession? = null

    val userId: String?
        get() = currentSession?.userId

    val isLoggedIn: Boolean
        get() = currentSession != null

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
                    // Email confirmation might be required — check for session
                    val accessToken = json.optString("access_token", null)
                    if (accessToken.isNullOrEmpty()) {
                        throw Exception("Confirmation email sent — check your inbox.")
                    }
                    val session = AuthSession(
                        userId = json.getJSONObject("user").getString("id"),
                        accessToken = accessToken,
                        refreshToken = json.getString("refresh_token"),
                    )
                    currentSession = session
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
                        doOutput = false
                    }
                    connection.responseCode
                    connection.disconnect()
                }
            }
        }
        currentSession = null
    }
}
