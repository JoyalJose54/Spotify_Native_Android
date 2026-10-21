package com.example.spotify_clone.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.spotify_clone.data.models.Playlist
import com.example.spotify_clone.data.remote.FirebaseRepository
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiCopyMoveSheet(
    isMove: Boolean,
    sourcePlaylist: Playlist,
    selectedSongIds: List<String>,
    repository: FirebaseRepository,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allPlaylists by repository.streamPlaylists().collectAsState(initial = emptyList())
    var isCreatingPlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    val candidatePlaylists = remember(allPlaylists, sourcePlaylist, isMove) {
        allPlaylists.filter { pl ->
            pl.id != "all_songs" && (!isMove || pl.id != sourcePlaylist.id)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF282828),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF555555))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp)
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isMove) "Move selected songs" else "Copy selected songs",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${selectedSongIds.size} songs selected",
                    fontFamily = SpotifyMixFontFamily,
                    fontSize = 13.sp,
                    color = SpotifyColors.lightGrey
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFF383838), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SpotifyColors.green)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    // 1. Create Playlist row
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCreatingPlaylist = true }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Create playlist",
                                    tint = SpotifyColors.green,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = "Create playlist",
                                fontFamily = SpotifyMixFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }

                    // 2. Existing Playlists list
                    items(candidatePlaylists, key = { it.id }) { pl ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isProcessing = true
                                    coroutineScope.launch {
                                        try {
                                            val existing = pl.trackIds
                                            val toAdd = selectedSongIds.filter { !existing.contains(it) }
                                            val duplicates = selectedSongIds.size - toAdd.size

                                            if (toAdd.isEmpty()) {
                                                Toast.makeText(
                                                    context,
                                                    "Skipped: Song(s) already exist in ${pl.name}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                onComplete()
                                                return@launch
                                            }

                                            repository.addTracksToPlaylist(pl.id, toAdd)
                                            if (isMove) {
                                                repository.removeTracksFromPlaylist(sourcePlaylist.id, toAdd)
                                            }

                                            val actionStr = if (isMove) "moved" else "copied"
                                            val dupStr = if (duplicates > 0) " ($duplicates duplicate(s) skipped)" else ""
                                            Toast.makeText(
                                                context,
                                                "Successfully $actionStr ${toAdd.size} song(s) to ${pl.name}$dupStr",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onComplete()
                                        } catch (e: Exception) {
                                            isProcessing = false
                                            Toast.makeText(
                                                context,
                                                "Failed to ${if (isMove) "move" else "copy"}: ${e.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            if (pl.imageUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = pl.imageUrl,
                                    contentDescription = pl.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SpotifyColors.surface)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SpotifyColors.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = SpotifyColors.lightGrey,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pl.name,
                                    fontFamily = SpotifyMixFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${pl.trackCount} songs",
                                    fontFamily = SpotifyMixFontFamily,
                                    fontSize = 13.sp,
                                    color = SpotifyColors.lightGrey
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCreatingPlaylist) {
        AlertDialog(
            onDismissRequest = { isCreatingPlaylist = false },
            containerColor = SpotifyColors.surface,
            shape = RoundedCornerShape(12.dp),
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
                        val name = newPlaylistName.trim().ifEmpty { "My Playlist" }
                        isCreatingPlaylist = false
                        isProcessing = true
                        coroutineScope.launch {
                            try {
                                repository.createPlaylist(name = name, trackIds = selectedSongIds)
                                if (isMove) {
                                    repository.removeTracksFromPlaylist(sourcePlaylist.id, selectedSongIds)
                                }
                                val actionStr = if (isMove) "Moved" else "Copied"
                                Toast.makeText(
                                    context,
                                    "$actionStr ${selectedSongIds.size} songs to new playlist \"$name\"",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onComplete()
                            } catch (e: Exception) {
                                isProcessing = false
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyColors.green)
                ) {
                    Text(
                        text = "Create",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { isCreatingPlaylist = false }) {
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
}
