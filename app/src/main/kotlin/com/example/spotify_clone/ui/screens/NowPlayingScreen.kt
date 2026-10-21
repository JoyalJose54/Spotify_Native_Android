package com.example.spotify_clone.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.media3.common.Player
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue
import coil3.compose.AsyncImage
import com.example.spotify_clone.R
import com.example.spotify_clone.data.models.Song
import com.example.spotify_clone.data.remote.FirebaseRepository
import com.example.spotify_clone.ui.components.TrackListItem
import com.example.spotify_clone.ui.components.TrackOptionsSheet
import com.example.spotify_clone.ui.components.SaveInPlaylistSheet
import com.example.spotify_clone.ui.components.SleepTimerSheet
import com.example.spotify_clone.playback.PlayerConnection
import com.example.spotify_clone.playback.SleepTimerOption
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily
import com.example.spotify_clone.ui.theme.rememberPaletteColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    song: Song,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isShuffle: Boolean,
    positionMs: Long,
    durationMs: Long,
    playlistName: String?,
    queue: List<Song>,
    queueIndex: Int,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffleClick: () -> Unit = {},
    onRepeatClick: (() -> Unit)? = null,
    onQueueItemClick: (Int) -> Unit,
    onCollapseClick: () -> Unit,
    repository: FirebaseRepository? = null,
    playerConnection: PlayerConnection? = null,
    onNavigateToAlbum: ((albumId: String, title: String, imageUrl: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = rememberPaletteColors(song.imageUrl)
    val coroutineScope = rememberCoroutineScope()
    var showTrackOptionsSheet by remember { mutableStateOf(false) }
    var showSaveInPlaylistSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    val activeSleepTimerOption by (playerConnection?.activeSleepTimerOption?.collectAsState()
        ?: remember { mutableStateOf(null) })

    val currentRepeatMode by (playerConnection?.repeatMode?.collectAsState()
        ?: remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) })

    val likedSongIds by (repository?.streamLikedSongIds()?.collectAsState(initial = emptySet())
        ?: remember { mutableStateOf(emptySet()) })
    val isLiked = likedSongIds.contains(song.id)


    val effectiveQueue = remember(queue, song) {
        if (queue.isEmpty()) listOf(song) else queue
    }
    val effectiveIndex = remember(effectiveQueue, queueIndex, song.id) {
        val found = effectiveQueue.indexOfFirst { it.id == song.id }
        if (found >= 0) found
        else queueIndex.coerceIn(0, (effectiveQueue.size - 1).coerceAtLeast(0))
    }

    val pagerState = rememberPagerState(
        initialPage = effectiveIndex
    ) {
        effectiveQueue.size
    }

    // Smoothly scroll pager when external track changes occur (Next/Prev buttons, autoplay)
    androidx.compose.runtime.LaunchedEffect(effectiveIndex) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != effectiveIndex) {
            pagerState.animateScrollToPage(effectiveIndex)
        }
    }

    // Trigger song change when user finishes swiping to a new page
    androidx.compose.runtime.LaunchedEffect(pagerState.settledPage) {
        if (!pagerState.isScrollInProgress && pagerState.settledPage != effectiveIndex && pagerState.settledPage in effectiveQueue.indices) {
            onQueueItemClick(pagerState.settledPage)
        }
    }

    val animatedBg by animateColorAsState(
        targetValue = palette.bgColor,
        animationSpec = tween(500),
        label = "bgTop"
    )
    val animatedBgDark by animateColorAsState(
        targetValue = palette.bgColorDark,
        animationSpec = tween(500),
        label = "bgDark"
    )
    val animatedBgBottom by animateColorAsState(
        targetValue = palette.bgColorBottom,
        animationSpec = tween(500),
        label = "bgBottom"
    )

    var isQueueSheetVisible by remember { mutableStateOf(false) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderProgress by remember { mutableFloatStateOf(0f) }

    val currentProgress = if (isDraggingSlider) {
        sliderProgress
    } else if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val gradientBrush = Brush.verticalGradient(
        listOf(animatedBg, animatedBgDark, animatedBgBottom)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBrush)
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // ── HEADER ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapseClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PLAYING FROM PLAYLIST",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = playlistName ?: if (song.album.isNotEmpty()) song.album else "All Songs",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                IconButton(
                    onClick = { showTrackOptionsSheet = true },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.7f))

            // ── ALBUM ART with Horizontal Swipe (Smooth & Fast) ───────────────
            if (effectiveQueue.size > 1) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentPadding = PaddingValues(horizontal = 28.dp),
                    pageSpacing = 16.dp,
                    key = { page -> effectiveQueue.getOrNull(page)?.id ?: page }
                ) { page ->
                    val pageSong = effectiveQueue.getOrNull(page) ?: song
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                    val scale = lerp(1f, 0.90f, pageOffset.coerceIn(0f, 1f))
                    val alpha = lerp(1f, 0.55f, pageOffset.coerceIn(0f, 1f))

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            }
                            .shadow(24.dp, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(SpotifyColors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pageSong.imageUrl.isNotEmpty()) {
                            AsyncImage(
                                model = pageSong.imageUrl,
                                contentDescription = pageSong.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = SpotifyColors.lightGrey,
                                modifier = Modifier.size(80.dp)
                            )
                        }
                    }
                }
            } else {
                var dragOffsetX by remember { mutableFloatStateOf(0f) }
                val animatedOffsetX by animateFloatAsState(
                    targetValue = dragOffsetX,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                    label = "singleCoverDrag"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(1f)
                        .graphicsLayer { translationX = animatedOffsetX }
                        .shadow(24.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpotifyColors.surface)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetX += dragAmount * 0.75f
                                },
                                onDragEnd = {
                                    if (dragOffsetX < -50f) {
                                        onNextClick()
                                    } else if (dragOffsetX > 50f) {
                                        onPreviousClick()
                                    }
                                    dragOffsetX = 0f
                                },
                                onDragCancel = {
                                    dragOffsetX = 0f
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (song.imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = song.imageUrl,
                            contentDescription = song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = SpotifyColors.lightGrey,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.7f))

            // ── SONG INFO ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = song.title,
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (song.artist.isNotEmpty()) song.artist else "Unknown Artist",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── PROGRESS BAR ──────────────────────────────────────────────────
            Slider(
                value = currentProgress,
                onValueChange = {
                    isDraggingSlider = true
                    sliderProgress = it
                },
                onValueChangeFinished = {
                    isDraggingSlider = false
                    val targetMs = (sliderProgress * durationMs).toLong()
                    onSeek(targetMs)
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 16.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(if (isDraggingSlider) (sliderProgress * durationMs).toLong() else positionMs),
                    fontFamily = SpotifyMixFontFamily,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = formatMs(durationMs),
                    fontFamily = SpotifyMixFontFamily,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── CONTROLS ROW ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Repeat / Loop with green indicator dot & RepeatOne support
                val isRepeatActive = (currentRepeatMode != Player.REPEAT_MODE_OFF)
                val isRepeatOne = (currentRepeatMode == Player.REPEAT_MODE_ONE)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        onRepeatClick?.invoke() ?: playerConnection?.toggleRepeatMode()
                    }
                ) {
                    Icon(
                        imageVector = if (isRepeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (isRepeatActive) SpotifyColors.green else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    if (isRepeatActive) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(SpotifyColors.green)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // Previous
                IconButton(
                    onClick = onPreviousClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.previous),
                        contentDescription = "Previous",
                        modifier = Modifier.size(42.dp)
                    )
                }

                // Play / Pause / Buffering
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(68.dp)
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = Color.White.copy(alpha = 0.8f),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(38.dp)
                        )
                    } else {
                        Image(
                            painter = painterResource(
                                id = if (isPlaying) R.drawable.pause else R.drawable.play
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(68.dp)
                        )
                    }
                }

                // Next
                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.next),
                        contentDescription = "Next",
                        modifier = Modifier.size(42.dp)
                    )
                }

                // Sleep Timer with indicator
                val isTimerActive = (activeSleepTimerOption != null)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { showSleepTimerSheet = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Sleep timer",
                        tint = if (isTimerActive) SpotifyColors.green else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    if (isTimerActive) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(SpotifyColors.green)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // ── BOTTOM ROW ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = "Devices",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { /* share */ }
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = "Queue",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { isQueueSheetVisible = true }
                    )
                }
            }
        }
    }

    // ── QUEUE SHEET ───────────────────────────────────────────────────────────
    if (isQueueSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { isQueueSheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SpotifyColors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Next in queue",
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(queue, key = { index, s -> "${s.id}_$index" }) { index, s ->
                        TrackListItem(
                            song = s,
                            isPlaying = index == queueIndex,
                            onClick = {
                                onQueueItemClick(index)
                                isQueueSheetVisible = false
                            }
                        )
                    }
                }
            }
        }
    }

    if (showTrackOptionsSheet && repository != null) {
        TrackOptionsSheet(
            song = song,
            currentPlaylistId = null,
            repository = repository,
            onDismiss = { showTrackOptionsSheet = false },
            onAddToPlaylist = { showSaveInPlaylistSheet = true },
            onNavigateToAlbum = onNavigateToAlbum
        )
    }

    if (showSaveInPlaylistSheet && repository != null) {
        SaveInPlaylistSheet(
            song = song,
            repository = repository,
            onDismiss = { showSaveInPlaylistSheet = false }
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
            activeOption = activeSleepTimerOption,
            onSelectOption = { option ->
                playerConnection?.setSleepTimer(option)
            },
            onTurnOffTimer = {
                playerConnection?.clearSleepTimer()
            },
            onDismiss = { showSleepTimerSheet = false }
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
