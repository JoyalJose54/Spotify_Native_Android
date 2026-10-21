package com.example.spotify_clone.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.spotify_clone.R
import com.example.spotify_clone.data.models.Playlist
import com.example.spotify_clone.data.models.Song
import com.example.spotify_clone.ui.components.PlaylistCollage
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily
import com.example.spotify_clone.ui.theme.SpotifyMixMonoFontFamily
import java.util.Calendar
import kotlin.math.abs

@Composable
fun HomeScreen(
    allSongs: List<Song>,
    playlists: List<Playlist>,
    currentPlayingSongId: String?,
    userName: String = "",
    recentlyPlayed: List<Song> = emptyList(),
    topSongTitle: String? = null,
    topArtistName: String? = null,
    onPlaylistClick: (Playlist) -> Unit,
    onSongClick: (song: Song, queue: List<Song>) -> Unit,
    onNavigateToTopSongs: () -> Unit = {},
    onNavigateToTopArtists: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeChip by remember { mutableStateOf("Music") }

    val timeGreeting = remember {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            h < 12 -> "Good morning"
            h < 18 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val greeting = remember(timeGreeting, userName) {
        if (userName.isNotBlank()) "$timeGreeting, $userName" else timeGreeting
    }

    // Stable Jump Back In: Uses user's recently played songs, fallback to library songs
    val jumpSongs = remember(recentlyPlayed, allSongs) {
        if (recentlyPlayed.isNotEmpty()) recentlyPlayed.take(8)
        else allSongs.take(8)
    }

    // Made For You: Daily rotated song from each playlist
    val madeForYouEntries = remember(playlists, allSongs) {
        val now = System.currentTimeMillis()
        val timeZone = java.util.TimeZone.getDefault()
        val daysSinceEpoch = ((now + timeZone.getOffset(now)) / (1000L * 60 * 60 * 24)).toInt()

        val trackMap = allSongs.associateBy { it.id }
        playlists.mapNotNull { pl ->
            val tracks = if (pl.tracks.isNotEmpty()) pl.tracks
            else pl.trackIds.mapNotNull { trackMap[it] }

            if (tracks.isEmpty()) null
            else {
                val seed = kotlin.math.abs(pl.id.hashCode())
                val dailyIndex = (daysSinceEpoch + seed) % tracks.size
                val song = tracks[dailyIndex]
                Pair(song, pl)
            }
        }
    }
    val distinctArtists = remember(allSongs) {
        allSongs.map { it.artist }.filter { it.isNotEmpty() && it != "Unknown Artist" }.distinct().take(5)
    }
    val topItems = remember(playlists, allSongs) {
        val playlistTiles = playlists.take(6).map { TopGridItem.FromPlaylist(it) }
        val remaining = (6 - playlistTiles.size).coerceAtLeast(0)
        val songTiles = allSongs.take(remaining).map { TopGridItem.FromSong(it) }
        (playlistTiles + songTiles).take(6)
    }
    // Pre-compute artist→songs map ONCE to avoid per-item recomputation inside LazyRow
    // which caused severe jank and flickering during horizontal scrolling
    val artistSongsMap = remember(allSongs, distinctArtists) {
        val map = HashMap<String, List<Song>>(distinctArtists.size * 2)
        for (artist in distinctArtists) {
            map[artist] = allSongs.filter { it.artist == artist }
        }
        map
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyColors.black)
    ) {
        // ── 1. Top App Bar with Greeting ──────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 36.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = greeting,
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = "Notifications",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // ── 2. Quick-Filter Chips ─────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Music", "Podcasts & Shows", "Audiobooks").forEach { chip ->
                    val isSelected = activeChip == chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) SpotifyColors.green else SpotifyColors.surface)
                            .clickable { activeChip = chip }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = chip,
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isSelected) Color.Black else Color.White
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // ── 3. Top Grid (2 columns x 3 rows) ──────────────────────────────────
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                topItems.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { item ->
                            TopGridTile(
                                item = item,
                                isPlaying = when (item) {
                                    is TopGridItem.FromSong -> item.song.id == currentPlayingSongId
                                    is TopGridItem.FromPlaylist -> false
                                },
                                onClick = {
                                    when (item) {
                                        is TopGridItem.FromPlaylist -> onPlaylistClick(item.playlist)
                                        is TopGridItem.FromSong -> onSongClick(item.song, allSongs)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ── 4. Spotify Wrapped Card ───────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFFB74D), Color(0xFFE91E63))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "2026",
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "#SPOTIFYWRAPPED",
                            fontFamily = SpotifyMixMonoFontFamily,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = SpotifyColors.lightGrey
                        )
                        Text(
                            text = "Your 2026 in review",
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val resolvedTopSong = topSongTitle?.takeIf { it.isNotBlank() }
                        ?: if (allSongs.isNotEmpty()) allSongs.first().title else "Top Tracks"
                    val resolvedTopArtist = topArtistName?.takeIf { it.isNotBlank() }
                        ?: if (allSongs.isNotEmpty()) allSongs.first().artist else "Top Artists"

                    WrappedCard(
                        gradient = listOf(Color(0xFFF06292), Color(0xFF7C4DFF)),
                        label = "Your Top Songs",
                        sub = resolvedTopSong,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToTopSongs() }
                    )
                    WrappedCard(
                        gradient = listOf(Color(0xFF7C4DFF), Color(0xFF00BCD4)),
                        label = "Your Artists",
                        sub = resolvedTopArtist,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToTopArtists() }
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        // ── 5. All Songs Quick Card ───────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpotifyColors.cardBg)
                    .clickable {
                        val allSongsPl = Playlist(
                            id = "all_songs",
                            name = "All Songs",
                            description = "${allSongs.size} tracks in library",
                            tracks = allSongs
                        )
                        onPlaylistClick(allSongsPl)
                    }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val allSongsPl = remember(allSongs) {
                        Playlist(
                            id = "all_songs",
                            name = "All Songs",
                            description = "${allSongs.size} tracks in library",
                            tracks = allSongs
                        )
                    }
                    PlaylistCollage(
                        playlist = allSongsPl,
                        currentTracks = allSongs.take(4),
                        size = 48.dp,
                        cornerRadius = 4.dp
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "All Songs",
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = "${allSongs.size} songs • Tap to view all",
                            fontFamily = SpotifyMixFontFamily,
                            fontSize = 12.sp,
                            color = SpotifyColors.lightGrey
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SpotifyColors.green)
                            .clickable {
                                if (allSongs.isNotEmpty()) {
                                    onSongClick(allSongs.first(), allSongs)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        // ── 6. Jump Back In Carousel ──────────────────────────────────────────
        if (allSongs.isNotEmpty()) {
            item {
                Text(
                    text = "Jump back in",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(jumpSongs, key = { "jump_${it.id}" }, contentType = { "jump_card" }) { song ->
                        SongCarouselCard(
                            song = song,
                            onClick = { onSongClick(song, allSongs) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // ── 7. Made For You (Daily rotated song from each playlist) ───────────
        if (madeForYouEntries.isNotEmpty()) {
            item {
                Text(
                    text = "Made for you",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                val mfyQueue = remember(madeForYouEntries) { madeForYouEntries.map { it.first } }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(madeForYouEntries, key = { "mfy_${it.first.id}_${it.second.id}" }, contentType = { "daily_song" }) { (song, pl) ->
                        val sub = if (song.artist.isNotBlank()) "${song.artist} • ${pl.name}" else pl.name
                        SongCarouselCard(
                            song = song,
                            subtitle = sub,
                            onClick = {
                                onSongClick(song, mfyQueue)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // ── 8. Recommended Stations ───────────────────────────────────────────
        if (distinctArtists.isNotEmpty()) {
            item {
                    Text(
                        text = "Recommended Stations",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(distinctArtists, key = { "station_$it" }, contentType = { "station_card" }) { artist ->
                            // Use pre-computed map - no per-item recomputation during scroll
                            val artistSongs = artistSongsMap[artist] ?: emptyList()
                            RadioStationCard(
                                artistName = artist,
                                songs = artistSongs,
                                onClick = {
                                    if (artistSongs.isNotEmpty()) {
                                        onSongClick(artistSongs.first(), artistSongs)
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

sealed class TopGridItem {
    data class FromPlaylist(val playlist: Playlist) : TopGridItem()
    data class FromSong(val song: Song) : TopGridItem()
}

@Composable
fun TopGridTile(
    item: TopGridItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(SpotifyColors.surface)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (item) {
            is TopGridItem.FromPlaylist -> {
                PlaylistCollage(
                    playlist = item.playlist,
                    currentTracks = item.playlist.tracks.take(4),
                    size = 56.dp,
                    cornerRadius = 0.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.playlist.name,
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
            }
            is TopGridItem.FromSong -> {
                Box(
                    modifier = Modifier.size(56.dp).background(Color(0xFF333333)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.song.imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = item.song.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = SpotifyColors.lightGrey)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.song.title,
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isPlaying) SpotifyColors.green else Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
            }
        }
    }
}

@Composable
fun WrappedCard(
    gradient: List<Color>,
    label: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(130.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(gradient))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sub,
                fontFamily = SpotifyMixFontFamily,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SongCarouselCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyColors.surface),
            contentAlignment = Alignment.Center
        ) {
            if (song.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = song.imageUrl,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SpotifyColors.lightGrey,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = song.title,
            fontFamily = SpotifyMixFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = subtitle ?: if (song.artist.isNotEmpty()) song.artist else "Song",
            fontFamily = SpotifyMixFontFamily,
            fontSize = 11.sp,
            color = SpotifyColors.lightGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PlaylistCarouselCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        PlaylistCollage(
            playlist = playlist,
            size = 140.dp,
            cornerRadius = 8.dp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.name,
            fontFamily = SpotifyMixFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = if (playlist.owner.isNotEmpty()) "By ${playlist.owner}" else "Playlist",
            fontFamily = SpotifyMixFontFamily,
            fontSize = 11.sp,
            color = SpotifyColors.lightGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RadioStationCard(
    artistName: String,
    songs: List<Song>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        Color(0xFFE57373),
        Color(0xFF81C784),
        Color(0xFF64B5F6),
        Color(0xFFFFB74D),
        Color(0xFFBA68C8),
        Color(0xFF4DB6AC),
        Color(0xFFFF8A65)
    )
    val cardColor = remember(artistName) { colors[abs(artistName.hashCode()) % colors.size] }
    val images = remember(songs) { songs.map { it.imageUrl }.filter { it.isNotEmpty() }.take(3) }

    Column(
        modifier = modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(cardColor)
                .padding(10.dp)
        ) {
            // Top Header: Logo + RADIO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_spotify_icon_rgb_green),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "RADIO",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    color = Color.Black.copy(alpha = 0.65f)
                )
            }

            // Middle Overlapping Avatars (Centered nicely)
            Box(
                modifier = Modifier
                    .size(120.dp, 60.dp)
                    .align(Alignment.Center)
            ) {
                when {
                    images.size == 1 -> {
                        AsyncImage(
                            model = images[0],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                        )
                    }
                    images.size == 2 -> {
                        AsyncImage(
                            model = images[0],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.CenterStart)
                                .clip(CircleShape)
                        )
                        AsyncImage(
                            model = images[1],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.CenterEnd)
                                .clip(CircleShape)
                        )
                    }
                    images.size >= 3 -> {
                        AsyncImage(
                            model = images[0],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .align(Alignment.CenterStart)
                                .clip(CircleShape)
                        )
                        AsyncImage(
                            model = images[2],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .align(Alignment.CenterEnd)
                                .clip(CircleShape)
                        )
                        AsyncImage(
                            model = images[1],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(54.dp)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(40.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
            }

            // Bottom Artist Name
            Text(
                text = artistName,
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val subtitle = remember(artistName, songs) {
            if (songs.size > 1) "With $artistName and more" else "Radio • $artistName"
        }
        Text(
            text = subtitle,
            fontFamily = SpotifyMixFontFamily,
            fontSize = 11.sp,
            color = SpotifyColors.lightGrey,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
