package com.example.spotify_clone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.spotify_clone.data.models.Playlist
import com.example.spotify_clone.data.models.Song
import com.example.spotify_clone.ui.components.PlaylistCollage
import com.example.spotify_clone.ui.components.CreateOptionsSheet
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily

@Composable
fun LibraryScreen(
    playlists: List<Playlist>,
    totalSongCount: Int,
    allSongs: List<Song>,
    onPlaylistClick: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onReorderPlaylists: (List<String>) -> Unit = {},
    onOpenSmartIngestion: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeFilter by remember { mutableStateOf("Playlists") }
    var isGridView by rememberSaveable { mutableStateOf(false) }
    var isCreateDialogOpen by remember { mutableStateOf(false) }
    var showCreateOptionsSheet by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    var localPlaylists by remember(playlists) { mutableStateOf(playlists) }
    var draggingPlaylistId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 76.dp.toPx() }

    val filters = listOf("Playlists", "Artists", "Albums")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyColors.black)
    ) {
        // Top App Bar
        Spacer(modifier = Modifier.height(36.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SpotifyColors.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = SpotifyColors.lightGrey,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Your Library",
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = { showCreateOptionsSheet = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Playlist",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Filter Chips
        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { filter ->
                val isSelected = activeFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) SpotifyColors.green else SpotifyColors.surface)
                        .clickable { activeFilter = if (isSelected) "All" else filter }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = filter,
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isSelected) Color.Black else Color.White
                    )
                }
            }
        }

        // Sort Row (Recently added & Grid icon)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Recently added",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }

            IconButton(
                onClick = { isGridView = !isGridView },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                    contentDescription = if (isGridView) "List View" else "Grid View",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pinned Item: All Songs
                item {
                    val allSongsPl = Playlist(
                        id = "all_songs",
                        name = "All Songs",
                        description = "$totalSongCount songs in your library",
                        tracks = allSongs
                    )
                    PlaylistGridCard(
                        playlist = allSongsPl,
                        currentTracks = allSongs.take(4),
                        onClick = { onPlaylistClick(allSongsPl) }
                    )
                }

                items(localPlaylists, key = { "grid_${it.id}" }) { pl ->
                    PlaylistGridCard(
                        playlist = pl,
                        onClick = { onPlaylistClick(pl) }
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Pinned Item: All Songs / Liked Songs
                item {
                    val allSongsPl = Playlist(
                        id = "all_songs",
                        name = "All Songs",
                        description = "$totalSongCount songs in your library",
                        tracks = allSongs
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPlaylistClick(allSongsPl)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlaylistCollage(
                            playlist = allSongsPl,
                            currentTracks = allSongs.take(4),
                            size = 64.dp,
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
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Pinned • Playlist • $totalSongCount songs",
                                fontFamily = SpotifyMixFontFamily,
                                fontSize = 13.sp,
                                color = SpotifyColors.lightGrey
                            )
                        }
                    }
                }

                // Playlist Items - Reorderable via Press & Hold
                items(localPlaylists, key = { it.id }) { pl ->
                    val isDragging = pl.id == draggingPlaylistId
                    val zIndex = if (isDragging) 10f else 1f
                    val rowBg = if (isDragging) Color(0xFF2E2E2E) else Color.Transparent

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(zIndex)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetY else 0f
                                if (isDragging) {
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                    shadowElevation = 10f
                                }
                            }
                            .background(rowBg, RoundedCornerShape(8.dp))
                            .pointerInput(pl.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingPlaylistId = pl.id
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                        val currentIdx = localPlaylists.indexOfFirst { it.id == draggingPlaylistId }
                                        if (currentIdx != -1) {
                                            if (dragOffsetY > itemHeightPx * 0.7f && currentIdx < localPlaylists.size - 1) {
                                                val nextIdx = currentIdx + 1
                                                val list = localPlaylists.toMutableList()
                                                val item = list.removeAt(currentIdx)
                                                list.add(nextIdx, item)
                                                localPlaylists = list
                                                dragOffsetY -= itemHeightPx
                                            } else if (dragOffsetY < -itemHeightPx * 0.7f && currentIdx > 0) {
                                                val prevIdx = currentIdx - 1
                                                val list = localPlaylists.toMutableList()
                                                val item = list.removeAt(currentIdx)
                                                list.add(prevIdx, item)
                                                localPlaylists = list
                                                dragOffsetY += itemHeightPx
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        val finalOrder = localPlaylists.map { it.id }
                                        draggingPlaylistId = null
                                        dragOffsetY = 0f
                                        onReorderPlaylists(finalOrder)
                                    },
                                    onDragCancel = {
                                        draggingPlaylistId = null
                                        dragOffsetY = 0f
                                    }
                                )
                            }
                            .clickable {
                                if (draggingPlaylistId == null) {
                                    onPlaylistClick(pl)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlaylistCollage(
                                playlist = pl,
                                size = 64.dp,
                                cornerRadius = 4.dp
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pl.name,
                                    fontFamily = SpotifyMixFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val curator = if (pl.owner.isNotEmpty()) pl.owner else "User"
                                Text(
                                    text = "Playlist • $curator • ${pl.trackCount} songs",
                                    fontFamily = SpotifyMixFontFamily,
                                    fontSize = 13.sp,
                                    color = SpotifyColors.lightGrey,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }

    // Create Playlist Dialog
    if (isCreateDialogOpen) {
        AlertDialog(
            onDismissRequest = { isCreateDialogOpen = false },
            containerColor = SpotifyColors.surface,
            title = {
                Text(
                    text = "Name your playlist",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("My Playlist #1", color = SpotifyColors.lightGrey) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.trim().isNotEmpty()) {
                            onCreatePlaylist(newPlaylistName.trim())
                            newPlaylistName = ""
                            isCreateDialogOpen = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyColors.green)
                ) {
                    Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isCreateDialogOpen = false }) {
                    Text("Cancel", color = SpotifyColors.lightGrey, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showCreateOptionsSheet) {
        CreateOptionsSheet(
            onDismiss = { showCreateOptionsSheet = false },
            onCreatePlaylist = { isCreateDialogOpen = true },
            onOpenSmartIngestion = {
                showCreateOptionsSheet = false
                onOpenSmartIngestion()
            }
        )
    }
}

@Composable
fun PlaylistGridCard(
    playlist: Playlist,
    currentTracks: List<Song> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyColors.surface)
        ) {
            PlaylistCollage(
                playlist = playlist,
                currentTracks = currentTracks,
                size = androidx.compose.ui.unit.Dp.Unspecified,
                cornerRadius = 8.dp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.name,
            fontFamily = SpotifyMixFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        val sub = if (playlist.id == "all_songs") "Pinned • Playlist"
        else "Playlist • ${playlist.trackCount} songs"

        Text(
            text = sub,
            fontFamily = SpotifyMixFontFamily,
            fontSize = 12.sp,
            color = SpotifyColors.lightGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
