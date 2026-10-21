package com.example.spotify_clone.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.spotify_clone.data.models.Song
import com.example.spotify_clone.data.remote.FirebaseRepository
import com.example.spotify_clone.ui.theme.SpotifyColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistScreen(
    playlistId: String,
    initialSongs: List<Song>,
    repository: FirebaseRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var songs by remember { mutableStateOf(initialSongs) }
    var isDirty by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = SpotifyColors.black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Edit playlist",
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
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = SpotifyColors.green,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(20.dp)
                        )
                    } else {
                        TextButton(
                            onClick = {
                                if (!isDirty) {
                                    onBack()
                                    return@TextButton
                                }
                                isSaving = true
                                coroutineScope.launch {
                                    try {
                                        repository.reorderPlaylistTracks(playlistId, songs.map { it.id })
                                        Toast.makeText(context, "Playlist updated", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                                        isSaving = false
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = "Save",
                                color = SpotifyColors.green,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpotifyColors.black)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    // Red remove button
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable {
                                coroutineScope.launch {
                                    repository.removeTrackFromPlaylist(playlistId, song.id)
                                }
                                val updated = songs.toMutableList().apply { removeAt(index) }
                                songs = updated
                                isDirty = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(1.8.dp, Color(0xFFFF5252)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Remove",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Cover Art
                    if (song.imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = song.imageUrl,
                            contentDescription = song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SpotifyColors.surface)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SpotifyColors.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = SpotifyColors.lightGrey,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = song.artist,
                            color = SpotifyColors.lightGrey,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    // Move up / down quick buttons for smooth reordering
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (index > 0) {
                            Text(
                                text = "▲",
                                color = SpotifyColors.lightGrey,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .clickable {
                                        val mutable = songs.toMutableList()
                                        val item = mutable.removeAt(index)
                                        mutable.add(index - 1, item)
                                        songs = mutable
                                        isDirty = true
                                    }
                            )
                        }
                        if (index < songs.size - 1) {
                            Text(
                                text = "▼",
                                color = SpotifyColors.lightGrey,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .clickable {
                                        val mutable = songs.toMutableList()
                                        val item = mutable.removeAt(index)
                                        mutable.add(index + 1, item)
                                        songs = mutable
                                        isDirty = true
                                    }
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Reorder",
                            tint = SpotifyColors.lightGrey,
                            modifier = Modifier.padding(start = 4.dp).size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
