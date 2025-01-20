package com.first.meragaana.service

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.first.meragaana.data.PlaybackQuality
import com.first.meragaana.utils.AudioFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Random

class MusicService : Service() {
    enum class RepeatMode {
        NONE, ONE, ALL
    }

    private var mediaPlayer: MediaPlayer? = null
    private val binder = LocalBinder()
    private var currentAudio: AudioFile? = null
    private var playlist: List<AudioFile> = emptyList()
    private var currentIndex = 0
    private var isShuffleOn = false
    private var repeatMode = RepeatMode.NONE
    private var playbackQuality = PlaybackQuality.HIGH
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var playbackStateListeners = mutableListOf<() -> Unit>()

    inner class LocalBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        initMediaPlayer()
    }

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setOnPreparedListener { mp ->
                mp.start()
                notifyPlaybackStateChanged()
            }
            setOnCompletionListener {
                when (repeatMode) {
                    RepeatMode.ONE -> playCurrentSong()
                    RepeatMode.ALL -> playNext()
                    RepeatMode.NONE -> if (currentIndex < playlist.lastIndex) playNext()
                }
            }
            setOnErrorListener { _, what, extra ->
                Log.e("MusicService", "MediaPlayer error: what=$what, extra=$extra")
                true
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    fun setPlaylist(newPlaylist: List<AudioFile>, startIndex: Int = 0) {
        playlist = newPlaylist
        currentIndex = startIndex.coerceIn(0, playlist.lastIndex)
        playCurrentSong()
    }

    fun getCurrentAudio(): AudioFile? = currentAudio

    fun playCurrentSong() {
        if (playlist.isEmpty()) return

        currentAudio = playlist[currentIndex]
        mediaPlayer?.apply {
            reset()
            try {
                setDataSource(applicationContext, currentAudio?.uri ?: return)
                prepareAsync()
            } catch (e: Exception) {
                Log.e("MusicService", "Error setting data source", e)
            }
        }
        notifyPlaybackStateChanged()
    }

    fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.start()
            }
            notifyPlaybackStateChanged()
        }
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        
        currentIndex = if (isShuffleOn) {
            Random().nextInt(playlist.size)
        } else {
            (currentIndex + 1) % playlist.size
        }
        playCurrentSong()
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        
        currentIndex = if (isShuffleOn) {
            Random().nextInt(playlist.size)
        } else if (currentIndex > 0) {
            currentIndex - 1
        } else {
            playlist.lastIndex
        }
        playCurrentSong()
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun addPlaybackStateListener(listener: () -> Unit) {
        playbackStateListeners.add(listener)
    }

    fun removePlaybackStateListener(listener: () -> Unit) {
        playbackStateListeners.remove(listener)
    }

    private fun notifyPlaybackStateChanged() {
        playbackStateListeners.forEach { it.invoke() }
    }
}
