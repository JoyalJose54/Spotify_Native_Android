package com.example.spotify_clone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotify_clone.data.remote.FirebaseRepository
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily
import kotlinx.coroutines.launch

@Composable
fun AuthFlowScreen(
    repository: FirebaseRepository,
    onBack: () -> Unit,
    onAuthComplete: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = SpotifyColors.black,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (step > 0) step-- else onBack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                LinearProgressIndicator(
                    progress = { if (step == 0) 0.5f else 1.0f },
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = SpotifyColors.green,
                    trackColor = SpotifyColors.surface
                )

                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Create account",
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                color = SpotifyColors.lightGrey
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (step == 0) {
                // Step 0: Email
                Text(
                    text = "What's your email?",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "You'll need to confirm this later.",
                    fontFamily = SpotifyMixFontFamily,
                    fontSize = 14.sp,
                    color = SpotifyColors.lightGrey
                )

                Spacer(modifier = Modifier.height(28.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = ""
                    },
                    placeholder = {
                        Text(
                            text = "Email address",
                            fontFamily = SpotifyMixFontFamily,
                            color = SpotifyColors.lightGrey
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        fontFamily = SpotifyMixFontFamily,
                        fontSize = 13.sp,
                        color = Color(0xFFE22134)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                val isEmailValid = email.contains("@") && email.contains(".")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isEmailValid) SpotifyColors.green else SpotifyColors.surface)
                        .clickable(enabled = isEmailValid) {
                            step = 1
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Next",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isEmailValid) Color.Black else SpotifyColors.lightGrey
                    )
                }
            } else {
                // Step 1: Password
                Text(
                    text = "Create a password",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Use at least 8 characters.",
                    fontFamily = SpotifyMixFontFamily,
                    fontSize = 14.sp,
                    color = SpotifyColors.lightGrey
                )

                Spacer(modifier = Modifier.height(28.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = ""
                    },
                    placeholder = {
                        Text(
                            text = "Password",
                            fontFamily = SpotifyMixFontFamily,
                            color = SpotifyColors.lightGrey
                        )
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = SpotifyColors.lightGrey
                            )
                        }
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

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        fontFamily = SpotifyMixFontFamily,
                        fontSize = 13.sp,
                        color = Color(0xFFE22134)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                val isPasswordValid = password.length >= 8
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isPasswordValid && !isLoading) SpotifyColors.green else SpotifyColors.surface)
                        .clickable(enabled = isPasswordValid && !isLoading) {
                            isLoading = true
                            errorMessage = ""
                            coroutineScope.launch {
                                val res = repository.signUpWithEmail(email.trim(), password)
                                isLoading = false
                                if (res.isSuccess) {
                                    onAuthComplete()
                                } else {
                                    // If account already exists or sign up failed, try sign in or show message
                                    val signInRes = repository.signInWithEmail(email.trim(), password)
                                    if (signInRes.isSuccess) {
                                        onAuthComplete()
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Failed to create account"
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Create account",
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (isPasswordValid) Color.Black else SpotifyColors.lightGrey
                        )
                    }
                }
            }
        }
    }
}
