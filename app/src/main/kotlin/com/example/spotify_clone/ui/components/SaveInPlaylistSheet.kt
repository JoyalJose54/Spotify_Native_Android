package com.example.spotify_clone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotify_clone.data.models.Playlist
import com.example.spotify_clone.data.models.Song
import com.example.spotify_clone.data.remote.FirebaseRepository
import com.example.spotify_clone.ui.theme.SpotifyColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveInPlaylistSheet(
    song: Song,
    repository: FirebaseRepository,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val playlists by repository.streamPlaylists().collectAsState(initial = emptyList())
    val eligiblePlaylists = remember(playlists) { playlists.filter { it.id != "all_songs" } }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val selectedPlaylistIds = remember { mutableStateListOf<String>() }
    var hasInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(playlists, song.id) {
        if (!hasInitialized && playlists.isNotEmpty()) {
            selectedPlaylistIds.clear()
            val alreadyIn = playlists.filter { it.id != "all_songs" && it.trackIds.contains(song.id) }.map { it.id }
            selectedPlaylistIds.addAll(alreadyIn)
            hasInitialized = true
        }
    }

    val filteredPlaylists = remember(eligiblePlaylists, searchQuery) {
        if (searchQuery.isBlank()) eligiblePlaylists
        else eligiblePlaylists.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF282828),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
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
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Add to playlist",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // New Playlist & Done Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                // New Playlist Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showCreateDialog = true }
                        .padding(vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF383838)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New playlist",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "New playlist",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Done Button
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val initialIds = playlists
                                .filter { it.id != "all_songs" && it.trackIds.contains(song.id) }
                                .map { it.id }
                                .toSet()
                            val currentSelected = selectedPlaylistIds.toSet()

                            val toAdd = currentSelected.filter { !initialIds.contains(it) }
                            val toRemove = initialIds.filter { !currentSelected.contains(it) }

                            for (plId in toAdd) {
                                repository.addTrackToPlaylist(plId, song.id)
                            }
                            for (plId in toRemove) {
                                repository.removeTrackFromPlaylist(plId, song.id)
                            }
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyColors.green),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Done",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (eligiblePlaylists.size > 4) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Find a playlist", color = SpotifyColors.lightGrey, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SpotifyColors.lightGrey, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = SpotifyColors.lightGrey, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedBorderColor = Color(0xFF3E3E3E),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                items(filteredPlaylists, key = { it.id }) { pl ->
                    val isSelected = selectedPlaylistIds.contains(pl.id)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                if (isSelected) {
                                    selectedPlaylistIds.remove(pl.id)
                                } else {
                                    selectedPlaylistIds.add(pl.id)
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        PlaylistCollage(
                            playlist = pl,
                            size = 48.dp,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = "${pl.trackIds.size} songs",
                                color = SpotifyColors.lightGrey,
                                fontSize = 12.sp
                            )
                        }

                        // Checkbox
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) SpotifyColors.green else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color.Transparent)
                                        .padding(2.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, SpotifyColors.lightGrey),
                                        modifier = Modifier.fillMaxSize()
                                    ) {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Color(0xFF282828),
            title = {
                Text(
                    text = "Give your playlist a name",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SpotifyColors.green,
                        unfocusedBorderColor = Color(0xFF444444)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) {
                            coroutineScope.launch {
                                val plId = repository.createPlaylist(name, listOf(song.id))
                                selectedPlaylistIds.add(plId)
                                showCreateDialog = false
                                newPlaylistName = ""
                            }
                        }
                    }
                ) {
                    Text("Create", color = SpotifyColors.green, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = SpotifyColors.lightGrey)
                }
            }
        )
    }
}
