package com.oliva.notes.app.data.processing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.oliva.notes.app.data.local.MeetingMinuteDatabase
import com.oliva.notes.app.data.local.entity.MeetingStatus
import com.oliva.notes.app.data.sync.SyncManager
import com.oliva.notes.app.domain.repository.MeetingRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MeetingProcessingService : Service() {

    @Inject lateinit var meetingRepository: MeetingRepository
    @Inject lateinit var database: MeetingMinuteDatabase
    @Inject lateinit var syncManager: SyncManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    private var lastStatus: String = "Preparing…"
    private var lastProgress: Float = 0f

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            notificationManager.cancel(NOTIFICATION_ID)
            notificationManager.notify(NOTIFICATION_ID, buildNotification(lastStatus, lastProgress))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val meetingIdStr = intent?.getStringExtra(EXTRA_MEETING_ID) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val meetingId = UUID.fromString(meetingIdStr)

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Preparing…", 0f),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        serviceScope.launch {
            runPipeline(meetingId)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private suspend fun runPipeline(meetingId: UUID) {
        val meeting = database.meetingDao().getById(meetingId) ?: run {
            Log.e(TAG, "Meeting not found: $meetingId")
            return
        }

        try {
            updateNotification("Syncing…", 0.05f)
            syncManager.ensureMeetingExistsRemotely(meeting).getOrThrow()
            Log.d(TAG, "Pre-pipeline sync completed for ${meeting.id}")

            when (meeting.status) {
                MeetingStatus.RECORDED, MeetingStatus.RECORDING -> {
                    fullPipeline(meetingId)
                }
                MeetingStatus.UPLOADED, MeetingStatus.TRANSCRIBING -> {
                    val audioUrl = meeting.audioUrl ?: run {
                        updateNotification("Uploading audio…", 0.1f)
                        meetingRepository.uploadAudio(meetingId).getOrThrow()
                    }
                    transcribeAndSummarize(meetingId, audioUrl)
                }
                MeetingStatus.TRANSCRIBED, MeetingStatus.SUMMARIZING -> {
                    summarize(meetingId)
                }
                MeetingStatus.ERROR -> {
                    if (meeting.audioUrl != null) {
                        transcribeAndSummarize(meetingId, meeting.audioUrl)
                    } else if (meeting.localAudioPath != null) {
                        fullPipeline(meetingId)
                    } else {
                        Log.e(TAG, "Cannot retry: no audio URL or local file for $meetingId")
                        throw IllegalStateException("No audio available to retry processing")
                    }
                }
                else -> Log.d(TAG, "No processing needed for status ${meeting.status}")
            }
            Log.d(TAG, "Pipeline completed for $meetingId")
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline failed for $meetingId", e)
            database.meetingDao().getById(meetingId)?.let { m ->
                database.meetingDao().update(
                    m.copy(status = MeetingStatus.ERROR, updatedAt = Instant.now())
                )
            }
        }
    }

    private suspend fun fullPipeline(meetingId: UUID) {
        updateNotification("Uploading audio…", 0.15f)
        val audioUrl = meetingRepository.uploadAudio(meetingId).getOrThrow()
        transcribeAndSummarize(meetingId, audioUrl)
    }

    private suspend fun transcribeAndSummarize(meetingId: UUID, audioUrl: String) {
        updateNotification("Transcribing…", 0.35f)
        meetingRepository.transcribeMeeting(meetingId, audioUrl).getOrThrow()
        summarize(meetingId)
    }

    private suspend fun summarize(meetingId: UUID) {
        updateNotification("Generating summary…", 0.7f)
        val text = meetingRepository.getTranscriptText(meetingId)
        if (text.isNotBlank()) {
            meetingRepository.summarizeMeeting(meetingId, text).getOrThrow()
        }
    }

    private fun updateNotification(status: String, progress: Float) {
        lastStatus = status
        lastProgress = progress
        notificationManager.notify(NOTIFICATION_ID, buildNotification(status, progress))
    }

    private fun buildNotification(status: String, progress: Float): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Processing meeting")
            .setContentText(status)
            .setOngoing(true)
            .setProgress(100, (progress * 100).toInt(), false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (Build.VERSION.SDK_INT >= 36) {
            builder.extras.putBoolean("android.requestPromotedOngoing", true)
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        serviceScope.cancel()
        notificationManager.cancel(NOTIFICATION_ID)
    }

    companion object {
        private const val TAG = "ProcessingService"
        private const val LEGACY_CHANNEL_ID = "meeting_processing"
        const val CHANNEL_ID = "meeting_processing_v2"
        private const val NOTIFICATION_ID = 2001
        private const val EXTRA_MEETING_ID = "meeting_id"

        fun start(context: Context, meetingId: UUID) {
            val intent = Intent(context, MeetingProcessingService::class.java).apply {
                putExtra(EXTRA_MEETING_ID, meetingId.toString())
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun createChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.deleteNotificationChannel(LEGACY_CHANNEL_ID)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Meeting processing",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows progress while your meeting is being processed"
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
