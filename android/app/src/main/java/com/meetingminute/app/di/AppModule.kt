package com.meetingminute.app.di

import android.content.Context
import androidx.room.Room
import com.meetingminute.app.data.audio.AudioPlayer
import com.meetingminute.app.data.local.MeetingMinuteDatabase
import com.meetingminute.app.data.remote.SupabaseClientProvider
import com.meetingminute.app.data.remote.SupabaseConfig
import com.meetingminute.app.data.remote.SupabaseStorageClient
import com.meetingminute.app.data.repository.MeetingRepositoryImpl
import com.meetingminute.app.domain.repository.MeetingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): MeetingMinuteDatabase {
        return Room.databaseBuilder(
            context,
            MeetingMinuteDatabase::class.java,
            "meeting_minute.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideSupabaseConfig(
        provider: SupabaseClientProvider
    ): SupabaseConfig = provider.config

    @Provides
    @Singleton
    fun provideSupabaseStorageClient(
        config: SupabaseConfig
    ): SupabaseStorageClient = SupabaseStorageClient(config)

    @Provides
    @Singleton
    fun provideMeetingRepository(
        impl: MeetingRepositoryImpl
    ): MeetingRepository = impl

    @Provides
    @Singleton
    fun provideAudioPlayer(): AudioPlayer {
        return AudioPlayer()
    }
}
