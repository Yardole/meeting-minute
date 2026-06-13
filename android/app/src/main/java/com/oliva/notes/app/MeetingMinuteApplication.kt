package com.oliva.notes.app

import android.app.Application
import com.oliva.notes.app.data.processing.MeetingProcessingService
import com.oliva.notes.app.data.push.OlivaFirebaseMessagingService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MeetingMinuteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OlivaFirebaseMessagingService.createNotificationChannel(this)
        MeetingProcessingService.createChannel(this)
    }
}
