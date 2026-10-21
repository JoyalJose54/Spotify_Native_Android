package com.example.spotify_clone.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.spotify_clone.data.models.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SleepTimerOption(val label: String, val minutes: Int?) {
    FIVE_MINUTES("5 minutes", 5),
    TEN_MINUTES("10 minutes", 10),
    FIFTEEN_MINUTES("15 minutes", 15),
    THIRTY_MINUTES("30 minutes", 30),
    FORTY_FIVE_MINUTES("45 minutes", 45),
    ONE_HOUR("1 hour", 60),
    END_OF_TRACK("End of track", null);

    val toastMessage: String
        get() = if (this == END_OF_TRACK) {
            "Your sleep timer is set for the end of this track"
        } else {
            "Your sleep timer is set for $label"
        }
}

@OptIn(UnstableApi::class)
class PlayerConnection(private val context: Context) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set

    private val scope = CoroutineScope(Dispatchers.Main)
    private var positionJob: Job? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playlist = MutableStateFlow<List<Song>>(emptyList())
    val playlist: StateFlow<List<Song>> = _playlist.asStateFlow()

    private val _currentPlaylistName = MutableStateFlow<String?>("All Songs")
    val currentPlaylistName: StateFlow<String?> = _currentPlaylistName.asStateFlow()

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private var sleepTimerJob: Job? = null
    private val _activeSleepTimerOption = MutableStateFlow<SleepTimerOption?>(null)
    val activeSleepTimerOption: StateFlow<SleepTimerOption?> = _activeSleepTimerOption.asStateFlow()

    private val prefs = context.getSharedPreferences("spotify_player_prefs", Context.MODE_PRIVATE)

    init {
        _currentSong.value = loadLastSong()
        connect()
    }

    private fun saveLastSong(song: Song) {
        prefs.edit()
            .putString("last_song_id", song.id)
            .putString("last_song_title", song.title)
            .putString("last_song_artist", song.artist)
            .putString("last_song_album", song.album)
            .putString("last_song_imageUrl", song.imageUrl)
            .putString("last_song_streamUrl", song.streamUrl)
            .putLong("last_song_duration", song.durationMs)
            .apply()
    }

    private fun loadLastSong(): Song? {
        val id = prefs.getString("last_song_id", null) ?: return null
        val title = prefs.getString("last_song_title", "") ?: ""
        val artist = prefs.getString("last_song_artist", "") ?: ""
        val album = prefs.getString("last_song_album", "") ?: ""
        val imageUrl = prefs.getString("last_song_imageUrl", "") ?: ""
        val streamUrl = prefs.getString("last_song_streamUrl", "") ?: ""
        val duration = prefs.getLong("last_song_duration", 0L)
        return Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            imageUrl = imageUrl,
            streamUrl = streamUrl,
            durationMs = duration
        )
    }

    private fun connect() {
        val sessionToken = SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            setupPlayerListener()
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        val player = controller ?: return
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) startPositionTracking() else stopPositionTracking()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (_activeSleepTimerOption.value == SleepTimerOption.END_OF_TRACK &&
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                ) {
                    clearSleepTimer()
                    controller?.pause()
                    return
                }
                val mediaId = mediaItem?.mediaId
                val matchedIndex = _playlist.value.indexOfFirst { it.id == mediaId }
                if (matchedIndex >= 0) {
                    _queueIndex.value = matchedIndex
                    val current = _playlist.value[matchedIndex]
                    _currentSong.value = current
                    saveLastSong(current)
                } else if (mediaItem != null) {
                    val fallback = Song(
                        id = mediaItem.mediaId,
                        title = mediaItem.mediaMetadata.title?.toString() ?: "",
                        artist = mediaItem.mediaMetadata.artist?.toString() ?: "",
                        album = mediaItem.mediaMetadata.albumTitle?.toString() ?: "",
                        imageUrl = mediaItem.mediaMetadata.artworkUri?.toString() ?: "",
                        streamUrl = ""
                    )
                    _currentSong.value = fallback
                    saveLastSong(fallback)
                }
                _durationMs.value = player.duration.coerceAtLeast(0L)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED &&
                    _activeSleepTimerOption.value == SleepTimerOption.END_OF_TRACK
                ) {
                    clearSleepTimer()
                    controller?.pause()
                }
                _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _isShuffle.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }
        })
        _isPlaying.value = player.isPlaying
        _isShuffle.value = player.shuffleModeEnabled
        _repeatMode.value = player.repeatMode
        _durationMs.value = player.duration.coerceAtLeast(0L)
        _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
        if (player.isPlaying) {
            startPositionTracking()
        }

        val currentItem = player.currentMediaItem
        if (currentItem != null) {
            val matched = _playlist.value.find { it.id == currentItem.mediaId }
            if (matched != null) {
                _currentSong.value = matched
                saveLastSong(matched)
            } else if (_currentSong.value == null) {
                val reconstructed = Song(
                    id = currentItem.mediaId,
                    title = currentItem.mediaMetadata.title?.toString() ?: "",
                    artist = currentItem.mediaMetadata.artist?.toString() ?: "",
                    album = currentItem.mediaMetadata.albumTitle?.toString() ?: "",
                    imageUrl = currentItem.mediaMetadata.artworkUri?.toString() ?: "",
                    streamUrl = ""
                )
                _currentSong.value = reconstructed
                saveLastSong(reconstructed)
            }
        }
    }

    private fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                controller?.let {
                    _currentPositionMs.value = it.currentPosition.coerceAtLeast(0L)
                    if (it.duration > 0) _durationMs.value = it.duration
                }
                delay(200)
            }
        }
    }

    private fun stopPositionTracking() {
        positionJob?.cancel()
        controller?.let {
            _currentPositionMs.value = it.currentPosition.coerceAtLeast(0L)
        }
    }

    fun playSong(song: Song, queue: List<Song> = emptyList(), playlistName: String = "", startIndex: Int = -1) {
        val player = controller ?: return
        val currentQueue = if (queue.isNotEmpty()) queue else listOf(song)
        _playlist.value = currentQueue
        if (playlistName.isNotEmpty()) {
            _currentPlaylistName.value = playlistName
        }

        val mediaItems = currentQueue.map { MediaPlaybackService.songToMediaItem(it) }
        val index = if (startIndex >= 0) startIndex else currentQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        _queueIndex.value = index

        player.setMediaItems(mediaItems, index, 0L)
        player.prepare()
        player.play()
        _currentSong.value = song
        saveLastSong(song)
    }

    fun skipToQueueIndex(index: Int) {
        val player = controller ?: return
        if (index in 0 until _playlist.value.size) {
            _queueIndex.value = index
            val song = _playlist.value[index]
            _currentSong.value = song
            saveLastSong(song)
            player.seekToDefaultPosition(index)
            player.play()
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.currentMediaItem == null && _currentSong.value != null) {
            playSong(_currentSong.value!!)
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun seekNext() {
        controller?.seekToNextMediaItem()
    }

    fun seekPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        val player = controller ?: return
        val newState = !player.shuffleModeEnabled
        player.shuffleModeEnabled = newState
        _isShuffle.value = newState
    }

    fun toggleRepeatMode() {
        val player = controller ?: return
        val nextMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextMode
        _repeatMode.value = nextMode
    }

    fun setSleepTimer(option: SleepTimerOption) {
        sleepTimerJob?.cancel()
        _activeSleepTimerOption.value = option

        if (option == SleepTimerOption.END_OF_TRACK) {
            // Handled when track finishes
        } else {
            val durationMs = (option.minutes ?: 0) * 60 * 1000L
            sleepTimerJob = scope.launch {
                delay(durationMs)
                controller?.pause()
                clearSleepTimer()
            }
        }
    }

    fun clearSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _activeSleepTimerOption.value = null
    }
}
