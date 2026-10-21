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
import androidx.compose.ui.draw.blur
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
import com.example.spotify_clone.ui.theme.SpotifyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopSongsScreen(
    repository: FirebaseRepository,
    playerConnection: PlayerConnection,
    onBack: () -> Unit
) {
    val topEntries by repository.streamTopTracks(limit = 10).collectAsState(initial = emptyList())
    val allSongs by repository.streamAllSongs().collectAsState(initial = emptyList())

    val currentSong by playerConnection.currentSong.collectAsState()

    var selectedTrackForOptions by remember { mutableStateOf<Song?>(null) }
    var selectedTrackForPlaylistSave by remember { mutableStateOf<Song?>(null) }

    val displayEntries = remember(topEntries, allSongs) {
        if (topEntries.isNotEmpty()) topEntries
        else {
            // Fallback sample when no plays yet
            allSongs.take(10).mapIndexed { i, s -> Pair(s, 10 - i) }
        }
    }

    val topSong = displayEntries.firstOrNull()?.first
    val bgColor = Color(0xFF0A0012)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Ambient background blur from #1 song cover
        if (topSong != null && topSong.imageUrl.isNotEmpty()) {
            AsyncImage(
                model = topSong.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(70.dp)
            )
        }

        // Overlay gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            bgColor.copy(alpha = 0.55f),
                            bgColor.copy(alpha = 0.85f),
                            bgColor
                        ),
                        startY = 0f,
                        endY = 1000f
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Spotify Wrapped",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
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
            },
            floatingActionButton = {
                if (displayEntries.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            val tracks = displayEntries.map { it.first }
                            playerConnection.playSong(
                                tracks.first(),
                                queue = tracks,
                                playlistName = "Your Top Songs",
                                startIndex = 0
                            )
                        },
                        containerColor = SpotifyColors.green,
                        contentColor = Color.Black,
                        shape = CircleShape,
                        modifier = Modifier.padding(bottom = 60.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play all",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header Banner
                item {
                    Column(modifier = Modifier.padding(bottom = 20.dp)) {
                        Text(
                            text = "YOUR TOP SONGS",
                            color = Color(0xFFE040FB),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Most listened tracks this year",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (topEntries.isNotEmpty()) "Calculated live from your playback history" else "Previewing recommended top tracks",
                            color = SpotifyColors.lightGrey,
                            fontSize = 13.sp
                        )
                    }
                }

                // Songs List
                itemsIndexed(displayEntries, key = { _, pair -> pair.first.id }) { index, (song, playCount) ->
                    val isCurrent = currentSong?.id == song.id
                    val rank = index + 1

                    val rankColor = when (rank) {
                        1 -> Color(0xFFFFD700) // Gold
                        2 -> Color(0xFFC0C0C0) // Silver
                        3 -> Color(0xFFCD7F32) // Bronze
                        else -> SpotifyColors.lightGrey
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val tracks = displayEntries.map { it.first }
                                playerConnection.playSong(
                                    song,
                                    queue = tracks,
                                    playlistName = "Your Top Songs",
                                    startIndex = index
                                )
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        // Rank Badge
                        Text(
                            text = "$rank",
                            color = rankColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.width(32.dp)
                        )

                        // Cover
                        AsyncImage(
                            model = song.imageUrl,
                            contentDescription = song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SpotifyColors.surface)
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = if (isCurrent) SpotifyColors.green else Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = song.artist,
                                    color = SpotifyColors.lightGrey,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "• $playCount plays",
                                    color = Color(0xFFB388FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // 3-dots
                        IconButton(
                            onClick = { selectedTrackForOptions = song }
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

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }

        // Options sheet
        selectedTrackForOptions?.let { song ->
            TrackOptionsSheet(
                song = song,
                currentPlaylistId = null,
                repository = repository,
                onDismiss = { selectedTrackForOptions = null },
                onAddToPlaylist = { selectedTrackForPlaylistSave = song }
            )
        }

        // Save in playlist sheet
        selectedTrackForPlaylistSave?.let { song ->
            SaveInPlaylistSheet(
                song = song,
                repository = repository,
                onDismiss = { selectedTrackForPlaylistSave = null }
            )
        }
    }
}
