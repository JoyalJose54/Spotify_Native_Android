package com.example.spotify_clone.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotify_clone.R
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily

@Composable
fun LoginScreen(
    onSignUp: () -> Unit,
    onGuestLogin: () -> Unit,
    onGoogleLogin: () -> Unit = onGuestLogin
) {
    // Breathing scale for center icon
    val infiniteTransition = rememberInfiniteTransition(label = "login_anim")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    // Rotating dashed arc
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    // Ripples
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyColors.black)
            .padding(horizontal = 32.dp)
    ) {
        // Center Logo & Headlines Block
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .offset(y = (-40).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Icon with concentric ripples & dashed ring
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Expanding ripple rings
                listOf(0.0f, 0.33f, 0.66f).forEach { delayOffset ->
                    val t = (rippleProgress + delayOffset) % 1.0f
                    val ringScale = 1.0f + t * 1.8f
                    val ringAlpha = (1.0f - t).coerceIn(0f, 0.6f)

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .scale(ringScale)
                            .border(
                                width = 1.2.dp,
                                color = SpotifyColors.green.copy(alpha = ringAlpha),
                                shape = CircleShape
                            )
                    )
                }

                // Rotating dashed arc
                Canvas(
                    modifier = Modifier
                        .size(112.dp)
                        .rotate(rotation)
                ) {
                    drawCircle(
                        color = SpotifyColors.green.copy(alpha = 0.28f),
                        style = Stroke(
                            width = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                        )
                    )
                }

                // Central Spotify Logo
                Image(
                    painter = painterResource(id = R.drawable.ic_spotify_icon_rgb_green),
                    contentDescription = "Spotify Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .scale(breathScale)
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "Millions of songs.",
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )

            Text(
                text = "Free on Spotify.",
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = SpotifyColors.green,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Music streaming, redefined.",
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }

        // Bottom Action Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // "Sign up free" - Solid Spotify Green Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SpotifyColors.green)
                    .clickable { onSignUp() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sign up free",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // "Continue with Google" - Outlined Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable { onGoogleLogin() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "G",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Continue with Google",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // "Guest login" - Outlined Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable { onGuestLogin() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Guest login",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
