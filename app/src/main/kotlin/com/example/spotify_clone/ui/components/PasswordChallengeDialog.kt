package com.example.spotify_clone.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily

@Composable
fun PasswordChallengeDialog(
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    fun submit() {
        // Default unlock password (can be customized or linked to user auth)
        if (password.trim() == "spotify" || password.trim() == "admin") {
            onSuccess()
        } else {
            hasError = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpotifyColors.surface,
        shape = RoundedCornerShape(14.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = SpotifyColors.green,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Protected Library",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Enter master password to access the complete music library:",
                    fontFamily = SpotifyMixFontFamily,
                    fontSize = 13.sp,
                    color = SpotifyColors.lightGrey
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        hasError = false
                    },
                    placeholder = {
                        Text(
                            text = "Enter Password",
                            fontFamily = SpotifyMixFontFamily,
                            color = SpotifyColors.lightGrey
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    singleLine = true,
                    isError = hasError,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = SpotifyColors.green,
                        focusedBorderColor = SpotifyColors.green,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        errorBorderColor = Color(0xFFE22134)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasError) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Access Denied • Incorrect Password",
                        fontFamily = SpotifyMixFontFamily,
                        fontSize = 12.sp,
                        color = Color(0xFFE22134),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) {
                Text(
                    text = "UNLOCK",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyColors.green
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "CANCEL",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyColors.lightGrey
                )
            }
        }
    )
}
