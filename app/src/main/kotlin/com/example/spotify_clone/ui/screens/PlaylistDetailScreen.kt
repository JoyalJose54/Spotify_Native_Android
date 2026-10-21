package com.example.spotify_clone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotify_clone.data.models.Playlist
import com.example.spotify_clone.data.models.Song
import com.example.spotify_clone.data.remote.FirebaseRepository
import com.example.spotify_clone.ui.components.DuplicateResolverSheet
import com.example.spotify_clone.ui.components.UnreferencedSongsSheet
import com.example.spotify_clone.ui.components.MultiCopyMoveSheet
import com.example.spotify_clone.ui.components.NameDetailsSheet
import com.example.spotify_clone.ui.components.PlaylistCollage
import com.example.spotify_clone.ui.components.PlaylistSortOrder
import com.example.spotify_clone.ui.components.SaveInPlaylistSheet
import com.example.spotify_clone.ui.components.SortSheet
import com.example.spotify_clone.ui.components.TrackListItem
import com.example.spotify_clone.ui.components.TrackOptionsSheet
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily
import com.example.spotify_clone.ui.theme.rememberPaletteColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    playlistSongsFlow: Flow<List<Song>>,
    currentPlayingSongId: String?,
    repository: FirebaseRepository,
    onBackClick: () -> Unit,
    onSongClick: (song: Song, queue: List<Song>) -> Unit,
    onNavigateToAddSongs: ((playlistId: String, existingIds: List<String>) -> Unit)? = null,
    onNavigateToEditPlaylist: ((playlistId: String, songs: List<Song>) -> Unit)? = null,
    onNavigateToAlbum: ((albumId: String, title: String, imageUrl: String) -> Unit)? = null,
    onPlaylistDeleted: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val liveSongs by playlistSongsFlow.collectAsState(initial = playlist.tracks)
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(PlaylistSortOrder.CUSTOM) }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedSongIds = remember { mutableStateListOf<String>() }
    var copyMoveConfig by remember { mutableStateOf<Pair<Boolean, List<String>>?>(null) } // (isMove, songIds)

    BackHandler(enabled = isSelectionMode) {
        selectedSongIds.clear()
        isSelectionMode = false
    }

    var showSortSheet by remember { mutableStateOf(false) }
    var showNameDetailsSheet by remember { mutableStateOf(false) }
    var showDuplicateResolverSheet by remember { mutableStateOf(false) }
    var showUnreferencedSongsSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedTrackForOptions by remember { mutableStateOf<Song?>(null) }
    var selectedTrackForSave by remember { mutableStateOf<Song?>(null) }

    val allPlaylists by repository.streamRawPlaylists().collectAsState(initial = emptyList())
    val unreferencedCount by remember(liveSongs, allPlaylists) {
        derivedStateOf {
            if (playlist.id != "all_songs") 0
            else {
                val referencedIds = allPlaylists
                    .filter { it.id != "all_songs" }
                    .flatMap { it.trackIds }
                    .toSet()
                liveSongs.count { it.id !in referencedIds }
            }
        }
    }

    val listState = rememberLazyListState()

    val showAppBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300
        }
    }

    val displaySongs = remember(liveSongs, searchQuery, sortOrder) {
        var result = liveSongs
        if (searchQuery.isNotEmpty()) {
            val q = searchQuery.trim().lowercase()
            val tier1 = ArrayList<Song>() // Title starts with query
            val tier2 = ArrayList<Song>() // Word in title starts with query
            val tier3 = ArrayList<Song>() // Artist starts with query
            val tier4 = ArrayList<Song>() // Word in artist starts with query
            val tier5 = ArrayList<Song>() // Title contains query (multi-char only)
            val tier6 = ArrayList<Song>() // Artist/Album contains query (multi-char only)

            val isSingleChar = q.length == 1
            val wordDelimiters = charArrayOf(' ', '-', '_', '(', ')', '[', ']', ',', '.', '\'', '"', '/', '&')

            for (song in result) {
                val titleLow = song.title.lowercase()
                val artistLow = song.artist.lowercase()
                val albumLow = song.album.lowercase()

                if (titleLow.startsWith(q)) {
                    tier1.add(song)
                    continue
                }

                val titleWords = titleLow.split(*wordDelimiters).filter { it.isNotEmpty() }
                if (titleWords.any { it.startsWith(q) }) {
                    tier2.add(song)
                    continue
                }

                if (artistLow.startsWith(q)) {
                    tier3.add(song)
                    continue
                }

                val artistWords = artistLow.split(*wordDelimiters).filter { it.isNotEmpty() }
                if (artistWords.any { it.startsWith(q) }) {
                    tier4.add(song)
                    continue
                }

                if (!isSingleChar) {
                    if (titleLow.contains(q)) {
                        tier5.add(song)
                    } else if (artistLow.contains(q) || albumLow.contains(q)) {
                        tier6.add(song)
                    }
                }
            }
            result = tier1 + tier2 + tier3 + tier4 + tier5 + tier6
        }
        when (sortOrder) {
            PlaylistSortOrder.TITLE -> result.sortedBy { it.title.lowercase() }
            PlaylistSortOrder.ARTIST -> result.sortedBy { it.artist.lowercase() }
            PlaylistSortOrder.ALBUM -> result.sortedBy { it.album.lowercase() }
            PlaylistSortOrder.RECENTLY_ADDED -> {
                if (playlist.id == "all_songs") {
                    result.sortedWith { a, b ->
                        val timeA = a.createdAt?.time ?: 0L
                        val timeB = b.createdAt?.time ?: 0L
                        timeB.compareTo(timeA)
                    }
                } else {
                    result.reversed()
                }
            }
            PlaylistSortOrder.CUSTOM -> result
        }
    }

    val firstCoverUrl = liveSongs.firstOrNull()?.imageUrl ?: playlist.imageUrl
    val dynamicPalette = rememberPaletteColors(imageUrl = firstCoverUrl)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyColors.black)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            // Dynamic Gradient Header & Album Art Collage
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    dynamicPalette.bgColor.copy(alpha = 0.85f),
                                    dynamicPalette.bgColorDark.copy(alpha = 0.55f),
                                    SpotifyColors.black
                                ),
                                startY = 0f,
                                endY = 1000f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    PlaylistCollage(
                        playlist = playlist,
                        currentTracks = remember(liveSongs) { liveSongs.take(4) },
                        size = 210.dp,
                        modifier = Modifier.graphicsLayer {
                            val index = listState.firstVisibleItemIndex
                            val offset = listState.firstVisibleItemScrollOffset
                            val scale = if (index > 0) 0.65f else (1f - (offset / 500f).coerceIn(0f, 0.35f))
                            scaleX = scale
                            scaleY = scale
                            alpha = (scale * 1.2f).coerceIn(0f, 1f)
                        }
                    )
                }
            }

            // Playlist Info + Find in playlist Search Box + Action Buttons Row
            item {
                // "Find in playlist" Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF242424))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SpotifyColors.lightGrey,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = SpotifyMixFontFamily
                            ),
                            cursorBrush = SolidColor(SpotifyColors.green),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Find in playlist",
                                        color = SpotifyColors.lightGrey,
                                        fontSize = 13.sp,
                                        fontFamily = SpotifyMixFontFamily
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = SpotifyColors.lightGrey,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = playlist.name,
                    fontFamily = SpotifyMixFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = Color.White,
                    letterSpacing = (-0.3).sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Curator / Owner row
                if (playlist.owner.isNotEmpty() && !playlist.owner.contains("local_user")) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(SpotifyColors.green),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = playlist.owner.take(1).uppercase(),
                                fontFamily = SpotifyMixFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = Color.Black
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = playlist.owner,
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Lock + Duration
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = SpotifyColors.lightGrey,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "${liveSongs.size} songs • ${formatTotalDuration(liveSongs)}",
                        fontFamily = SpotifyMixFontFamily,
                        fontSize = 13.sp,
                        color = SpotifyColors.lightGrey
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Row (Download, Share, Shuffle, Big Play Button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowCircleDown,
                        contentDescription = "Download",
                        tint = SpotifyColors.lightGrey,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = SpotifyColors.lightGrey,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = SpotifyColors.lightGrey,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(SpotifyColors.green)
                            .clickable {
                                if (displaySongs.isNotEmpty()) {
                                    onSongClick(displaySongs.first(), displaySongs)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pill Buttons Row (Add, Edit, Duplicates, Unreferenced, Sort)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (playlist.id == "all_songs") {
                        PillButton(
                            icon = Icons.Default.CleaningServices,
                            label = "Duplicates",
                            contentColor = SpotifyColors.green,
                            onClick = { showDuplicateResolverSheet = true }
                        )
                        PillButton(
                            icon = Icons.Default.LinkOff,
                            label = if (unreferencedCount > 0) "Unreferenced ($unreferencedCount)" else "Unreferenced",
                            contentColor = SpotifyColors.green,
                            onClick = { showUnreferencedSongsSheet = true }
                        )
                    } else {
                        PillButton(
                            icon = Icons.Default.Add,
                            label = "Add",
                            onClick = {
                                onNavigateToAddSongs?.invoke(playlist.id, liveSongs.map { it.id })
                            }
                        )
                        PillButton(
                            icon = Icons.Default.Edit,
                            label = "Edit",
                            onClick = {
                                onNavigateToEditPlaylist?.invoke(playlist.id, liveSongs)
                            }
                        )
                    }
                    PillButton(
                        icon = Icons.Default.SwapHoriz,
                        label = sortOrder.label,
                        isActive = sortOrder != PlaylistSortOrder.CUSTOM,
                        onClick = { showSortSheet = true }
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Track List
            items(displaySongs, key = { it.id }, contentType = { "track" }) { song ->
                val isSelected = selectedSongIds.contains(song.id)
                TrackListItem(
                    song = song,
                    isPlaying = song.id == currentPlayingSongId,
                    onClick = { onSongClick(song, displaySongs) },
                    onOptionsClick = { selectedTrackForOptions = song },
                    isSelectionMode = isSelectionMode,
                    isSelected = isSelected,
                    onSelectedChange = { checked ->
                        if (checked) selectedSongIds.add(song.id)
                        else selectedSongIds.remove(song.id)
                    },
                    onLongClick = {
                        if (playlist.id != "all_songs") {
                            isSelectionMode = true
                            if (!selectedSongIds.contains(song.id)) {
                                selectedSongIds.add(song.id)
                            }
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(120.dp))
            }
        }

        // Top Pinned App Bar (Back chevron, Title, 3-dots)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .background(
                    if (showAppBarTitle) Color(0xFF121212) else Color.Transparent
                )
                .padding(top = 32.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                AnimatedVisibility(
                    visible = showAppBarTitle,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = playlist.name,
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!showAppBarTitle) {
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (playlist.id != "all_songs") {
                    IconButton(onClick = { showNameDetailsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Multi-Selection Bottom Bar (Cancel, Copy, Move)
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Surface(
                color = Color(0xFF282828),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SelectionActionButton(
                        icon = Icons.Default.Close,
                        label = "Cancel",
                        onClick = {
                            selectedSongIds.clear()
                            isSelectionMode = false
                        }
                    )

                    SelectionActionButton(
                        icon = Icons.Default.CopyAll,
                        label = "Copy",
                        enabled = selectedSongIds.isNotEmpty(),
                        onClick = {
                            copyMoveConfig = Pair(false, selectedSongIds.toList())
                        }
                    )

                    SelectionActionButton(
                        icon = Icons.Default.SwapHoriz,
                        label = "Move",
                        enabled = selectedSongIds.isNotEmpty(),
                        onClick = {
                            copyMoveConfig = Pair(true, selectedSongIds.toList())
                        }
                    )

                    SelectionActionButton(
                        icon = Icons.Default.DeleteOutline,
                        label = "Delete",
                        enabled = selectedSongIds.isNotEmpty(),
                        color = Color(0xFFFF5252),
                        onClick = {
                            showDeleteConfirmDialog = true
                        }
                    )
                }
            }
        }

        // Sort Sheet
        if (showSortSheet) {
            SortSheet(
                currentSort = sortOrder,
                onSortSelected = { sortOrder = it },
                onDismiss = { showSortSheet = false }
            )
        }

        // Name and details Sheet
        if (showNameDetailsSheet) {
            NameDetailsSheet(
                playlist = playlist,
                repository = repository,
                onDismiss = { showNameDetailsSheet = false },
                onPlaylistDeleted = {
                    showNameDetailsSheet = false
                    onPlaylistDeleted?.invoke() ?: onBackClick()
                }
            )
        }

        // Track Options Sheet
        selectedTrackForOptions?.let { track ->
            TrackOptionsSheet(
                song = track,
                currentPlaylistId = playlist.id,
                repository = repository,
                onDismiss = { selectedTrackForOptions = null },
                onAddToPlaylist = { selectedTrackForSave = track },
                onNavigateToAlbum = onNavigateToAlbum
            )
        }

        // Save in Playlist Sheet
        selectedTrackForSave?.let { track ->
            SaveInPlaylistSheet(
                song = track,
                repository = repository,
                onDismiss = { selectedTrackForSave = null }
            )
        }

        // Duplicates Resolver Sheet (Only for "All Songs" master library)
        if (showDuplicateResolverSheet) {
            DuplicateResolverSheet(
                repository = repository,
                onDismiss = { showDuplicateResolverSheet = false }
            )
        }

        // Unreferenced Songs Sheet (Only for "All Songs" master library)
        if (showUnreferencedSongsSheet) {
            UnreferencedSongsSheet(
                repository = repository,
                currentPlayingSongId = currentPlayingSongId,
                onSongClick = onSongClick,
                onDismiss = { showUnreferencedSongsSheet = false }
            )
        }

        // Multi-Selection Delete Confirmation Dialog
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                containerColor = SpotifyColors.surface,
                shape = RoundedCornerShape(12.dp),
                title = {
                    Text(
                        text = "Remove From Playlist?",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                },
                text = {
                    Text(
                        text = "Remove ${selectedSongIds.size} song${if (selectedSongIds.size > 1) "s" else ""} from this playlist? The tracks will remain in your library.",
                        fontFamily = SpotifyMixFontFamily,
                        fontSize = 14.sp,
                        color = SpotifyColors.lightGrey
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val toDelete = selectedSongIds.toList()
                            showDeleteConfirmDialog = false
                            coroutineScope.launch {
                                repository.removeTracksFromPlaylist(playlist.id, toDelete)
                                selectedSongIds.clear()
                                isSelectionMode = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Text(
                            text = "Remove",
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text(
                            text = "Cancel",
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyColors.lightGrey
                        )
                    }
                }
            )
        }

        // Multi Copy / Move Sheet
        copyMoveConfig?.let { (isMove, songIds) ->
            MultiCopyMoveSheet(
                isMove = isMove,
                sourcePlaylist = playlist,
                selectedSongIds = songIds,
                repository = repository,
                onDismiss = { copyMoveConfig = null },
                onComplete = {
                    copyMoveConfig = null
                    selectedSongIds.clear()
                    isSelectionMode = false
                }
            )
        }
    }
}

@Composable
private fun SelectionActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    color: Color = Color.White,
    onClick: () -> Unit
) {
    val finalColor = if (enabled) color else Color.White.copy(alpha = 0.3f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = finalColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontFamily = SpotifyMixFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = finalColor
        )
    }
}

@Composable
private fun PillButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    val finalContentColor = if (isActive) Color.Black else contentColor
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) SpotifyColors.green else Color(0xFF282828))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = finalContentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = finalContentColor
            )
        }
    }
}

private fun formatTotalDuration(songs: List<Song>): String {
    val totalSec = songs.sumOf { it.durationMs } / 1000
    val min = totalSec / 60
    return if (min >= 60) {
        val hrs = min / 60
        val remMin = min % 60
        "${hrs}h ${remMin}m"
    } else {
        "${min}m"
    }
}
