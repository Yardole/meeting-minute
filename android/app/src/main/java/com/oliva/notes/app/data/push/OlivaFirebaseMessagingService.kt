package com.oliva.notes.app.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.oliva.notes.app.MainActivity
import com.oliva.notes.app.data.local.MeetingMinuteDatabase
import com.oliva.notes.app.data.local.entity.ProfileEntity
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OlivaFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var database: MeetingMinuteDatabase

    @Inject
    lateinit var authClient: com.oliva.notes.app.data.remote.SupabaseAuthClient

    companion object {
        private const val TAG = "OlivaFCM"
        const val CHANNEL_ID = "oliva_meeting_updates"
        const val CHANNEL_NAME = "Meeting updates"
        const val EXTRA_MEETING_ID = "extra_meeting_id"

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when meeting processing completes"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        storeToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received: ${message.data}")

        val title = message.data["title"] ?: message.notification?.title ?: "Oliva"
        val body = message.data["body"] ?: message.notification?.body ?: "Your meeting is ready."
        val meetingId = message.data["meeting_id"]

        showNotification(title, body, meetingId)
    }

    private fun showNotification(title: String, body: String, meetingId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            meetingId?.let { putExtra(EXTRA_MEETING_ID, it) }
        }
        val requestCode = meetingId?.hashCode() ?: System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun storeToken(token: String) {
        try {
            val userIdStr = authClient.userId ?: run {
                Log.w(TAG, "Cannot store FCM token — no logged-in user")
                return
            }
            val profileId = UUID.fromString(userIdStr)
            runBlocking(Dispatchers.IO) {
                val existing = database.profileDao().getById(profileId)
                val profile = (existing ?: ProfileEntity(id = profileId)).copy(
                    fcmToken = token,
                    updatedAt = Instant.now()
                )
                database.profileDao().insert(profile)
            }
            Log.d(TAG, "FCM token stored in local profile")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store FCM token", e)
        }
    }
}
