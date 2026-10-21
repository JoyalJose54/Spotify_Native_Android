package com.example.spotify_clone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.spotify_clone.data.models.Song
import com.example.spotify_clone.data.remote.FirebaseRepository
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily
import com.example.spotify_clone.utils.DuplicateGroup
import com.example.spotify_clone.utils.SongDuplicateMatcher
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateResolverSheet(
    repository: FirebaseRepository,
    onDismiss: () -> Unit
) {
    val allSongs by repository.streamAllSongs().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var showAutoCleanConfirm by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Multi-select map: song.id -> Boolean (checked for deletion)
    val selectedSongIds = remember { mutableStateMapOf<String, Boolean>() }

    // Group songs by word-level & messy title matching using SongDuplicateMatcher
    val duplicateGroups: List<DuplicateGroup> by remember(allSongs) {
        derivedStateOf {
            SongDuplicateMatcher.findDuplicatesInLibrary(allSongs)
        }
    }

    val totalDuplicateSongs by remember(duplicateGroups) {
        derivedStateOf { duplicateGroups.sumOf { it.songs.size } }
    }

    val totalExtraEntries by remember(duplicateGroups) {
        derivedStateOf { duplicateGroups.sumOf { it.songs.size - 1 } }
    }

    val selectedCount = selectedSongIds.count { it.value }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
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
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
        ) {
            if (duplicateGroups.isEmpty()) {
                // Empty state: Clean database
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
                        text = "No Duplicates Found",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Strict checking found 0 duplicate songs in your library. Every track is unique!",
                        fontFamily = SpotifyMixFontFamily,
                        fontSize = 14.sp,
                        color = SpotifyColors.lightGrey,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyColors.green),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Done",
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                // Duplicates Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Duplicates Checker",
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Found ${duplicateGroups.size} cluster(s) with $totalDuplicateSongs songs ($totalExtraEntries duplicates)",
                            fontFamily = SpotifyMixFontFamily,
                            fontSize = 12.sp,
                            color = SpotifyColors.lightGrey
                        )
                    }

                    if (!isProcessing) {
                        Button(
                            onClick = { showAutoCleanConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyColors.green),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Auto Clean",
                                fontFamily = SpotifyMixFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Selection Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            // Preselect all entries except the first one in each group
                            selectedSongIds.clear()
                            for (group in duplicateGroups) {
                                for (i in 1 until group.songs.size) {
                                    selectedSongIds[group.songs[i].id] = true
                                }
                            }
                        },
                        label = {
                            Text("Select Duplicates (Keep 1st)", fontSize = 11.sp, fontFamily = SpotifyMixFontFamily)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )

                    if (selectedCount > 0) {
                        FilterChip(
                            selected = false,
                            onClick = { selectedSongIds.clear() },
                            label = {
                                Text("Clear Selection", fontSize = 11.sp, fontFamily = SpotifyMixFontFamily)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                labelColor = Color(0xFFFF5252)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = msg,
                        fontFamily = SpotifyMixFontFamily,
                        fontSize = 12.sp,
                        color = if (msg.startsWith("Error")) Color(0xFFFF5252) else SpotifyColors.green
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
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
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(duplicateGroups) { group ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .border(
                                        width = 1.dp,
                                        color = SpotifyColors.green.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                // Group Header with matched words
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(SpotifyColors.green)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Matched: ${group.matchedWords.joinToString(", ") { "\"$it\"" }}",
                                            fontFamily = SpotifyMixFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = SpotifyColors.green,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "${group.songs.size} versions",
                                        fontFamily = SpotifyMixFontFamily,
                                        fontSize = 11.sp,
                                        color = SpotifyColors.lightGrey
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Song entries in this duplicate cluster
                                group.songs.forEachIndexed { idx, songEntry ->
                                    val isKeepDefault = (idx == 0)
                                    val isChecked = selectedSongIds[songEntry.id] == true

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isChecked) Color(0xFFFF5252).copy(alpha = 0.15f)
                                                else if (isKeepDefault) SpotifyColors.green.copy(alpha = 0.08f)
                                                else Color.Black.copy(alpha = 0.35f)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isChecked) Color(0xFFFF5252).copy(alpha = 0.6f)
                                                else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                // Toggle checkbox
                                                val current = selectedSongIds[songEntry.id] ?: false
                                                selectedSongIds[songEntry.id] = !current
                                            }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Checkbox for selection
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                selectedSongIds[songEntry.id] = checked
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Color(0xFFFF5252),
                                                uncheckedColor = Color.White.copy(alpha = 0.5f),
                                                checkmarkColor = Color.White
                                            ),
                                            modifier = Modifier.size(28.dp)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Artwork
                                        if (songEntry.imageUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = songEntry.imageUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                .background(SpotifyColors.surface),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = Color.Gray
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        // Song Details
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = songEntry.title,
                                                    fontFamily = SpotifyMixFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )

                                                Spacer(modifier = Modifier.width(6.dp))

                                                if (isKeepDefault) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(SpotifyColors.green)
                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = "KEEP",
                                                            fontFamily = SpotifyMixFontFamily,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 8.sp,
                                                            color = Color.Black
                                                        )
                                                    }
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Color(0xFF333333))
                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = "DUP",
                                                            fontFamily = SpotifyMixFontFamily,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 8.sp,
                                                            color = Color(0xFFFF5252)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = songEntry.artist.ifEmpty { "Unknown Artist" },
                                                    fontFamily = SpotifyMixFontFamily,
                                                    fontSize = 11.sp,
                                                    color = SpotifyColors.lightGrey,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (songEntry.durationMs > 0) {
                                                    Text(
                                                        text = " • ${formatMs(songEntry.durationMs)}",
                                                        fontFamily = SpotifyMixFontFamily,
                                                        fontSize = 11.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "ID: ${songEntry.id}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                color = Color.White.copy(alpha = 0.3f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        // Individual delete trash button
                                        IconButton(
                                            onClick = { songToDelete = songEntry },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete Track",
                                                tint = Color(0xFFFF5252),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }

                // Bottom Floating Action Bar for Selected Deletions
                if (selectedCount > 0 && !isProcessing) {
                    Surface(
                        color = Color(0xFF1E1E1E),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "$selectedCount selected",
                                    fontFamily = SpotifyMixFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Ready to delete from database",
                                    fontFamily = SpotifyMixFontFamily,
                                    fontSize = 11.sp,
                                    color = Color(0xFFFF8A80)
                                )
                            }

                            Button(
                                onClick = { showDeleteSelectedConfirm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Delete Selected ($selectedCount)",
                                    fontFamily = SpotifyMixFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Auto Clean Confirmation Dialog
    if (showAutoCleanConfirm) {
        AlertDialog(
            onDismissRequest = { showAutoCleanConfirm = false },
            containerColor = SpotifyColors.surface,
            shape = RoundedCornerShape(12.dp),
            title = {
                Text(
                    text = "Auto Clean Duplicates?",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "This will automatically keep the 1st version of each duplicate cluster and permanently delete all other $totalExtraEntries duplicate entries from the Firestore database and all playlists.",
                    fontFamily = SpotifyMixFontFamily,
                    fontSize = 14.sp,
                    color = SpotifyColors.lightGrey
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAutoCleanConfirm = false
                        isProcessing = true
                        coroutineScope.launch {
                            var deletedCount = 0
                            try {
                                for (group in duplicateGroups) {
                                    for (i in 1 until group.songs.size) {
                                        repository.deleteTrack(group.songs[i].id)
                                        deletedCount++
                                    }
                                }
                                selectedSongIds.clear()
                                statusMessage = "Successfully removed $deletedCount duplicate entries"
                            } catch (e: Exception) {
                                statusMessage = "Error cleaning duplicates: ${e.message}"
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyColors.green)
                ) {
                    Text(
                        text = "Clean All ($totalExtraEntries)",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoCleanConfirm = false }) {
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

    // Delete Selected Confirmation Dialog
    if (showDeleteSelectedConfirm) {
        val targets = selectedSongIds.filter { it.value }.keys.toList()
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            containerColor = SpotifyColors.surface,
            shape = RoundedCornerShape(12.dp),
            title = {
                Text(
                    text = "Delete ${targets.size} selected songs?",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "This will permanently delete the ${targets.size} chosen duplicate track(s) from the Firestore database and all playlists. This action cannot be undone.",
                    fontFamily = SpotifyMixFontFamily,
                    fontSize = 14.sp,
                    color = SpotifyColors.lightGrey
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteSelectedConfirm = false
                        isProcessing = true
                        coroutineScope.launch {
                            var deletedCount = 0
                            try {
                                for (id in targets) {
                                    repository.deleteTrack(id)
                                    deletedCount++
                                }
                                selectedSongIds.clear()
                                statusMessage = "Successfully deleted $deletedCount duplicate track(s)"
                            } catch (e: Exception) {
                                statusMessage = "Error deleting tracks: ${e.message}"
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text(
                        text = "Delete ${targets.size} Songs",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) {
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

    // Single Duplicate Delete Confirmation Dialog
    songToDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            containerColor = SpotifyColors.surface,
            shape = RoundedCornerShape(12.dp),
            title = {
                Text(
                    text = "Delete duplicate track?",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "This will permanently delete \"${song.title}\" (${song.artist}) from the database. This action cannot be undone.",
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
                                selectedSongIds.remove(id)
                                statusMessage = "Deleted duplicate entry successfully"
                            } catch (e: Exception) {
                                statusMessage = "Error: ${e.message}"
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
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
