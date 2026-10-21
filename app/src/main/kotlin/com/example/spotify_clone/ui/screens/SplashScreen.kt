package com.example.spotify_clone.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.spotify_clone.R
import com.example.spotify_clone.ui.theme.SpotifyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Spotify-faithful startup sequence:
 * 1. App logo (ic_spotify_icon_rgb_green) appears and fades in with subtle scale (0–900ms)
 * 2. Original splash animation (R.raw.splash Lottie) plays
 * 3. On animation completion, onComplete() triggers navigation:
 *    - First time user: LoginScreen
 *    - Existing user: HomeScreen
 */
@Composable
fun SplashScreen(onComplete: () -> Unit) {
    // 0 = App Logo, 1 = Lottie Splash Animation, 2 = Complete
    var phase by remember { mutableStateOf(0) }

    // Phase 1: Logo animation
    val logoScale = remember { Animatable(0.75f) }
    val logoAlpha = remember { Animatable(0f) }
    val logoExitAlpha = remember { Animatable(1f) }

    // Phase 2: Lottie animation composition
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.splash))
    var startLottie by remember { mutableStateOf(false) }

    val lottieProgress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = startLottie,
        iterations = 1,
        speed = 1.0f
    )

    LaunchedEffect(Unit) {
        // Step 1: Logo fades in and gently scales up
        launch { logoAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        launch { logoScale.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }

        delay(900)

        // Step 2: Smooth transition from logo to Lottie animation
        phase = 1
        launch { logoExitAlpha.animateTo(0f, tween(250, easing = FastOutSlowInEasing)) }
        delay(150)
        startLottie = true
    }

    // Step 3: Trigger onComplete when Lottie finishes
    LaunchedEffect(lottieProgress, startLottie) {
        if (startLottie && lottieProgress >= 0.99f) {
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyColors.black),
        contentAlignment = Alignment.Center
    ) {
        // ── Phase 1: Spotify App Logo ──────────────────────────────────────────
        if (phase == 0 || logoExitAlpha.value > 0.01f) {
            Image(
                painter = painterResource(id = R.drawable.ic_spotify_icon_rgb_green),
                contentDescription = "Spotify",
                modifier = Modifier
                    .size(130.dp)
                    .graphicsLayer {
                        val s = logoScale.value
                        scaleX = s
                        scaleY = s
                        alpha = logoAlpha.value * logoExitAlpha.value
                    }
            )
        }

        // ── Phase 2: Original Lottie Splash Animation ──────────────────────────
        if (phase == 1 && composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { lottieProgress },
                modifier = Modifier.size(220.dp)
            )
        }
    }
}
