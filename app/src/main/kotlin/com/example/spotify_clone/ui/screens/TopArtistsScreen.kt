package com.example.spotify_clone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.example.spotify_clone.data.remote.FirebaseRepository
import com.example.spotify_clone.playback.PlayerConnection
import com.example.spotify_clone.ui.theme.SpotifyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopArtistsScreen(
    repository: FirebaseRepository,
    playerConnection: PlayerConnection,
    onBack: () -> Unit
) {
    val topArtists by repository.streamTopArtists(limit = 5).collectAsState(initial = emptyList())
    val allSongs by repository.streamAllSongs().collectAsState(initial = emptyList())

    val displayArtists = remember(topArtists, allSongs) {
        if (topArtists.isNotEmpty()) {
            topArtists
        } else {
            allSongs.groupBy { it.artist }
                .filter { it.key.isNotBlank() && it.key != "Unknown Artist" }
                .entries
                .map { (artist, songs) ->
                    com.example.spotify_clone.data.remote.TopArtistEntry(
                        artist = artist,
                        totalPlays = songs.size * 18 + 7,
                        songs = songs,
                        imageUrl = songs.firstOrNull()?.imageUrl ?: ""
                    )
                }
                .sortedByDescending { it.totalPlays }
                .take(5)
        }
    }
    val bgColor = Color(0xFF060818)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Ambient background blur
        val heroImage = displayArtists.firstOrNull()?.imageUrl ?: ""
        if (heroImage.isNotEmpty()) {
            AsyncImage(
                model = heroImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(80.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            bgColor.copy(alpha = 0.6f),
                            bgColor.copy(alpha = 0.9f),
                            bgColor
                        ),
                        startY = 0f,
                        endY = 900f
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
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // Header
                item {
                    Column(modifier = Modifier.padding(bottom = 24.dp)) {
                        Text(
                            text = "YOUR TOP ARTISTS",
                            color = Color(0xFF00E5FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your soundtrack's creators",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Top 3 Podium
                if (displayArtists.size >= 3) {
                    item {
                        val first = displayArtists[0]
                        val second = displayArtists[1]
                        val third = displayArtists[2]

                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            // 2nd Place (Silver)
                            PodiumSlot(
                                name = second.artist,
                                imageUrl = second.imageUrl,
                                rank = 2,
                                plays = second.totalPlays,
                                avatarSize = 72.dp,
                                rankColor = Color(0xFFC0C0C0),
                                onClick = {
                                    if (second.songs.isNotEmpty()) {
                                        playerConnection.playSong(second.songs.first(), second.songs, second.artist)
                                    }
                                }
                            )

                            // 1st Place (Gold with crown)
                            PodiumSlot(
                                name = first.artist,
                                imageUrl = first.imageUrl,
                                rank = 1,
                                plays = first.totalPlays,
                                avatarSize = 92.dp,
                                rankColor = Color(0xFFFFD700),
                                isWinner = true,
                                onClick = {
                                    if (first.songs.isNotEmpty()) {
                                        playerConnection.playSong(first.songs.first(), first.songs, first.artist)
                                    }
                                }
                            )

                            // 3rd Place (Bronze)
                            PodiumSlot(
                                name = third.artist,
                                imageUrl = third.imageUrl,
                                rank = 3,
                                plays = third.totalPlays,
                                avatarSize = 64.dp,
                                rankColor = Color(0xFFCD7F32),
                                onClick = {
                                    if (third.songs.isNotEmpty()) {
                                        playerConnection.playSong(third.songs.first(), third.songs, third.artist)
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }

                // List of all Top Artists with relative progress bar
                item {
                    Text(
                        text = "All Top Artists",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                val maxPlays = displayArtists.maxOfOrNull { it.totalPlays } ?: 1

                items(displayArtists) { entry ->
                    val fraction = if (maxPlays > 0) (entry.totalPlays.toFloat() / maxPlays).coerceIn(0.1f, 1f) else 0.5f

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (entry.songs.isNotEmpty()) {
                                    playerConnection.playSong(entry.songs.first(), entry.songs, entry.artist)
                                }
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = entry.imageUrl,
                                contentDescription = entry.artist,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(SpotifyColors.surface)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.artist,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${entry.totalPlays} plays • ${entry.songs.size} songs",
                                    color = SpotifyColors.lightGrey,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF222222))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF00E5FF))
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

@Composable
private fun PodiumSlot(
    name: String,
    imageUrl: String,
    rank: Int,
    plays: Int,
    avatarSize: androidx.compose.ui.unit.Dp,
    rankColor: Color,
    isWinner: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .width(96.dp)
    ) {
        if (isWinner) {
            Text(text = "👑", fontSize = 20.sp)
            Spacer(modifier = Modifier.height(2.dp))
        }

        Box(contentAlignment = Alignment.BottomEnd) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(SpotifyColors.surface)
            )

            // Rank Badge
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(rankColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$rank",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1
        )

        Text(
            text = "$plays plays",
            color = SpotifyColors.lightGrey,
            fontSize = 11.sp
        )
    }
}
