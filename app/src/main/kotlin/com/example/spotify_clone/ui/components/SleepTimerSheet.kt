package com.example.spotify_clone.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotify_clone.playback.SleepTimerOption
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily

/**
 * Spotify Sleep Timer Bottom Sheet.
 * Matches Spotify's native Sleep Timer modal:
 * - Options: 5 min, 10 min, 15 min, 30 min, 45 min, 1 hour, End of track
 * - Highlights active selection with Spotify green & checkmark icon
 * - Shows "Turn off timer" when an active timer is running
 * - Displays authentic toast confirmation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    activeOption: SleepTimerOption?,
    onSelectOption: (SleepTimerOption) -> Unit,
    onTurnOffTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF222222),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 14.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF555555))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header Title
            Text(
                text = "Sleep timer",
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                thickness = 0.8.dp
            )

            // Timer Selection Rows
            SleepTimerOption.values().forEach { option ->
                val isSelected = (activeOption == option)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectOption(option)
                            Toast.makeText(context, option.toastMessage, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option.label,
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 15.sp,
                        color = if (isSelected) SpotifyColors.green else Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Active",
                            tint = SpotifyColors.green,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // "Turn off timer" Option (displayed when a timer is currently running)
            if (activeOption != null) {
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onTurnOffTimer()
                            Toast.makeText(context, "Sleep timer turned off", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Turn off timer",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = SpotifyColors.lightGrey,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
