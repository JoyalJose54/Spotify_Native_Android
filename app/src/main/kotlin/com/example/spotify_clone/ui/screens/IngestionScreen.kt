package com.example.spotify_clone.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.spotify_clone.data.models.Song
import com.example.spotify_clone.data.remote.CsvEntry
import com.example.spotify_clone.data.remote.FirebaseRepository
import com.example.spotify_clone.data.remote.IngestionService
import com.example.spotify_clone.data.remote.YouTubeSearchResult
import com.example.spotify_clone.ui.theme.SpotifyColors
import com.example.spotify_clone.ui.theme.SpotifyMixFontFamily
import com.example.spotify_clone.utils.MatchResult
import com.example.spotify_clone.utils.SongDuplicateMatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngestionScreen(
    repository: FirebaseRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ingestionService = remember { IngestionService(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Search, 1: CSV Batch

    var isOnline by remember { mutableStateOf(false) }
    var isCheckingStatus by remember { mutableStateOf(true) }

    val userPlaylists by repository.streamPlaylists().collectAsState(initial = emptyList())
    val allSongs by repository.streamAllSongs().collectAsState(initial = emptyList())
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var isPlaylistDropdownExpanded by remember { mutableStateOf(false) }

    // Search Tab State
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<YouTubeSearchResult>>(emptyList()) }
    val ingestingMap = remember { mutableStateMapOf<String, String>() }
    val ingestedMap = remember { mutableStateMapOf<String, Boolean>() }

    // Duplicate detection prompt states
    var pendingSearchQuery by remember { mutableStateOf<String?>(null) }
    var searchDuplicateMatches by remember { mutableStateOf<List<Pair<Song, MatchResult>>>(emptyList()) }
    var showSearchDuplicateDialog by remember { mutableStateOf(false) }

    var pendingIngestItem by remember { mutableStateOf<YouTubeSearchResult?>(null) }
    var ingestDuplicateMatches by remember { mutableStateOf<List<Pair<Song, MatchResult>>>(emptyList()) }
    var showIngestDuplicateDialog by remember { mutableStateOf(false) }

    fun performSearch(query: String) {
        if (query.isNotEmpty() && !isSearching) {
            isSearching = true
            coroutineScope.launch {
                try {
                    val results = ingestionService.searchYouTube(query)
                    searchResults = results
                    if (results.isEmpty()) {
                        if (!isOnline) {
                            Toast.makeText(context, "Backend is offline. Check settings or Wi-Fi.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "No results found for \"$query\"", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("IngestionScreen", "Search error", e)
                    Toast.makeText(context, "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isSearching = false
                }
            }
        }
    }

    val triggerSearch: (Boolean) -> Unit = { force ->
        val query = searchQuery.trim()
        if (query.isNotEmpty() && !isSearching) {
            if (!force) {
                // Strict check: if any word or similarity matches existing songs in DB, prompt first!
                val matches = SongDuplicateMatcher.findMatchesInLibrary(query, allSongs)
                if (matches.isNotEmpty()) {
                    pendingSearchQuery = query
                    searchDuplicateMatches = matches
                    showSearchDuplicateDialog = true
                } else {
                    performSearch(query)
                }
            } else {
                performSearch(query)
            }
        }
    }

    fun executeIngest(item: YouTubeSearchResult) {
        if (selectedPlaylistId.isNullOrEmpty()) {
            Toast.makeText(context, "Please select a target playlist first", Toast.LENGTH_SHORT).show()
            isPlaylistDropdownExpanded = true
            return
        }

        ingestingMap[item.videoId] = "Downloading..."
        coroutineScope.launch {
            val res = ingestionService.ingestTrack(
                title = item.title,
                artist = item.artist,
                videoId = item.videoId,
                thumbnailUrl = item.thumbnailUrl,
                playlistId = selectedPlaylistId,
                onProgressUpdate = { step ->
                    ingestingMap[item.videoId] = step
                }
            )
            ingestingMap.remove(item.videoId)
            if (res.success) {
                ingestedMap[item.videoId] = true
                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // CSV Tab State
    var csvInput by remember { mutableStateOf("") }
    var isProcessingCsv by remember { mutableStateOf(false) }
    var csvProgress by remember { mutableStateOf("") }
    val csvEntries = remember { mutableStateListOf<Pair<CsvEntry, String>>() } // (entry, status)

    // Settings Dialog
    var showSettingsDialog by remember { mutableStateOf(false) }
    var customUrlInput by remember { mutableStateOf(ingestionService.backendUrl) }

    // Check backend status periodically
    LaunchedEffect(Unit) {
        while (true) {
            isCheckingStatus = true
            isOnline = ingestionService.checkStatus()
            isCheckingStatus = false
            delay(5000)
        }
    }

    Scaffold(
        containerColor = SpotifyColors.black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Smart Ingestion",
                            fontFamily = SpotifyMixFontFamily,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCheckingStatus) Color(0xFFFFB300)
                                        else if (isOnline) SpotifyColors.green
                                        else Color(0xFFFF5252)
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isCheckingStatus) "Connecting..." else if (isOnline) "Server Online" else "Server Offline",
                                fontFamily = SpotifyMixFontFamily,
                                color = SpotifyColors.lightGrey,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        customUrlInput = ingestionService.backendUrl
                        showSettingsDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpotifyColors.black)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Target Playlist Picker Dropdown (Enforced: Required for Ingestion)
            val selectedPlaylist = userPlaylists.firstOrNull { it.id == selectedPlaylistId }
            val hasPlaylistSelected = selectedPlaylist != null

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(
                        width = 1.dp,
                        color = if (hasPlaylistSelected) SpotifyColors.green.copy(alpha = 0.6f) else Color(0xFFFF9800).copy(alpha = 0.8f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { isPlaylistDropdownExpanded = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (hasPlaylistSelected) SpotifyColors.green.copy(alpha = 0.2f) else Color(0xFFFF9800).copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (hasPlaylistSelected) Icons.Default.QueueMusic else Icons.Default.PlaylistAdd,
                                contentDescription = null,
                                tint = if (hasPlaylistSelected) SpotifyColors.green else Color(0xFFFF9800),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Target Playlist",
                                    fontFamily = SpotifyMixFontFamily,
                                    color = SpotifyColors.lightGrey,
                                    fontSize = 11.sp
                                )
                                if (!hasPlaylistSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "* Required",
                                        fontFamily = SpotifyMixFontFamily,
                                        color = Color(0xFFFF9800),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = selectedPlaylist?.name ?: "Select a playlist to ingest into...",
                                fontFamily = SpotifyMixFontFamily,
                                color = if (hasPlaylistSelected) Color.White else Color(0xFFE0E0E0),
                                fontWeight = if (hasPlaylistSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select",
                        tint = if (hasPlaylistSelected) SpotifyColors.green else Color(0xFFFF9800)
                    )
                }

                DropdownMenu(
                    expanded = isPlaylistDropdownExpanded,
                    onDismissRequest = { isPlaylistDropdownExpanded = false },
                    modifier = Modifier
                        .background(Color(0xFF282828))
                        .widthIn(min = 240.dp)
                ) {
                    if (userPlaylists.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No playlists available", color = SpotifyColors.lightGrey) },
                            onClick = { isPlaylistDropdownExpanded = false }
                        )
                    } else {
                        userPlaylists.forEach { pl ->
                            val isSelected = pl.id == selectedPlaylistId
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QueueMusic,
                                            contentDescription = null,
                                            tint = if (isSelected) SpotifyColors.green else SpotifyColors.lightGrey,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pl.name,
                                                color = if (isSelected) SpotifyColors.green else Color.White,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 14.sp
                                            )
                                            if (pl.trackIds.isNotEmpty()) {
                                                Text(
                                                    text = "${pl.trackIds.size} songs",
                                                    color = SpotifyColors.lightGrey,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = SpotifyColors.green,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedPlaylistId = pl.id
                                    isPlaylistDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Tabs: Search vs Batch CSV
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SpotifyColors.black,
                contentColor = SpotifyColors.green,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Search YouTube",
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) SpotifyColors.green else SpotifyColors.lightGrey
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Batch CSV",
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 1) SpotifyColors.green else SpotifyColors.lightGrey
                        )
                    }
                )
            }

            if (selectedTab == 0) {
                // ── TAB 1: SEARCH & INGEST ─────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text("Search songs on YouTube...", color = SpotifyColors.lightGrey, fontSize = 14.sp)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = SpotifyColors.lightGrey, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(color = SpotifyColors.green, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { triggerSearch(false) }) {
                                    Text("GO", color = SpotifyColors.green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { triggerSearch(false) }),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E1E1E),
                            unfocusedContainerColor = Color(0xFF1E1E1E),
                            focusedBorderColor = SpotifyColors.green,
                            unfocusedBorderColor = Color(0xFF333333),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                }

                if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = SpotifyColors.lightGrey.copy(alpha = 0.4f),
                                modifier = Modifier.size(60.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isSearching) "Searching YouTube..." else "Search for any song to ingest into Spotify",
                                fontFamily = SpotifyMixFontFamily,
                                color = SpotifyColors.lightGrey,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        itemsIndexed(searchResults, key = { index, item -> "${item.videoId.ifEmpty { "yt" }}_$index" }) { _, item ->
                            val progressText = ingestingMap[item.videoId]
                            val isDone = ingestedMap[item.videoId] == true

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                if (item.thumbnailUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = item.thumbnailUrl,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(SpotifyColors.surface)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(SpotifyColors.surface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = SpotifyColors.lightGrey)
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        fontFamily = SpotifyMixFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.artist,
                                        fontFamily = SpotifyMixFontFamily,
                                        color = SpotifyColors.lightGrey,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.durationText.isNotEmpty()) {
                                        Text(
                                            text = item.durationText,
                                            fontFamily = SpotifyMixFontFamily,
                                            color = Color(0xFF888888),
                                            fontSize = 11.sp
                                        )
                                    }
                                    val itemMatches = remember(item.title, allSongs) {
                                        SongDuplicateMatcher.findMatchesInLibrary(item.title, allSongs)
                                    }
                                    if (itemMatches.isNotEmpty()) {
                                        Text(
                                            text = "⚠️ In Library (${itemMatches.first().first.title})",
                                            fontFamily = SpotifyMixFontFamily,
                                            fontSize = 11.sp,
                                            color = Color(0xFFFFB300),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Ingest Button or Animated Progress
                                if (isDone) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(SpotifyColors.green.copy(alpha = 0.2f))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = SpotifyColors.green, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Added", color = SpotifyColors.green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else if (progressText != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = SpotifyColors.green, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(progressText, color = SpotifyColors.green, fontSize = 10.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (selectedPlaylistId.isNullOrEmpty()) {
                                                Toast.makeText(
                                                    context,
                                                    "Please select a target playlist first",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                isPlaylistDropdownExpanded = true
                                                return@Button
                                            }

                                            val matches = SongDuplicateMatcher.findMatchesInLibrary(item.title, allSongs)
                                            if (matches.isNotEmpty()) {
                                                pendingIngestItem = item
                                                ingestDuplicateMatches = matches
                                                showIngestDuplicateDialog = true
                                            } else {
                                                executeIngest(item)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SpotifyColors.green,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text("Ingest", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }
                }
            } else {
                // ── TAB 2: BATCH CSV INGESTION ─────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Paste song list (one per line, e.g. \"Song Title, Artist\"):",
                        fontFamily = SpotifyMixFontFamily,
                        color = SpotifyColors.lightGrey,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = csvInput,
                        onValueChange = { csvInput = it },
                        placeholder = {
                            Text("Blinding Lights, The Weeknd\nLevitating, Dua Lipa\nStay, The Kid LAROI", color = Color(0xFF666666), fontSize = 13.sp)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E1E1E),
                            unfocusedContainerColor = Color(0xFF1E1E1E),
                            focusedBorderColor = SpotifyColors.green,
                            unfocusedBorderColor = Color(0xFF333333),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (selectedPlaylistId.isNullOrEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Please select a target playlist first before batch ingesting",
                                    Toast.LENGTH_SHORT
                                ).show()
                                isPlaylistDropdownExpanded = true
                                return@Button
                            }

                            val entries = ingestionService.parseCsv(csvInput)
                            if (entries.isEmpty()) {
                                Toast.makeText(context, "Please enter valid tracks", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            csvEntries.clear()
                            entries.forEach { csvEntries.add(Pair(it, "Pending")) }

                            isProcessingCsv = true
                            coroutineScope.launch {
                                for (idx in csvEntries.indices) {
                                    val entry = csvEntries[idx].first
                                    csvEntries[idx] = Pair(entry, "Processing...")
                                    val results = ingestionService.searchYouTube("${entry.title} ${entry.artist}")
                                    if (results.isNotEmpty()) {
                                        val first = results.first()
                                        val res = ingestionService.ingestTrack(
                                            title = entry.title,
                                            artist = entry.artist,
                                            videoId = first.videoId,
                                            thumbnailUrl = first.thumbnailUrl,
                                            playlistId = selectedPlaylistId
                                        )
                                        csvEntries[idx] = Pair(entry, if (res.success) "Done" else "Failed")
                                    } else {
                                        csvEntries[idx] = Pair(entry, "Not Found")
                                    }
                                }
                                isProcessingCsv = false
                                Toast.makeText(context, "Batch ingestion completed!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isProcessingCsv && csvInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyColors.green,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = if (isProcessingCsv) "Processing..." else "Ingest All Songs",
                            fontFamily = SpotifyMixFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(csvEntries) { (entry, status) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(entry.artist, color = SpotifyColors.lightGrey, fontSize = 12.sp, maxLines = 1)
                                }
                                Text(
                                    text = status,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (status) {
                                        "Done" -> SpotifyColors.green
                                        "Processing..." -> Color(0xFFFFB300)
                                        "Failed", "Not Found" -> Color(0xFFFF5252)
                                        else -> SpotifyColors.lightGrey
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Backend Settings Dialog matching Flutter ──────────────────────────────
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backend Connection", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Choose connection preset for ingestion service:",
                        color = SpotifyColors.lightGrey,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset 1: Permanent Ngrok Tunnel
                    Button(
                        onClick = {
                            ingestionService.backendUrl = IngestionService.DEFAULT_TUNNEL_URL
                            customUrlInput = IngestionService.DEFAULT_TUNNEL_URL
                            showSettingsDialog = false
                            coroutineScope.launch {
                                isCheckingStatus = true
                                isOnline = ingestionService.checkStatus()
                                isCheckingStatus = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (ingestionService.backendUrl == IngestionService.DEFAULT_TUNNEL_URL) SpotifyColors.green.copy(alpha = 0.2f) else Color(0xFF282828)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("Ngrok Tunnel (Permanent Default)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(IngestionService.DEFAULT_TUNNEL_URL.removePrefix("https://"), color = SpotifyColors.lightGrey, fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset 2: Local Wi-Fi
                    Button(
                        onClick = {
                            ingestionService.backendUrl = IngestionService.LOCAL_WIFI_URL
                            customUrlInput = IngestionService.LOCAL_WIFI_URL
                            showSettingsDialog = false
                            coroutineScope.launch {
                                isCheckingStatus = true
                                isOnline = ingestionService.checkStatus()
                                isCheckingStatus = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (ingestionService.backendUrl == IngestionService.LOCAL_WIFI_URL) SpotifyColors.green.copy(alpha = 0.2f) else Color(0xFF282828)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("Local Home Wi-Fi (${IngestionService.LOCAL_WIFI_URL.removePrefix("http://")})", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Fast Direct LAN Connection", color = SpotifyColors.lightGrey, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Or enter custom URL:", color = SpotifyColors.lightGrey, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customUrlInput,
                        onValueChange = { customUrlInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ingestionService.backendUrl = customUrlInput
                    showSettingsDialog = false
                    coroutineScope.launch {
                        isCheckingStatus = true
                        isOnline = ingestionService.checkStatus()
                        isCheckingStatus = false
                    }
                }) {
                    Text("Save", color = SpotifyColors.green, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel", color = SpotifyColors.lightGrey)
                }
            }
        )
    }

    // Search Duplicate Found Warning Dialog (Prompt before searching)
    if (showSearchDuplicateDialog && pendingSearchQuery != null) {
        val query = pendingSearchQuery!!
        AlertDialog(
            onDismissRequest = {
                showSearchDuplicateDialog = false
                pendingSearchQuery = null
            },
            containerColor = Color(0xFF202020),
            shape = RoundedCornerShape(14.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Duplicate Found in Library",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "The song you searched (\"$query\") matches ${searchDuplicateMatches.size} existing track(s) already in your database:",
                        fontFamily = SpotifyMixFontFamily,
                        fontSize = 13.sp,
                        color = SpotifyColors.lightGrey
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 170.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(searchDuplicateMatches.take(5)) { (matchedSong, matchResult) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (matchedSong.imageUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = matchedSong.imageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = matchedSong.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = matchedSong.artist.ifEmpty { "Unknown Artist" },
                                        color = SpotifyColors.lightGrey,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (matchResult.matchedWords.isNotEmpty()) {
                                        Text(
                                            text = "Matched word: " + matchResult.matchedWords.joinToString(", ") { "\"$it\"" },
                                            color = SpotifyColors.green,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Do you want to continue searching YouTube to ingest, or cancel?",
                        fontFamily = SpotifyMixFontFamily,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val q = pendingSearchQuery
                        showSearchDuplicateDialog = false
                        pendingSearchQuery = null
                        if (q != null) {
                            triggerSearch(true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyColors.green)
                ) {
                    Text("Continue Search", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSearchDuplicateDialog = false
                    pendingSearchQuery = null
                }) {
                    Text("Cancel", color = SpotifyColors.lightGrey, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        )
    }

    // Ingest Button Duplicate Warning Dialog (Prompt before downloading track)
    if (showIngestDuplicateDialog && pendingIngestItem != null) {
        val target = pendingIngestItem!!
        AlertDialog(
            onDismissRequest = {
                showIngestDuplicateDialog = false
                pendingIngestItem = null
            },
            containerColor = Color(0xFF202020),
            shape = RoundedCornerShape(14.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Duplicate Song Detected",
                        fontFamily = SpotifyMixFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "\"${target.title}\" shares similar words with ${ingestDuplicateMatches.size} existing track(s) in your database:",
                        fontFamily = SpotifyMixFontFamily,
                        fontSize = 13.sp,
                        color = SpotifyColors.lightGrey
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 170.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(ingestDuplicateMatches.take(5)) { (matchedSong, matchResult) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (matchedSong.imageUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = matchedSong.imageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = matchedSong.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = matchedSong.artist.ifEmpty { "Unknown Artist" },
                                        color = SpotifyColors.lightGrey,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (matchResult.matchedWords.isNotEmpty()) {
                                        Text(
                                            text = "Matched word: " + matchResult.matchedWords.joinToString(", ") { "\"$it\"" },
                                            color = SpotifyColors.green,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Do you want to continue ingesting this duplicate or cancel the operation?",
                        fontFamily = SpotifyMixFontFamily,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showIngestDuplicateDialog = false
                        pendingIngestItem = null
                        executeIngest(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyColors.green)
                ) {
                    Text("Continue Ingest", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showIngestDuplicateDialog = false
                    pendingIngestItem = null
                }) {
                    Text("Cancel", color = SpotifyColors.lightGrey, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        )
    }
}
