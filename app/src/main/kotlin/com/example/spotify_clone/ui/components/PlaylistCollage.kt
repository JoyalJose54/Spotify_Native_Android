package com.example.spotify_clone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import coil3.compose.AsyncImage
import com.example.spotify_clone.data.models.Playlist
import com.example.spotify_clone.data.models.Song
import com.example.spotify_clone.ui.theme.SpotifyColors

@Composable
fun PlaylistCollage(
    playlist: Playlist,
    currentTracks: List<Song> = emptyList(),
    size: Dp = 140.dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 4.dp
) {
    val tracks = if (currentTracks.isNotEmpty()) currentTracks else playlist.tracks
    val selectedUrls = androidx.compose.runtime.remember(playlist.imageUrl, tracks) {
        if (playlist.imageUrl.isNotEmpty()) emptyList()
        else {
            val list = ArrayList<String>(4)
            for (track in tracks) {
                if (track.imageUrl.isNotEmpty()) {
                    list.add(track.imageUrl)
                    if (list.size == 4) break
                }
            }
            list
        }
    }

    val finalGridUrls = androidx.compose.runtime.remember(selectedUrls) {
        if (selectedUrls.isNotEmpty()) {
            List(4) { i -> selectedUrls[i % selectedUrls.size] }
        } else {
            emptyList()
        }
    }

    val boxModifier = if (size.isSpecified) {
        modifier.size(size)
    } else {
        modifier.fillMaxWidth().aspectRatio(1f)
    }

    Box(
        modifier = boxModifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(SpotifyColors.surface),
        contentAlignment = Alignment.Center
    ) {
        if (playlist.imageUrl.isNotEmpty()) {
            AsyncImage(
                model = playlist.imageUrl,
                contentDescription = playlist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (selectedUrls.size == 1) {
            AsyncImage(
                model = selectedUrls[0],
                contentDescription = playlist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (finalGridUrls.size == 4) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AsyncImage(
                        model = finalGridUrls[0],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    AsyncImage(
                        model = finalGridUrls[1],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AsyncImage(
                        model = finalGridUrls[2],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    AsyncImage(
                        model = finalGridUrls[3],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = SpotifyColors.lightGrey,
                modifier = Modifier.size(size * 0.4f)
            )
        }
    }
}
