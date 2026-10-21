package com.example.spotify_clone.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.spotify_clone.ui.theme.SpotifyColors
import kotlinx.coroutines.launch

// ── Convx-authentic spring physics ───────────────────────────────────────────
// stiffness = 360f, dampingRatio = Spring.DampingRatioNoBouncy (matches Convx spec)
private val ConvxSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 360f
)

// ── Convx transition handoff point ───────────────────────────────────────────
private const val PLAYER_LAYER_HANDOFF_PROGRESS = 0.20f

/**
 * Convx-authentic Player Morphing Bottom Sheet.
 *
 * Implements the exact morphing transition seen in the Convx app:
 * - Collapsed state is strictly a floating mini-player pill (58dp) with ZERO overflow below
 * - Fluid vertical translation from resting position above bottom bar to full screen
 * - Edge-to-edge container margins transition from 8dp floating pill to 0dp full-screen
 * - Corner radii morph from 12dp pill rounding to full-screen corners
 * - Clean handoff: mini-player content fades out across [0..0.2], full-player fades in [0.2..0.45]
 * - 60/120Hz smooth rendering with hardware-accelerated RenderThread graphicsLayer
 */
@Composable
fun PlayerBottomSheet(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    bottomBarHeight: Dp,
    modifier: Modifier = Modifier,
    miniPlayerHeight: Dp = 58.dp,
    containerColor: Color = SpotifyColors.cardBg,
    miniPlayer: @Composable BoxScope.() -> Unit,
    fullPlayer: @Composable BoxScope.() -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Single Animatable drives the morph: 0 = collapsed (mini), 1 = expanded (full)
    val progress = remember { Animatable(if (isExpanded) 1f else 0f) }

    // Synchronize external state changes
    LaunchedEffect(isExpanded) {
        val target = if (isExpanded) 1f else 0f
        if (progress.targetValue != target) {
            progress.animateTo(target, ConvxSpring)
        }
    }

    // Intercept back gesture when expanded
    BackHandler(enabled = progress.value > 0.05f) {
        onExpandedChange(false)
        coroutineScope.launch { progress.animateTo(0f, ConvxSpring) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeightDp = maxHeight
        val screenHeightPx = with(density) { screenHeightDp.toPx() }
        val bottomBarHeightPx = with(density) { bottomBarHeight.toPx() }
        val miniPlayerHeightPx = with(density) { miniPlayerHeight.toPx() }
        val miniVerticalMarginPx = with(density) { 4.dp.toPx() }

        // The resting Y position of the collapsed mini player (from top of screen)
        val collapsedTopPx = (screenHeightPx - bottomBarHeightPx - miniPlayerHeightPx - miniVerticalMarginPx)
            .coerceAtLeast(0f)

        val travelPx = collapsedTopPx.coerceAtLeast(1f)

        val collapsedCornerDp = 12.dp
        val collapsedCornerPx = with(density) { collapsedCornerDp.toPx() }

        val p = progress.value.coerceIn(0f, 1f)

        // Interpolated bounds:
        // Collapsed (p = 0): exactly miniPlayerHeight (58dp) pill resting at collapsedTopPx
        // Expanding (p -> 1): top slides to 0, height expands to screenHeight, corners flatten
        val currentTopPx = lerp(collapsedTopPx, 0f, p)
        val currentHeightPx = lerp(miniPlayerHeightPx, screenHeightPx, p)
        val horizontalPaddingDp = lerp(8.dp.value, 0f, p).dp
        val cornerPx = lerp(collapsedCornerPx, 0f, p)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { currentHeightPx.toDp() })
                .graphicsLayer {
                    // Smooth vertical translation
                    translationY = currentTopPx
                }
                .padding(horizontal = horizontalPaddingDp)
                .graphicsLayer {
                    // Smooth corner morph: 12dp rounded pill -> 0dp edge-to-edge
                    shape = RoundedCornerShape(cornerPx)
                    clip = true
                    shadowElevation = lerp(16f, 0f, p)
                }
                .background(containerColor)
                .pointerInput(collapsedTopPx) {
                    val velocityTracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = { velocityTracker.resetTracking() },
                        onVerticalDrag = { change, dragAmount ->
                            velocityTracker.addPointerInputChange(change)
                            val deltaFraction = -dragAmount / travelPx
                            coroutineScope.launch {
                                progress.snapTo((progress.value + deltaFraction).coerceIn(0f, 1f))
                            }
                        },
                        onDragEnd = {
                            val velocityY = velocityTracker.calculateVelocity().y
                            velocityTracker.resetTracking()
                            val target = when {
                                velocityY > 600f -> 0f  // fling down -> collapse
                                velocityY < -600f -> 1f // fling up -> expand
                                progress.value >= 0.45f -> 1f
                                else -> 0f
                            }
                            onExpandedChange(target == 1f)
                            coroutineScope.launch { progress.animateTo(target, ConvxSpring) }
                        },
                        onDragCancel = {
                            velocityTracker.resetTracking()
                            val target = if (progress.value >= 0.45f) 1f else 0f
                            onExpandedChange(target == 1f)
                            coroutineScope.launch { progress.animateTo(target, ConvxSpring) }
                        }
                    )
                }
        ) {
            // ── Full Player Page ─────────────────────────────────────────────
            // Kept off composition when completely collapsed to maximize 120Hz scroll fps
            if (p > 0.04f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(screenHeightDp)
                        .graphicsLayer {
                            // Convx handoff: starts fading in at progress = 0.20, fully opaque at 0.45
                            val fullAlpha = ((p - PLAYER_LAYER_HANDOFF_PROGRESS) / 0.25f).coerceIn(0f, 1f)
                            alpha = fullAlpha
                            val s = lerp(0.96f, 1.0f, p)
                            scaleX = s
                            scaleY = s
                        }
                ) {
                    fullPlayer()
                }
            }

            // ── Mini Player Bar ──────────────────────────────────────────────
            // Visible when sheet is collapsed or in early expansion phase
            if (p < 0.95f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(miniPlayerHeight)
                        .graphicsLayer {
                            // Convx handoff: fades out quickly across [0 .. 0.20]
                            val miniAlpha = (1f - (p / PLAYER_LAYER_HANDOFF_PROGRESS)).coerceIn(0f, 1f)
                            alpha = miniAlpha
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (p < 0.1f) {
                                    onExpandedChange(true)
                                    coroutineScope.launch { progress.animateTo(1f, ConvxSpring) }
                                }
                            }
                        )
                ) {
                    miniPlayer()
                }
            }
        }
    }
}
