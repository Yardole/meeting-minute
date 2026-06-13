package com.oliva.notes.app.data.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.oliva.notes.app.data.local.MeetingMinuteDatabase
import com.oliva.notes.app.data.local.entity.ProfileEntity
import com.oliva.notes.app.data.remote.SupabaseAuthClient
import com.oliva.notes.app.data.sync.SyncManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class FcmTokenManager @Inject constructor(
    private val database: MeetingMinuteDatabase,
    private val authClient: SupabaseAuthClient,
    private val syncManager: SyncManager,
) {
    suspend fun requestAndStoreToken() {
        try {
            val userId = authClient.userId ?: run {
                Log.w(TAG, "Cannot store FCM token — no logged-in user")
                return
            }
            val token = suspendCancellableCoroutine { cont ->
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            Log.d(TAG, "FCM token obtained")
            val profileId = UUID.fromString(userId)
            val existing = database.profileDao().getById(profileId)
            val profile = (existing ?: ProfileEntity(id = profileId)).copy(
                fcmToken = token,
                updatedAt = Instant.now(),
            )
            database.profileDao().insert(profile)
            Log.d(TAG, "FCM token stored locally, syncing to Supabase")
            syncManager.syncAll(profileId)
            Log.d(TAG, "FCM token synced to Supabase")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request/store FCM token", e)
        }
    }

    companion object {
        private const val TAG = "FcmTokenManager"
    }
}
