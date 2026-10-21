package com.example.spotify_clone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotify_clone.data.models.Playlist
import com.example.spotify_clone.data.models.Song
import com.example.spotify_clone.data.remote.FirebaseRepository
import com.example.spotify_clone.playback.PlayerConnection
import com.example.spotify_clone.ui.components.MiniPlayer
import com.example.spotify_clone.ui.components.PasswordChallengeDialog
import com.example.spotify_clone.ui.components.PlayerBottomSheet
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily
import com.example.spotify_clone.ui.theme.rememberPaletteColors
import kotlinx.coroutines.launch

enum class MainTab { HOME, SEARCH, LIBRARY }

@Composable
fun MainScreen(
    repository: FirebaseRepository,
    playerConnection: PlayerConnection
) {
    val context = LocalContext.current
    val userName = remember {
        val prefs = context.getSharedPreferences("spotify_prefs", Context.MODE_PRIVATE)
        val fromPrefs = prefs.getString("user_name", "") ?: ""
        fromPrefs.ifEmpty { repository.currentUserName }
    }

    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var pendingProtectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var isNowPlayingExpanded by remember { mutableStateOf(false) }

    // Navigation sub-destinations
    var activeAlbum by remember { mutableStateOf<Triple<String, String, String>?>(null) } // (id, title, img)
    var isTopSongsOpen by remember { mutableStateOf(false) }
    var isTopArtistsOpen by remember { mutableStateOf(false) }
    var isSmartIngestionOpen by remember { mutableStateOf(false) }
    var addSongsForPlaylist by remember { mutableStateOf<Pair<String, List<String>>?>(null) } // (playlistId, existingIds)
    var editPlaylistForPlaylist by remember { mutableStateOf<Pair<String, List<Song>>?>(null) } // (playlistId, songs)

    val allSongs by repository.streamAllSongs().collectAsState(initial = emptyList())
    val rawPlaylists by repository.streamPlaylists().collectAsState(initial = emptyList())
    val playlistOrder by repository.streamPlaylistOrder().collectAsState(initial = repository.getCachedPlaylistOrder())
    val recentlyPlayed by repository.streamRecentlyPlayed().collectAsState(initial = emptyList())
    val topTracks by repository.streamTopTracks(limit = 1).collectAsState(initial = emptyList())
    val topArtists by repository.streamTopArtists(limit = 1).collectAsState(initial = emptyList())

    val topSongTitle = remember(topTracks) {
        topTracks.firstOrNull()?.first?.title
    }
    val topArtistName = remember(topArtists) {
        topArtists.firstOrNull()?.artist
    }

    val playlists = remember(rawPlaylists, playlistOrder) {
        if (rawPlaylists.isEmpty()) emptyList()
        else com.example.spotify_clone.utils.PlaylistOrderUtils.sortPlaylistsByOrder(rawPlaylists, playlistOrder)
    }

    val currentSong by playerConnection.currentSong.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val isBuffering by playerConnection.isBuffering.collectAsState()
    val isShuffle by playerConnection.isShuffle.collectAsState()
    val positionMs by playerConnection.currentPositionMs.collectAsState()
    val durationMs by playerConnection.durationMs.collectAsState()
    val playlistName by playerConnection.currentPlaylistName.collectAsState()
    val queue by playerConnection.playlist.collectAsState()
    val queueIndex by playerConnection.queueIndex.collectAsState()
    val likedSongIds by repository.streamLikedSongIds().collectAsState(initial = emptySet())

    val scope = rememberCoroutineScope()

    // 100% Flutter-matching Back Navigation Stack
    BackHandler(
        enabled = isNowPlayingExpanded ||
                addSongsForPlaylist != null ||
                editPlaylistForPlaylist != null ||
                isSmartIngestionOpen ||
                isTopSongsOpen ||
                isTopArtistsOpen ||
                activeAlbum != null ||
                selectedPlaylist != null ||
                currentTab != MainTab.HOME
    ) {
        when {
            isNowPlayingExpanded -> isNowPlayingExpanded = false
            addSongsForPlaylist != null -> addSongsForPlaylist = null
            editPlaylistForPlaylist != null -> editPlaylistForPlaylist = null
            isSmartIngestionOpen -> isSmartIngestionOpen = false
            isTopSongsOpen -> isTopSongsOpen = false
            isTopArtistsOpen -> isTopArtistsOpen = false
            activeAlbum != null -> activeAlbum = null
            selectedPlaylist != null -> selectedPlaylist = null
            currentTab != MainTab.HOME -> currentTab = MainTab.HOME
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyColors.black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    isSmartIngestionOpen -> {
                        IngestionScreen(
                            repository = repository,
                            onBack = { isSmartIngestionOpen = false }
                        )
                    }
                    isTopSongsOpen -> {
                        TopSongsScreen(
                            repository = repository,
                            playerConnection = playerConnection,
                            onBack = { isTopSongsOpen = false }
                        )
                    }
                    isTopArtistsOpen -> {
                        TopArtistsScreen(
                            repository = repository,
                            playerConnection = playerConnection,
                            onBack = { isTopArtistsOpen = false }
                        )
                    }
                    activeAlbum != null -> {
                        val album = activeAlbum!!
                        AlbumScreen(
                            albumId = album.first,
                            albumTitle = album.second,
                            albumImageUrl = album.third,
                            repository = repository,
                            playerConnection = playerConnection,
                            onBack = { activeAlbum = null }
                        )
                    }
                    addSongsForPlaylist != null -> {
                        val data = addSongsForPlaylist!!
                        AddSongsScreen(
                            playlistId = data.first,
                            existingTrackIds = data.second,
                            repository = repository,
                            onBack = { addSongsForPlaylist = null }
                        )
                    }
                    editPlaylistForPlaylist != null -> {
                        val data = editPlaylistForPlaylist!!
                        EditPlaylistScreen(
                            playlistId = data.first,
                            initialSongs = data.second,
                            repository = repository,
                            onBack = { editPlaylistForPlaylist = null }
                        )
                    }
                    selectedPlaylist != null -> {
                        PlaylistDetailScreen(
                            playlist = selectedPlaylist!!,
                            playlistSongsFlow = repository.streamPlaylistSongs(selectedPlaylist!!.id),
                            currentPlayingSongId = currentSong?.id,
                            repository = repository,
                            onBackClick = { selectedPlaylist = null },
                            onSongClick = { song, playlistQueue ->
                                scope.launch {
                                    repository.incrementPlayCount(song.id)
                                    repository.recordRecentlyPlayed(song.id)
                                }
                                playerConnection.playSong(
                                    song = song,
                                    queue = playlistQueue,
                                    playlistName = selectedPlaylist!!.name
                                )
                            },
                            onNavigateToAddSongs = { plId, existingIds ->
                                addSongsForPlaylist = Pair(plId, existingIds)
                            },
                            onNavigateToEditPlaylist = { plId, songs ->
                                editPlaylistForPlaylist = Pair(plId, songs)
                            },
                            onNavigateToAlbum = { albId, albTitle, albImg ->
                                activeAlbum = Triple(albId, albTitle, albImg)
                            },
                            onPlaylistDeleted = { selectedPlaylist = null }
                        )
                    }
                    else -> {
                        val openPlaylist: (Playlist) -> Unit = { pl ->
                            if (pl.id == "all_songs") {
                                pendingProtectedPlaylist = pl
                            } else {
                                selectedPlaylist = pl
                            }
                        }

                        when (currentTab) {
                            MainTab.HOME -> HomeScreen(
                                allSongs = allSongs,
                                playlists = playlists,
                                currentPlayingSongId = currentSong?.id,
                                userName = userName,
                                recentlyPlayed = recentlyPlayed,
                                topSongTitle = topSongTitle,
                                topArtistName = topArtistName,
                                onPlaylistClick = openPlaylist,
                                onSongClick = { song, homeQueue ->
                                    scope.launch {
                                        repository.incrementPlayCount(song.id)
                                        repository.recordRecentlyPlayed(song.id)
                                    }
                                    playerConnection.playSong(
                                        song = song,
                                        queue = homeQueue,
                                        playlistName = "Home"
                                    )
                                },
                                onNavigateToTopSongs = { isTopSongsOpen = true },
                                onNavigateToTopArtists = { isTopArtistsOpen = true }
                            )
                            MainTab.SEARCH -> SearchScreen(
                                allSongs = allSongs,
                                currentPlayingSongId = currentSong?.id,
                                repository = repository,
                                onSongClick = { song, searchQueue ->
                                    scope.launch {
                                        repository.incrementPlayCount(song.id)
                                        repository.recordRecentlyPlayed(song.id)
                                    }
                                    playerConnection.playSong(
                                        song = song,
                                        queue = searchQueue,
                                        playlistName = "Search"
                                    )
                                }
                            )
                            MainTab.LIBRARY -> LibraryScreen(
                                playlists = playlists,
                                totalSongCount = allSongs.size,
                                allSongs = allSongs,
                                onPlaylistClick = openPlaylist,
                                onCreatePlaylist = { name ->
                                    scope.launch {
                                        repository.createPlaylist(name)
                                    }
                                },
                                onReorderPlaylists = { newOrder ->
                                    scope.launch {
                                        repository.savePlaylistOrder(newOrder)
                                    }
                                },
                                onOpenSmartIngestion = { isSmartIngestionOpen = true }
                            )
                        }
                    }
                }
            }

            // Reserve space for floating MiniPlayer above bottom nav bar
            if (currentSong != null) {
                Spacer(modifier = Modifier.height(66.dp))
            }

            // Spotify Pure Black Bottom Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .navigationBarsPadding()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpotifyNavItem(
                    selected = currentTab == MainTab.HOME && selectedPlaylist == null && !isTopSongsOpen && !isTopArtistsOpen && !isSmartIngestionOpen && activeAlbum == null,
                    activeIcon = Icons.Filled.Home,
                    inactiveIcon = Icons.Outlined.Home,
                    label = "Home",
                    onClick = {
                        selectedPlaylist = null
                        activeAlbum = null
                        isTopSongsOpen = false
                        isTopArtistsOpen = false
                        isSmartIngestionOpen = false
                        addSongsForPlaylist = null
                        editPlaylistForPlaylist = null
                        currentTab = MainTab.HOME
                    },
                    modifier = Modifier.weight(1f)
                )
                SpotifyNavItem(
                    selected = currentTab == MainTab.SEARCH && selectedPlaylist == null,
                    activeIcon = Icons.Filled.Search,
                    inactiveIcon = Icons.Filled.Search,
                    label = "Search",
                    onClick = {
                        selectedPlaylist = null
                        activeAlbum = null
                        isTopSongsOpen = false
                        isTopArtistsOpen = false
                        isSmartIngestionOpen = false
                        addSongsForPlaylist = null
                        editPlaylistForPlaylist = null
                        currentTab = MainTab.SEARCH
                    },
                    modifier = Modifier.weight(1f)
                )
                SpotifyNavItem(
                    selected = currentTab == MainTab.LIBRARY && selectedPlaylist == null && !isSmartIngestionOpen,
                    activeIcon = Icons.Filled.LibraryMusic,
                    inactiveIcon = Icons.Outlined.LibraryMusic,
                    label = "Your Library",
                    onClick = {
                        selectedPlaylist = null
                        activeAlbum = null
                        isTopSongsOpen = false
                        isTopArtistsOpen = false
                        isSmartIngestionOpen = false
                        addSongsForPlaylist = null
                        editPlaylistForPlaylist = null
                        currentTab = MainTab.LIBRARY
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Convx Player Bottom Sheet (Morphing MiniPlayer <-> Full NowPlayingScreen)
        if (currentSong != null) {
            val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val totalBottomBarHeight = 56.dp + navBarPadding
            val currentSongPalette = rememberPaletteColors(currentSong!!.imageUrl)

            PlayerBottomSheet(
                isExpanded = isNowPlayingExpanded,
                onExpandedChange = { isNowPlayingExpanded = it },
                bottomBarHeight = totalBottomBarHeight,
                containerColor = currentSongPalette.bgColor,
                miniPlayer = {
                    MiniPlayer(
                        song = currentSong!!,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onPlayPauseClick = { playerConnection.togglePlayPause() },
                        onNextClick = { playerConnection.seekNext() },
                        onPreviousClick = { playerConnection.seekPrevious() },
                        onExpandClick = { isNowPlayingExpanded = true },
                        applyCardShape = false
                    )
                },
                fullPlayer = {
                    NowPlayingScreen(
                        song = currentSong!!,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        isShuffle = isShuffle,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        playlistName = playlistName,
                        queue = queue,
                        queueIndex = queueIndex,
                        onPlayPauseClick = { playerConnection.togglePlayPause() },
                        onNextClick = { playerConnection.seekNext() },
                        onPreviousClick = { playerConnection.seekPrevious() },
                        onSeek = { targetMs -> playerConnection.seekTo(targetMs) },
                        onShuffleClick = { playerConnection.toggleShuffle() },
                        onRepeatClick = { playerConnection.toggleRepeatMode() },
                        onQueueItemClick = { index -> playerConnection.skipToQueueIndex(index) },
                        onCollapseClick = { isNowPlayingExpanded = false },
                        repository = repository,
                        playerConnection = playerConnection,
                        onNavigateToAlbum = { albId, albTitle, albImg ->
                            isNowPlayingExpanded = false
                            activeAlbum = Triple(albId, albTitle, albImg)
                        }
                    )
                }
            )
        }

        // Master Library Password Challenge Dialog (For "All Songs" playlist only)
        pendingProtectedPlaylist?.let { pl ->
            PasswordChallengeDialog(
                onSuccess = {
                    pendingProtectedPlaylist = null
                    selectedPlaylist = pl
                },
                onDismiss = {
                    pendingProtectedPlaylist = null
                }
            )
        }
    }
}

@Composable
private fun SpotifyNavItem(
    selected: Boolean,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) activeIcon else inactiveIcon,
            contentDescription = label,
            tint = if (selected) Color.White else SpotifyColors.lightGrey,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontFamily = SpotifyMixFontFamily,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 10.sp,
            color = if (selected) Color.White else SpotifyColors.lightGrey
        )
    }
}
