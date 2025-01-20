package com.first.meragaana.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.first.meragaana.service.MediaPlayerService
import com.first.meragaana.utils.AudioFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val _currentSong = MutableStateFlow<AudioFile?>(null)
    val currentSong: StateFlow<AudioFile?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _queue = MutableStateFlow<List<AudioFile>>(emptyList())
    val queue: StateFlow<List<AudioFile>> = _queue

    private val _currentSongPosition = MutableStateFlow(-1)
    val currentSongPosition: StateFlow<Int> = _currentSongPosition

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled

    private val _repeatMode = MutableStateFlow(MediaPlayerService.REPEAT_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    private var mediaPlayerService: MediaPlayerService? = null
    private var bound = false
    private var positionUpdateJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlayerService.LocalBinder
            mediaPlayerService = binder.getService()
            bound = true
            
            // Initialize state from service
            mediaPlayerService?.let { playerService ->
                updatePlayerState(playerService)
                
                // Set up service callbacks
                playerService.setOnPlaybackStateChangeListener { isPlaying ->
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        startPositionUpdates()
                    } else {
                        stopPositionUpdates()
                    }
                }
                
                playerService.setOnSongChangeListener { song ->
                    _currentSong.value = song
                    _duration.value = playerService.getDuration() ?: 0L
                    _currentPosition.value = 0L
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mediaPlayerService = null
            bound = false
            stopPositionUpdates()
        }
    }

    init {
        bindService()
    }

    private fun bindService() {
        Intent(getApplication(), MediaPlayerService::class.java).also { intent ->
            getApplication<Application>().bindService(
                intent,
                connection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    fun playSong(position: Int, startPosition: Long = 0L) {
        viewModelScope.launch {
            try {
                if (position in 0 until (_queue.value.size)) {
                    Log.d(TAG, "Playing song at position $position")
                    mediaPlayerService?.playSong(position, startPosition)
                    _currentSongPosition.value = position
                    _currentSong.value = _queue.value[position]
                    _isPlaying.value = true
                    
                    // Wait briefly for the media player to initialize
                    delay(100)
                    mediaPlayerService?.getDuration()?.let { duration ->
                        if (duration > 0) {
                            _duration.value = duration
                        }
                    }
                    startPositionUpdates()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing song", e)
            }
        }
    }

    private fun updatePlayerState(playerService: MediaPlayerService) {
        _currentSong.value = playerService.getCurrentSong()
        _isPlaying.value = playerService.isPlaying
        _currentPosition.value = playerService.getCurrentPosition() ?: 0L
        _duration.value = playerService.getDuration() ?: 0L
        _queue.value = playerService.getQueue()
        _currentSongPosition.value = playerService.currentSongPosition
        _isShuffleEnabled.value = playerService.isShuffleEnabled
        _repeatMode.value = playerService.repeatMode
        
        if (playerService.isPlaying) {
            startPositionUpdates()
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = viewModelScope.launch {
            while (true) {
                mediaPlayerService?.let { service ->
                    service.getCurrentPosition()?.let { position ->
                        _currentPosition.value = position
                    }
                    // Update duration periodically in case it changes
                    service.getDuration()?.let { duration ->
                        if (duration > 0) {  // Only update if we have a valid duration
                            _duration.value = duration
                        }
                    }
                }
                delay(1000) // Update every second
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    fun playPause() {
        viewModelScope.launch {
            if (_isPlaying.value) {
                mediaPlayerService?.pause()
                _isPlaying.value = false
                stopPositionUpdates()
            } else {
                mediaPlayerService?.play()
                _isPlaying.value = true
                startPositionUpdates()
            }
        }
    }

    fun seekTo(position: Long) {
        viewModelScope.launch {
            mediaPlayerService?.seekTo(position)
            _currentPosition.value = position
        }
    }

    fun next() {
        viewModelScope.launch {
            Log.d(TAG, "Next button pressed")
            mediaPlayerService?.skipToNext()
            updateCurrentSongInfo()
        }
    }

    fun previous() {
        viewModelScope.launch {
            Log.d(TAG, "Previous button pressed")
            mediaPlayerService?.skipToPrevious()
            updateCurrentSongInfo()
        }
    }

    fun toggleShuffle() {
        viewModelScope.launch {
            _isShuffleEnabled.value = !_isShuffleEnabled.value
            mediaPlayerService?.setShuffleEnabled(_isShuffleEnabled.value)
        }
    }

    fun toggleRepeatMode() {
        viewModelScope.launch {
            _repeatMode.value = when (_repeatMode.value) {
                MediaPlayerService.REPEAT_OFF -> MediaPlayerService.REPEAT_ALL
                MediaPlayerService.REPEAT_ALL -> MediaPlayerService.REPEAT_ONE
                else -> MediaPlayerService.REPEAT_OFF
            }
            mediaPlayerService?.setRepeatMode(_repeatMode.value)
        }
    }

    private fun updateCurrentSongInfo() {
        mediaPlayerService?.let { service ->
            _currentSong.value = service.getCurrentSong()?.also {
                Log.d(TAG, "Current song updated: ${it.title}")
            }
            _currentPosition.value = service.getCurrentPosition() ?: 0L
            _duration.value = service.getDuration() ?: 0L
            _isPlaying.value = service.isPlaying
            _currentSongPosition.value = service.currentSongPosition
            _isShuffleEnabled.value = service.isShuffleEnabled
            _repeatMode.value = service.repeatMode
        }
    }

    fun setQueue(songs: List<AudioFile>) {
        viewModelScope.launch {
            Log.d(TAG, "Setting queue with ${songs.size} songs")
            _queue.value = songs
            mediaPlayerService?.setQueue(songs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPositionUpdates()
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
    }

    companion object {
        private const val TAG = "PlayerViewModel"
    }
}
