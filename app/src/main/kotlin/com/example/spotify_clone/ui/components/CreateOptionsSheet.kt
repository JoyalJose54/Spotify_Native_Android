package com.example.spotify_clone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotify_clone.ui.theme.SpotifyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOptionsSheet(
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenSmartIngestion: () -> Unit
) {
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
                .padding(vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            CreateOptionItem(
                icon = Icons.Default.MusicNote,
                title = "Playlist",
                subtitle = "Build a playlist with songs",
                onClick = {
                    onDismiss()
                    onCreatePlaylist()
                }
            )

            HorizontalDivider(
                color = Color(0xFF3E3E3E),
                thickness = 1.dp,
                modifier = Modifier.padding(start = 76.dp, end = 16.dp)
            )

            CreateOptionItem(
                icon = Icons.Default.PeopleOutline,
                title = "Blend",
                subtitle = "Build a playlist with a friend",
                onClick = {
                    onDismiss()
                }
            )

            HorizontalDivider(
                color = Color(0xFF3E3E3E),
                thickness = 1.dp,
                modifier = Modifier.padding(start = 76.dp, end = 16.dp)
            )

            CreateOptionItem(
                icon = Icons.Default.CloudDownload,
                title = "Smart Ingestion",
                subtitle = "Add songs via search, YouTube, JioSaavn or CSV",
                onClick = {
                    onDismiss()
                    onOpenSmartIngestion()
                }
            )
        }
    }
}

@Composable
private fun CreateOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF3E3E3E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = SpotifyColors.lightGrey,
                fontSize = 13.sp
            )
        }
    }
}
