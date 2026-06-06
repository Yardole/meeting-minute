package com.meetingminute.app.data.audio

import android.media.MediaPlayer
import android.media.PlaybackParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayer {

    private var player: MediaPlayer? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun prepare(filePath: String) {
        release()
        player = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
            _duration.value = duration
            _currentPosition.value = 0
            _speed.value = 1.0f
            setOnCompletionListener {
                _isPlaying.value = false
                _currentPosition.value = 0
                stopProgressUpdates()
            }
        }
    }

    fun play() {
        player?.start()
        _isPlaying.value = true
        startProgressUpdates()
    }

    fun pause() {
        player?.pause()
        _isPlaying.value = false
        stopProgressUpdates()
    }

    fun seekTo(positionMs: Int) {
        player?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun setSpeed(speed: Float) {
        player?.let {
            it.playbackParams = PlaybackParams().setSpeed(speed)
            _speed.value = speed
        }
    }

    fun release() {
        stopProgressUpdates()
        player?.release()
        player = null
        _isPlaying.value = false
        _currentPosition.value = 0
        _duration.value = 0
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = scope.launch {
            while (_isPlaying.value) {
                player?.let {
                    _currentPosition.value = it.currentPosition
                }
                delay(500)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }
}
