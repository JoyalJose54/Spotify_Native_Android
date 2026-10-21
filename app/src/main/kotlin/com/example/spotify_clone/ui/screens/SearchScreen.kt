package com.example.spotify_clone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotify_clone.data.models.Song
import com.example.spotify_clone.data.remote.FirebaseRepository
import com.example.spotify_clone.ui.components.TrackListItem
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    allSongs: List<Song>,
    currentPlayingSongId: String?,
    repository: FirebaseRepository,
    onSongClick: (song: Song, queue: List<Song>) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var rawQuery by remember { mutableStateOf("") }

    val recentSearches by repository.streamRecentSearches().collectAsState(initial = emptyList())

    // Instant (no debounce): character-by-character filtering for precise results
    // Ranked: title/artist prefix matches first, then contains matches
    val trimmedQuery = rawQuery.trim().lowercase()
    val searchResults = remember(trimmedQuery, allSongs) {
        if (trimmedQuery.isEmpty()) {
            emptyList()
        } else {
            val tier1 = ArrayList<Song>() // Title starts with query
            val tier2 = ArrayList<Song>() // Word in title starts with query
            val tier3 = ArrayList<Song>() // Artist starts with query
            val tier4 = ArrayList<Song>() // Word in artist starts with query
            val tier5 = ArrayList<Song>() // Title contains query (multi-char only)
            val tier6 = ArrayList<Song>() // Artist/Album contains query (multi-char only)

            val isSingleChar = trimmedQuery.length == 1
            val wordDelimiters = charArrayOf(' ', '-', '_', '(', ')', '[', ']', ',', '.', '\'', '"', '/', '&')

            for (song in allSongs) {
                val titleLow = song.title.lowercase()
                val artistLow = song.artist.lowercase()
                val albumLow = song.album.lowercase()

                if (titleLow.startsWith(trimmedQuery)) {
                    tier1.add(song)
                    continue
                }

                val titleWords = titleLow.split(*wordDelimiters).filter { it.isNotEmpty() }
                if (titleWords.any { it.startsWith(trimmedQuery) }) {
                    tier2.add(song)
                    continue
                }

                if (artistLow.startsWith(trimmedQuery)) {
                    tier3.add(song)
                    continue
                }

                val artistWords = artistLow.split(*wordDelimiters).filter { it.isNotEmpty() }
                if (artistWords.any { it.startsWith(trimmedQuery) }) {
                    tier4.add(song)
                    continue
                }

                if (!isSingleChar) {
                    if (titleLow.contains(trimmedQuery)) {
                        tier5.add(song)
                    } else if (artistLow.contains(trimmedQuery) || albumLow.contains(trimmedQuery)) {
                        tier6.add(song)
                    }
                }
            }

            tier1 + tier2 + tier3 + tier4 + tier5 + tier6
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyColors.black)
    ) {
        // ── Fixed Top Bar: Title + Camera ──────────────────────────────────────
        Spacer(modifier = Modifier.height(36.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Search",
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White
            )

            IconButton(onClick = { /* camera */ }) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // ── Search Bar + Cancel Action ─────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = rawQuery,
                        onValueChange = { rawQuery = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.Black,
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(SpotifyColors.green),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (rawQuery.isEmpty()) {
                                Text(
                                    text = "What do you want to listen to?",
                                    fontFamily = SpotifyMixFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF4A4A4A)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (rawQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { rawQuery = "" },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            if (rawQuery.isNotEmpty()) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Cancel",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    modifier = Modifier.clickable { rawQuery = "" }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Body: Results or Recent Searches ───────────────────────────────────
        if (trimmedQuery.isNotEmpty()) {
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = SpotifyColors.lightGrey,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No results for \"$rawQuery\"",
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please try different keywords.",
                            fontFamily = SpotifyMixFontFamily,
                            color = SpotifyColors.lightGrey,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(searchResults, key = { it.id }) { song ->
                        TrackListItem(
                            song = song,
                            isPlaying = song.id == currentPlayingSongId,
                            onClick = {
                                coroutineScope.launch {
                                    repository.addRecentSearch(song.id)
                                }
                                onSongClick(song, searchResults)
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(110.dp))
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (recentSearches.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent searches",
                                fontFamily = SpotifyMixFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )

                            Text(
                                text = "Clear all",
                                fontFamily = SpotifyMixFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = SpotifyColors.lightGrey,
                                modifier = Modifier.clickable {
                                    coroutineScope.launch {
                                        repository.clearRecentSearches()
                                    }
                                }
                            )
                        }
                    }

                    items(recentSearches, key = { "recent_${it.id}" }) { song ->
                        TrackListItem(
                            song = song,
                            isPlaying = song.id == currentPlayingSongId,
                            onClick = {
                                coroutineScope.launch {
                                    repository.addRecentSearch(song.id)
                                }
                                onSongClick(song, recentSearches)
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            repository.removeRecentSearch(song.id)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = SpotifyColors.lightGrey,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }

                if (recentSearches.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 100.dp, start = 32.dp, end = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Play what you love",
                                    fontFamily = SpotifyMixFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Search for artists, songs, podcasts, and more.",
                                    fontFamily = SpotifyMixFontFamily,
                                    fontSize = 14.sp,
                                    color = SpotifyColors.lightGrey,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(110.dp))
                }
            }
        }
    }
}
