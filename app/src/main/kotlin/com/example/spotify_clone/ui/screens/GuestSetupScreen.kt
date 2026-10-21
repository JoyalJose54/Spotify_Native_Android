package com.example.spotify_clone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily

@Composable
fun GuestSetupScreen(
    onComplete: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var termsAccepted by remember { mutableStateOf(false) }
    var privacyAccepted by remember { mutableStateOf(false) }

    val canProceed = termsAccepted && privacyAccepted && name.trim().isNotEmpty()

    Scaffold(
        containerColor = SpotifyColors.black,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "What should we call you?",
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                color = Color.White,
                lineHeight = 36.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Enter the name you'd like to use in the app.",
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                color = SpotifyColors.lightGrey
            )

            Spacer(modifier = Modifier.height(36.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = {
                    Text(
                        text = "Your Name",
                        fontFamily = SpotifyMixFontFamily,
                        color = SpotifyColors.lightGrey
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SpotifyColors.surface,
                    unfocusedContainerColor = SpotifyColors.surface,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = SpotifyColors.green,
                    focusedBorderColor = SpotifyColors.green,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Checkbox 1: Terms and Conditions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { termsAccepted = !termsAccepted }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = termsAccepted,
                    onCheckedChange = { termsAccepted = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = SpotifyColors.green,
                        uncheckedColor = SpotifyColors.lightGrey,
                        checkmarkColor = Color.Black
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I accept the Terms and Conditions",
                    fontFamily = SpotifyMixFontFamily,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            // Checkbox 2: Privacy Policy
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { privacyAccepted = !privacyAccepted }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = privacyAccepted,
                    onCheckedChange = { privacyAccepted = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = SpotifyColors.green,
                        uncheckedColor = SpotifyColors.lightGrey,
                        checkmarkColor = Color.Black
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I agree to the Privacy Policy",
                    fontFamily = SpotifyMixFontFamily,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // "Next" / "Let's Go" Pill Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (canProceed) SpotifyColors.green else SpotifyColors.surface)
                    .clickable(enabled = canProceed) {
                        onComplete(name.trim())
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Next",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (canProceed) Color.Black else SpotifyColors.lightGrey
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
