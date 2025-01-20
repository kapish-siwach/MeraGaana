package com.first.meragaana.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.first.meragaana.R
import com.first.meragaana.utils.AudioFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

class MediaPlayerService : Service() {
    private var player: ExoPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat
    private val binder = LocalBinder()
    private lateinit var prefs: SharedPreferences
    
    private val _currentSong = MutableStateFlow<AudioFile?>(null)
    val currentSong: StateFlow<AudioFile?> = _currentSong
    
    val isPlaying: Boolean
        get() = player?.isPlaying ?: false
    
    private var queue: List<AudioFile> = emptyList()
    var currentSongPosition = -1
    var isShuffleEnabled: Boolean = false
        private set
    var repeatMode: Int = REPEAT_OFF
        private set

    private var playbackStateChangeListener: ((Boolean) -> Unit)? = null
    private var songChangeListener: ((AudioFile?) -> Unit)? = null

    fun setOnPlaybackStateChangeListener(listener: (Boolean) -> Unit) {
        playbackStateChangeListener = listener
        // Immediately notify the current state
        listener(player?.isPlaying == true)
    }

    fun setOnSongChangeListener(listener: (AudioFile?) -> Unit) {
        songChangeListener = listener
        // Immediately notify the current song
        listener(_currentSong.value)
    }

    private fun notifyPlaybackStateChanged() {
        playbackStateChangeListener?.invoke(player?.isPlaying == true)
    }

    private fun notifySongChanged() {
        songChangeListener?.invoke(_currentSong.value)
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlayerService = this@MediaPlayerService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
        initializePlayer()
        initializeMediaSession()
        restorePlaybackState()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for music playback controls"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun initializePlayer() {
        Log.d(TAG, "Initializing ExoPlayer")
        player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                addListener(playerListener)
                playWhenReady = true
            }
    }
    
    private fun initializeMediaSession() {
        Log.d(TAG, "Initializing MediaSession")
        mediaSession = MediaSessionCompat(this, "MeraGaana").apply {
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE, 0, 1f)
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
                    )
                    .build()
            )
            setCallback(mediaSessionCallback)
            isActive = true
        }
    }
    
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "Playback state changed to: $playbackState")
            when (playbackState) {
                Player.STATE_READY -> {
                    val isPlaying = player?.isPlaying == true
                    Log.d(TAG, "Player ready, isPlaying: $isPlaying")
                    updatePlaybackState()
                    updateNotification()
                    if (isPlaying) {
                        startForeground(NOTIFICATION_ID, createNotification())
                    }
                    notifyPlaybackStateChanged()
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "Playback ended")
                    updatePlaybackState()
                    updateNotification()
                    onSongCompleted()
                }
                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "Buffering...")
                }
                Player.STATE_IDLE -> {
                    Log.d(TAG, "Player idle")
                }
            }
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "IsPlaying changed to: $isPlaying")
            updatePlaybackState()
            updateNotification()
            notifyPlaybackStateChanged()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "Player error: ${error.message}", error)
        }
    }
    
    private fun savePlaybackState() {
        val currentSong = _currentSong.value
        val position = player?.currentPosition ?: 0
        
        prefs.edit().apply {
            currentSong?.let {
                putString(PREF_LAST_SONG_ID, it.id.toString())
                putString(PREF_LAST_SONG_URI, it.uri.toString())
                putString(PREF_LAST_SONG_TITLE, it.title)
                putString(PREF_LAST_SONG_ARTIST, it.artist)
                putString(PREF_LAST_SONG_ALBUM, it.album)
                putLong(PREF_LAST_POSITION, position)
                putBoolean(PREF_WAS_PLAYING, isPlaying)
            }
            apply()
        }
        Log.d(TAG, "Saved playback state: ${currentSong?.title}, position: $position")
    }
    
    private fun restorePlaybackState() {
        val songId = prefs.getString(PREF_LAST_SONG_ID, null)
        val songUri = prefs.getString(PREF_LAST_SONG_URI, null)
        val wasPlaying = prefs.getBoolean(PREF_WAS_PLAYING, false)
        
        if (songId != null && songUri != null) {
            val lastSong = AudioFile(
                id = songId.toLong(),
                uri = android.net.Uri.parse(songUri),
                title = prefs.getString(PREF_LAST_SONG_TITLE, "") ?: "",
                artist = prefs.getString(PREF_LAST_SONG_ARTIST, "") ?: "",
                album = prefs.getString(PREF_LAST_SONG_ALBUM, "") ?: "",
                duration = 0L,
                path = "",
                albumArtUri = null,
                size = 0L,
                dateAdded = 0L,
                dateModified = 0L
            )
            
            val position = prefs.getLong(PREF_LAST_POSITION, 0)
            Log.d(TAG, "Restoring playback: ${lastSong.title}, position: $position")
            
            _currentSong.value = lastSong
            playSong(position.toInt(), autoPlay = wasPlaying)
        }
    }
    
    fun playSong(position: Int, startPosition: Long = 0, autoPlay: Boolean = true) {
        if (position !in queue.indices) return

        Log.d(TAG, "Playing song: ${queue[position].title}, URI: ${queue[position].uri}, startPosition: $startPosition")
        _currentSong.value = queue[position]
        
        try {
            val mediaItem = MediaItem.Builder()
                .setMediaId(queue[position].id.toString())
                .setUri(queue[position].uri)
                .build()
            
            player?.let { exoPlayer ->
                Log.d(TAG, "Setting media item")
                exoPlayer.setMediaItem(mediaItem)
                Log.d(TAG, "Preparing player")
                exoPlayer.prepare()
                if (startPosition > 0) {
                    exoPlayer.seekTo(startPosition)
                }
                if (autoPlay) {
                    Log.d(TAG, "Starting playback")
                    exoPlayer.play()
                }
            } ?: run {
                Log.e(TAG, "Player is null, reinitializing...")
                initializePlayer()
                player?.let { newPlayer ->
                    newPlayer.setMediaItem(mediaItem)
                    newPlayer.prepare()
                    if (startPosition > 0) {
                        newPlayer.seekTo(startPosition)
                    }
                    if (autoPlay) {
                        newPlayer.play()
                    }
                } ?: Log.e(TAG, "Failed to initialize player!")
            }
            
            updateMediaSession(queue[position])
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            notifySongChanged()
            notifyPlaybackStateChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing song", e)
            throw e
        }
    }
    
    fun setQueue(songs: List<AudioFile>) {
        queue = songs
    }

    fun getQueue(): List<AudioFile> = queue

    fun getCurrentSong(): AudioFile? = 
        if (currentSongPosition in queue.indices) queue[currentSongPosition] else null

    fun play() {
        Log.d(TAG, "Play requested")
        player?.play()
        notifyPlaybackStateChanged()
    }
    
    fun pause() {
        Log.d(TAG, "Pause requested")
        player?.pause()
        notifyPlaybackStateChanged()
    }
    
    fun seekTo(position: Long) {
        Log.d(TAG, "Seek to $position")
        player?.seekTo(position)
    }
    
    fun getCurrentPosition(): Long? {
        return player?.currentPosition?.also { 
            Log.d(TAG, "Current position: $it") 
        }
    }
    
    fun getCurrentSongPosition(): Long? {
        return player?.currentPosition?.also { 
            Log.d(TAG, "Current song position: $it") 
        }
    }
    
    fun getDuration(): Long? {
        return player?.duration?.also { 
            Log.d(TAG, "Duration: $it") 
        }
    }
    
    private fun updatePlaybackState() {
        val state = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        
        val position = player?.currentPosition ?: 0L
        
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, position, 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )
    }
    
    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            super.onPlay()
            play()
        }
        
        override fun onPause() {
            super.onPause()
            pause()
        }
        
        override fun onSkipToNext() {
            super.onSkipToNext()
            playNextSong()
        }
        
        override fun onSkipToPrevious() {
            super.onSkipToPrevious()
            playPreviousSong()
        }
        
        override fun onSeekTo(pos: Long) {
            super.onSeekTo(pos)
            seekTo(pos)
        }
    }
    
    private fun updateMediaSession(audioFile: AudioFile) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, audioFile.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, audioFile.artist)
            .build()
        mediaSession.setMetadata(metadata)
    }
    
    private fun createNotification(): Notification {
        val song = _currentSong.value ?: return createEmptyNotification()
        
        val playPauseAction = createPlayPauseAction()
        val previousAction = createPreviousAction()
        val nextAction = createNextAction()
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setOngoing(isPlaying)
            .addAction(previousAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        
        return builder.build()
    }
    
    private fun createEmptyNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle("MeraGaana")
            .setContentText("No song playing")
            .build()
    }
    
    private fun createPlayPauseAction(): NotificationCompat.Action {
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val title = if (isPlaying) "Pause" else "Play"
        val intent = Intent(this, MediaPlayerService::class.java).apply {
            action = ACTION_TOGGLE_PLAYBACK
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(icon, title, pendingIntent)
    }
    
    private fun createPreviousAction(): NotificationCompat.Action {
        val intent = Intent(this, MediaPlayerService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(R.drawable.ic_previous, "Previous", pendingIntent)
    }
    
    private fun createNextAction(): NotificationCompat.Action {
        val intent = Intent(this, MediaPlayerService::class.java).apply {
            action = ACTION_NEXT
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(R.drawable.ic_next, "Next", pendingIntent)
    }
    
    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAYBACK -> {
                if (isPlaying) pause() else play()
            }
            ACTION_NEXT -> {
                playNextSong()
            }
            ACTION_PREVIOUS -> {
                playPreviousSong()
            }
        }
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        savePlaybackState()
        player?.release()
        player = null
        mediaSession.release()
    }
    
    private fun onSongCompleted() {
        Log.d(TAG, "Song completed, repeat mode: $repeatMode")
        when (repeatMode) {
            REPEAT_OFF -> playNextSong()
            REPEAT_ONE -> {
                // Replay the current song
                player?.seekTo(0)
                player?.play()
            }
            REPEAT_ALL -> {
                // Play next song, will automatically wrap around to first song
                playNextSong()
            }
        }
    }

    private fun playNextSong() {
        if (queue.isEmpty()) return
        
        val nextPosition = when {
            isShuffleEnabled -> {
                // Get random position excluding current position
                val availablePositions = (queue.indices).filter { it != currentSongPosition }
                if (availablePositions.isNotEmpty()) {
                    availablePositions[Random.nextInt(availablePositions.size)]
                } else {
                    return
                }
            }
            currentSongPosition == queue.lastIndex -> {
                if (repeatMode == REPEAT_ALL) 0 else return
            }
            else -> currentSongPosition + 1
        }
        
        playSong(nextPosition)
        currentSongPosition = nextPosition
    }

    private fun playPreviousSong() {
        if (queue.isEmpty()) return
        val previousPosition = if (currentSongPosition > 0) currentSongPosition - 1 else queue.size - 1
        playSong(previousPosition)
        currentSongPosition = previousPosition
    }

    fun setShuffleEnabled(enabled: Boolean) {
        Log.d(TAG, "Shuffle enabled: $enabled")
        isShuffleEnabled = enabled
    }

    fun setRepeatMode(mode: Int) {
        Log.d(TAG, "Repeat mode set to: $mode")
        repeatMode = mode
    }

    fun skipToNext() {
        if (queue.isEmpty()) return
        
        val nextPosition = when {
            isShuffleEnabled -> {
                // Get random position excluding current position
                val availablePositions = (queue.indices).filter { it != currentSongPosition }
                if (availablePositions.isNotEmpty()) {
                    availablePositions[Random.nextInt(availablePositions.size)]
                } else {
                    return
                }
            }
            currentSongPosition == queue.lastIndex -> {
                if (repeatMode == REPEAT_ALL) 0 else return
            }
            else -> currentSongPosition + 1
        }
        
        playSong(nextPosition)
        currentSongPosition = nextPosition
    }

    fun skipToPrevious() {
        if (queue.isEmpty()) return
        val previousPosition = if (currentSongPosition > 0) currentSongPosition - 1 else queue.size - 1
        playSong(previousPosition)
        currentSongPosition = previousPosition
    }

    companion object {
        private const val TAG = "MediaPlayerService"
        private const val CHANNEL_ID = "MusicPlayback"
        private const val NOTIFICATION_ID = 1
        private const val PREFS_NAME = "MediaPlayerPrefs"
        private const val PREF_LAST_SONG_ID = "last_song_id"
        private const val PREF_LAST_SONG_URI = "last_song_uri"
        private const val PREF_LAST_SONG_TITLE = "last_song_title"
        private const val PREF_LAST_SONG_ARTIST = "last_song_artist"
        private const val PREF_LAST_SONG_ALBUM = "last_song_album"
        private const val PREF_LAST_POSITION = "last_position"
        private const val PREF_WAS_PLAYING = "was_playing"
        const val ACTION_TOGGLE_PLAYBACK = "com.first.meragaana.TOGGLE_PLAYBACK"
        const val ACTION_NEXT = "com.first.meragaana.NEXT"
        const val ACTION_PREVIOUS = "com.first.meragaana.PREVIOUS"
        const val REPEAT_OFF = 0
        const val REPEAT_ONE = 1
        const val REPEAT_ALL = 2
    }
}
