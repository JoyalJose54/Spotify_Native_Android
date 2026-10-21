package com.example.spotify_clone.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Album
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
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsSheet(
    song: Song,
    currentPlaylistId: String? = null,
    repository: FirebaseRepository,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onNavigateToAlbum: ((String, String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val likedSongIds by repository.streamLikedSongIds().collectAsState(initial = emptySet())
    val isLiked = likedSongIds.contains(song.id)
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
            // Track Info Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                AsyncImage(
                    model = song.imageUrl,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SpotifyColors.surface)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = song.artist,
                        color = SpotifyColors.lightGrey,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF383838), thickness = 1.dp)

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Like / Liked
            TrackOptionRow(
                icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                iconTint = if (isLiked) SpotifyColors.green else Color.White,
                title = if (isLiked) "Remove from Liked Songs" else "Save to your Liked Songs",
                onClick = {
                    coroutineScope.launch {
                        repository.toggleLikeSong(song.id, isLiked)
                    }
                }
            )

            // 2. Add to Playlist
            TrackOptionRow(
                icon = Icons.Default.PlaylistAdd,
                iconTint = Color.White,
                title = "Add to playlist",
                onClick = {
                    onDismiss()
                    onAddToPlaylist()
                }
            )

            // 3. Go to album (if available)
            if (song.album.isNotEmpty()) {
                TrackOptionRow(
                    icon = Icons.Outlined.Album,
                    iconTint = Color.White,
                    title = "View album \"${song.album}\"",
                    onClick = {
                        onDismiss()
                        onNavigateToAlbum?.invoke(song.albumId.ifEmpty { song.album }, song.album, song.imageUrl)
                    }
                )
            }

            // 4. Delete song for All Songs master library
            if (currentPlaylistId == "all_songs") {
                TrackOptionRow(
                    icon = Icons.Default.DeleteForever,
                    iconTint = Color(0xFFFF5252),
                    title = "Delete song",
                    onClick = {
                        showDeleteConfirm = true
                    }
                )
            }

            // 5. Share
            TrackOptionRow(
                icon = Icons.Default.Share,
                iconTint = Color.White,
                title = "Share",
                onClick = {
                    onDismiss()
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Listen to ${song.title} by ${song.artist} on Spotify!")
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                }
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = SpotifyColors.surface,
            shape = RoundedCornerShape(12.dp),
            title = {
                Text(
                    text = "Delete Song?",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "This will permanently delete \"${song.title}\" from the cloud database, all playlists, and Cloudinary storage. This cannot be undone.",
                    fontFamily = SpotifyMixFontFamily,
                    fontSize = 14.sp,
                    color = SpotifyColors.lightGrey
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        coroutineScope.launch {
                            repository.deleteTrack(song.id)
                            onDismiss()
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
                TextButton(onClick = { showDeleteConfirm = false }) {
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

@Composable
private fun TrackOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = if (iconTint == Color(0xFFFF5252)) Color(0xFFFF5252) else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
