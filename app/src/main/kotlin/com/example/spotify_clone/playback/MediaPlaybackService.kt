package com.example.spotify_clone.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.spotify_clone.MainActivity
import com.example.spotify_clone.data.models.Song
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
object MediaCache {
    private var instance: SimpleCache? = null

    @Synchronized
    fun get(context: Context): SimpleCache {
        if (instance == null) {
            val cacheDir = File(context.cacheDir, "spotify_audio_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(500L * 1024L * 1024L) // 500 MB LRU disk cache
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            instance = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return instance!!
    }
}

@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val upstreamFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        // 1. Persistent Local Storage Disk Cache:
        // Caches downloaded song data so playback seamlessly survives network drops and connection loss
        val simpleCache = MediaCache.get(this)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

        // 2. Aggressive Pre-buffering:
        // Rapidly downloads ahead (up to 5 min / entire track) and retains 60s back-buffer for 0ms seeking
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 60_000,          // Rapidly downloads at least 60s
                /* maxBufferMs = */ 300_000,         // Pre-buffers up to 5 minutes (entire track!)
                /* bufferForPlaybackMs = */ 1_000,    // Starts playback in just 1s
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ 60_000, // Retains 60s behind playback for 0ms instant back-seeking
                /* retainBackBufferFromKeyframe = */ true
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val player = mediaSession?.player
            if (player != null) {
                player.pause()
                player.stop()
                player.clearMediaItems()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            // ignore
        }
        super.onTaskRemoved(rootIntent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    companion object {
        fun songToMediaItem(song: Song): MediaItem {
            return MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(Uri.parse(song.streamUrl))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(if (song.imageUrl.isNotEmpty()) Uri.parse(song.imageUrl) else null)
                        .build()
                )
                .build()
        }
    }
}
