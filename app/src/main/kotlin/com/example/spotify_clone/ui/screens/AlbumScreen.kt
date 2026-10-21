package com.example.spotify_clone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.spotify_clone.data.models.Song
import com.example.spotify_clone.data.remote.FirebaseRepository
import com.example.spotify_clone.playback.PlayerConnection
import com.example.spotify_clone.ui.components.SaveInPlaylistSheet
import com.example.spotify_clone.ui.components.TrackOptionsSheet
import com.example.spotify_clone.ui.theme.rememberPaletteColors
import com.example.spotify_clone.ui.theme.SpotifyColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    albumId: String,
    albumTitle: String,
    albumArtist: String = "",
    albumImageUrl: String = "",
    repository: FirebaseRepository,
    playerConnection: PlayerConnection,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var albumTracks by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    // Dominant color handled via rememberPaletteColors

    var selectedTrackForOptions by remember { mutableStateOf<Song?>(null) }
    var selectedTrackForPlaylistSave by remember { mutableStateOf<Song?>(null) }

    val currentSong by playerConnection.currentSong.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val palette = rememberPaletteColors(albumImageUrl.ifEmpty { albumTracks.firstOrNull()?.imageUrl ?: "" })
    val dominantColor = palette.dominant

    LaunchedEffect(albumTitle) {
        coroutineScope.launch {
            val tracks = repository.fetchTracksByAlbum(albumTitle)
            albumTracks = tracks
            isLoading = false
        }
    }

    // Palette extracted automatically

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        dominantColor.copy(alpha = 0.8f),
                        Color(0xFF121212),
                        SpotifyColors.black
                    ),
                    startY = 0f,
                    endY = 1200f
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = albumTitle,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SpotifyColors.green)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // Header Artwork & Details
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 24.dp)
                        ) {
                            AsyncImage(
                                model = albumImageUrl.ifEmpty { albumTracks.firstOrNull()?.imageUrl },
                                contentDescription = albumTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SpotifyColors.surface)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = albumTitle,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = albumArtist.ifEmpty { albumTracks.firstOrNull()?.artist ?: "Unknown Artist" },
                                color = SpotifyColors.lightGrey,
                                fontSize = 14.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Album • ${albumTracks.size} songs",
                                color = Color(0xFF888888),
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Big Green Play Button
                            Button(
                                onClick = {
                                    if (albumTracks.isNotEmpty()) {
                                        playerConnection.playSong(
                                            albumTracks.first(),
                                            queue = albumTracks,
                                            playlistName = albumTitle,
                                            startIndex = 0
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyColors.green),
                                shape = CircleShape,
                                modifier = Modifier.size(56.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Album",
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    // Track List (1, 2, 3...)
                    itemsIndexed(albumTracks, key = { _, s -> s.id }) { index, track ->
                        val isCurrent = currentSong?.id == track.id

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerConnection.playSong(
                                        track,
                                        queue = albumTracks,
                                        playlistName = albumTitle,
                                        startIndex = index
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            // Track Number or Green Playing Indicator
                            Text(
                                text = "${index + 1}",
                                color = if (isCurrent) SpotifyColors.green else SpotifyColors.lightGrey,
                                fontSize = 15.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.width(32.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = if (isCurrent) SpotifyColors.green else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = track.artist,
                                    color = SpotifyColors.lightGrey,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }

                            // 3-dots more menu
                            IconButton(
                                onClick = { selectedTrackForOptions = track }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = SpotifyColors.lightGrey,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Track Options Sheet
        selectedTrackForOptions?.let { track ->
            TrackOptionsSheet(
                song = track,
                currentPlaylistId = null,
                repository = repository,
                onDismiss = { selectedTrackForOptions = null },
                onAddToPlaylist = {
                    selectedTrackForPlaylistSave = track
                }
            )
        }

        // Save in Playlist Sheet
        selectedTrackForPlaylistSave?.let { track ->
            SaveInPlaylistSheet(
                song = track,
                repository = repository,
                onDismiss = { selectedTrackForPlaylistSave = null }
            )
        }
    }
}
