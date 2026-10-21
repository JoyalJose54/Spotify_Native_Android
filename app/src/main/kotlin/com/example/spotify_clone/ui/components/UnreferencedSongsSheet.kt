package com.example.spotify_clone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.spotify_clone.data.models.Playlist
import com.example.spotify_clone.data.models.Song
import com.example.spotify_clone.data.remote.FirebaseRepository
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnreferencedSongsSheet(
    repository: FirebaseRepository,
    currentPlayingSongId: String?,
    onSongClick: (song: Song, queue: List<Song>) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val allSongs by repository.streamAllSongs().collectAsState(initial = emptyList())
    val playlists by repository.streamRawPlaylists().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var songToSaveInPlaylist by remember { mutableStateOf<Song?>(null) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    var showAddAllDialog by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var newPlaylistNameForAddAll by remember { mutableStateOf("") }
    var isCreatingNewPlaylist by remember { mutableStateOf(false) }

    // Identify songs not in any user playlist (ignoring "all_songs" virtual master)
    val unreferencedSongs by remember(allSongs, playlists) {
        derivedStateOf {
            val referencedIds = playlists
                .filter { it.id != "all_songs" }
                .flatMap { it.trackIds }
                .toSet()
            allSongs.filter { it.id !in referencedIds }
        }
    }

    val filteredSongs by remember(unreferencedSongs, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) unreferencedSongs
            else {
                val q = searchQuery.trim().lowercase()
                unreferencedSongs.filter {
                    it.title.lowercase().contains(q) ||
                            it.artist.lowercase().contains(q) ||
                            it.album.lowercase().contains(q)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Gray.copy(alpha = 0.6f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SpotifyColors.green.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = null,
                        tint = SpotifyColors.green,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Unreferenced Tracks",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Found ${unreferencedSongs.size} songs not in any playlist",
                        fontFamily = SpotifyMixFontFamily,
                        fontSize = 13.sp,
                        color = SpotifyColors.lightGrey
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (unreferencedSongs.isEmpty()) {
                // Empty state: All songs referenced
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        tint = SpotifyColors.green,
                        modifier = Modifier.size(68.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "All Tracks Are Referenced",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Every song in your database belongs to at least one playlist.",
                        fontFamily = SpotifyMixFontFamily,
                        fontSize = 14.sp,
                        color = SpotifyColors.lightGrey,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                // Controls Row: Search & Batch Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Search Bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = SpotifyColors.lightGrey,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontFamily = SpotifyMixFontFamily
                                ),
                                cursorBrush = SolidColor(SpotifyColors.green),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search tracks...",
                                            color = SpotifyColors.lightGrey,
                                            fontSize = 13.sp,
                                            fontFamily = SpotifyMixFontFamily
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = SpotifyColors.lightGrey,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Batch Add All to Playlist Button
                    Button(
                        onClick = { showAddAllDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyColors.green),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add All",
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontSize = 13.sp
                        )
                    }
                }

                statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        fontFamily = SpotifyMixFontFamily,
                        fontSize = 12.sp,
                        color = SpotifyColors.green
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(8.dp))

                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SpotifyColors.green)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredSongs, key = { it.id }) { song ->
                            UnreferencedSongItem(
                                song = song,
                                isPlaying = song.id == currentPlayingSongId,
                                onClick = { onSongClick(song, filteredSongs) },
                                onAddToPlaylist = { songToSaveInPlaylist = song },
                                onDelete = { songToDelete = song }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(30.dp))
                        }
                    }
                }
            }
        }
    }

    // Single Song Save in Playlist Sheet
    songToSaveInPlaylist?.let { song ->
        SaveInPlaylistSheet(
            song = song,
            repository = repository,
            onDismiss = { songToSaveInPlaylist = null }
        )
    }

    // Single Song Delete from Database Confirmation Dialog
    songToDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            containerColor = SpotifyColors.surface,
            shape = RoundedCornerShape(12.dp),
            title = {
                Text(
                    text = "Delete From Database?",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "This will permanently delete \"${song.title}\" by ${song.artist} from your database and audio storage. This action cannot be undone.",
                    fontFamily = SpotifyMixFontFamily,
                    fontSize = 14.sp,
                    color = SpotifyColors.lightGrey
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = song.id
                        songToDelete = null
                        isProcessing = true
                        coroutineScope.launch {
                            try {
                                repository.deleteTrack(id)
                                statusMessage = "Deleted track from database successfully"
                            } catch (e: Exception) {
                                statusMessage = "Error deleting track: ${e.message}"
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text(
                        text = "Delete",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) {
                    Text(
                        text = "Cancel",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyColors.lightGrey
                    )
                }
            }
        )
    }

    // Add All to Playlist Dialog
    if (showAddAllDialog) {
        val userPlaylists = playlists.filter { it.id != "all_songs" }
        AlertDialog(
            onDismissRequest = {
                showAddAllDialog = false
                isCreatingNewPlaylist = false
            },
            containerColor = SpotifyColors.surface,
            shape = RoundedCornerShape(12.dp),
            title = {
                Text(
                    text = "Add All Unreferenced Tracks",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Add all ${unreferencedSongs.size} unreferenced songs to a playlist:",
                        fontFamily = SpotifyMixFontFamily,
                        fontSize = 14.sp,
                        color = SpotifyColors.lightGrey
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (!isCreatingNewPlaylist) {
                        // Create New Playlist Trigger
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SpotifyColors.green.copy(alpha = 0.12f))
                                .clickable { isCreatingNewPlaylist = true }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = SpotifyColors.green,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Create new playlist",
                                fontFamily = SpotifyMixFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SpotifyColors.green
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Existing Playlists List
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(userPlaylists) { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .clickable {
                                            showAddAllDialog = false
                                            isProcessing = true
                                            coroutineScope.launch {
                                                try {
                                                    val ids = unreferencedSongs.map { it.id }
                                                    repository.addTracksToPlaylist(pl.id, ids)
                                                    statusMessage = "Added ${ids.size} tracks to \"${pl.name}\""
                                                } catch (e: Exception) {
                                                    statusMessage = "Error: ${e.message}"
                                                } finally {
                                                    isProcessing = false
                                                }
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                        contentDescription = null,
                                        tint = SpotifyColors.lightGrey,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = pl.name,
                                        fontFamily = SpotifyMixFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        // Create New Playlist Form
                        OutlinedTextField(
                            value = newPlaylistNameForAddAll,
                            onValueChange = { newPlaylistNameForAddAll = it },
                            placeholder = { Text("Playlist name", color = SpotifyColors.lightGrey) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SpotifyColors.green,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { isCreatingNewPlaylist = false }) {
                                Text("Back", color = SpotifyColors.lightGrey)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val name = newPlaylistNameForAddAll.trim().ifEmpty { "Unreferenced Tracks" }
                                    showAddAllDialog = false
                                    isCreatingNewPlaylist = false
                                    isProcessing = true
                                    coroutineScope.launch {
                                        try {
                                            val ids = unreferencedSongs.map { it.id }
                                            repository.createPlaylist(name, ids)
                                            statusMessage = "Created \"$name\" with ${ids.size} tracks"
                                            newPlaylistNameForAddAll = ""
                                        } catch (e: Exception) {
                                            statusMessage = "Error: ${e.message}"
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyColors.green)
                            ) {
                                Text("Create & Add", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                if (!isCreatingNewPlaylist) {
                    TextButton(onClick = { showAddAllDialog = false }) {
                        Text(
                            text = "Cancel",
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyColors.lightGrey
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun UnreferencedSongItem(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        if (song.imageUrl.isNotEmpty()) {
            AsyncImage(
                model = song.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF282828)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title & Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (isPlaying) SpotifyColors.green else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist}${if (song.album.isNotEmpty()) " • ${song.album}" else ""}",
                fontFamily = SpotifyMixFontFamily,
                fontSize = 12.sp,
                color = SpotifyColors.lightGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Add to Playlist Button
        IconButton(
            onClick = onAddToPlaylist,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = "Add to playlist",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Delete from Database Button
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete track from database",
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
