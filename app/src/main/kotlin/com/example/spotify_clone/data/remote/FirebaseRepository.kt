package com.example.spotify_clone.data.remote

import android.content.Context
import android.util.Log
import com.example.spotify_clone.data.local.AppDatabase
import com.example.spotify_clone.data.local.CachedSongEntity
import com.example.spotify_clone.data.models.Playlist
import com.example.spotify_clone.data.models.Song
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class TopArtistEntry(
    val artist: String,
    val totalPlays: Int,
    val songs: List<Song>,
    val imageUrl: String
)

class FirebaseRepository(private val context: Context) {
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database = AppDatabase.getDatabase(context)
    private val songDao = database.songDao()
    private val ingestionService = IngestionService(context)
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    val currentUserId: String? get() = auth.currentUser?.uid
    val isLoggedIn: Boolean get() = auth.currentUser != null
    val currentUserName: String get() = auth.currentUser?.displayName ?: ""

    suspend fun signInAnonymously(): Result<FirebaseUser?> = runCatching {
        auth.signInAnonymously().await().user
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser?> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await().user
    }

    suspend fun signUpWithEmail(email: String, password: String): Result<FirebaseUser?> = runCatching {
        auth.createUserWithEmailAndPassword(email, password).await().user
    }

    suspend fun updateUserProfile(displayName: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: return@runCatching
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()
        user.updateProfile(profileUpdates).await()
        firestore.collection("users").document(user.uid).set(
            mapOf("name" to displayName, "email" to (user.email ?: "")),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    fun signOut() {
        auth.signOut()
    }

    private val _allSongsFlow = MutableStateFlow<List<Song>>(emptyList())
    val allSongsFlow: StateFlow<List<Song>> = _allSongsFlow.asStateFlow()

    init {
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.d("FirebaseRepository", "Anonymous auth succeeded: ${it.user?.uid}")
                }
                .addOnFailureListener { e ->
                    Log.w("FirebaseRepository", "Anonymous auth warning: ${e.message}")
                }
        }

        // 1. Immediately emit Room cache (0ms instant startup)
        repositoryScope.launch {
            try {
                val local = songDao.getAllSongs().firstOrNull()?.map { it.toSong() } ?: emptyList()
                if (local.isNotEmpty()) {
                    val sorted = local.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                    _allSongsFlow.value = sorted
                }
            } catch (e: Exception) {
                Log.w("FirebaseRepository", "Room cache read error: ${e.message}")
            }
        }

        // 2. Single shared Firestore listener for entire app lifecycle
        firestore.collection("tracks")
            .addSnapshotListener { snapshot: QuerySnapshot?, error: FirebaseFirestoreException? ->
                if (error != null) {
                    Log.w("FirebaseRepository", "Firestore tracks listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val songs = snapshot.documents.mapNotNull { doc: DocumentSnapshot ->
                        runCatching { Song.fromFirestore(doc) }.getOrNull()
                    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

                    // Content equality check: If IDs and titles match current cache, DO NOT emit
                    // to prevent unnecessary recompositions and flickering in UI
                    val current = _allSongsFlow.value
                    val isIdentical = current.size == songs.size && current.indices.all { i ->
                        current[i].id == songs[i].id && current[i].title == songs[i].title
                    }

                    if (!isIdentical) {
                        _allSongsFlow.value = songs
                    }

                    repositoryScope.launch {
                        try {
                            songDao.insertSongs(songs.map { CachedSongEntity.fromSong(it) })
                        } catch (e: Exception) {
                            Log.w("FirebaseRepository", "Room cache write error: ${e.message}")
                        }
                    }
                }
            }
    }

    /**
     * Shared live stream of all tracks in Firestore.
     * Backed by single StateFlow so all screens and collections share the same instant, warm state.
     */
    fun streamAllSongs(): Flow<List<Song>> = allSongsFlow

    /**
     * Raw live stream of playlists from Firestore.
     */
    fun streamRawPlaylists(): Flow<List<Playlist>> = callbackFlow {
        val listener = firestore.collection("playlists")
            .orderBy("name")
            .addSnapshotListener { snapshot: QuerySnapshot?, error: FirebaseFirestoreException? ->
                if (error != null) {
                    Log.w("FirebaseRepository", "Firestore playlists error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val playlists = snapshot.documents.mapNotNull { doc: DocumentSnapshot ->
                        runCatching { Playlist.fromFirestore(doc) }.getOrNull()
                    }
                    trySend(playlists)
                }
            }
        awaitClose { listener.remove() }
    }.flowOn(Dispatchers.IO)

    /**
     * Live stream of all user and curated playlists with tracks resolved for cover art.
     */
    fun streamPlaylists(): Flow<List<Playlist>> = combine(
        streamRawPlaylists(),
        streamAllSongs()
    ) { playlists, allSongs ->
        val trackMap = allSongs.associateBy { it.id }
        playlists.map { pl ->
            val resolved = pl.trackIds.mapNotNull { trackMap[it] }
            pl.copy(tracks = resolved)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Live stream of tracks for a given playlist.
     */
    fun streamPlaylistSongs(playlistId: String): Flow<List<Song>> {
        if (playlistId == "all_songs") {
            return allSongsFlow
        }
        return callbackFlow {

        val listener = firestore.collection("playlists").document(playlistId)
            .addSnapshotListener { doc: DocumentSnapshot?, error: FirebaseFirestoreException? ->
                if (error != null) {
                    Log.w("FirebaseRepository", "Firestore playlist error: ${error.message}")
                    return@addSnapshotListener
                }
                if (doc == null || !doc.exists()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val playlist = Playlist.fromFirestore(doc)
                repositoryScope.launch {
                    val resolved = resolveTrackIds(playlist.trackIds)
                    trySend(resolved)
                }
            }
        awaitClose { listener.remove() }
    }.flowOn(Dispatchers.IO)
    }

    /**
     * Top tracks based on play count for Spotify Wrapped.
     */
    fun streamTopTracks(limit: Int = 10): Flow<List<Pair<Song, Int>>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users").document(uid).collection("playCounts")
            .orderBy("count", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    return@addSnapshotListener
                }
                repositoryScope.launch {
                    val pairs = mutableListOf<Pair<Song, Int>>()
                    val trackIds = snapshot.documents.map { it.id }
                    val resolved = resolveTrackIds(trackIds).associateBy { it.id }
                    for (doc in snapshot.documents) {
                        val count = (doc.get("count") as? Number)?.toInt() ?: 0
                        val song = resolved[doc.id]
                        if (song != null) {
                            pairs.add(Pair(song, count))
                        }
                    }
                    trySend(pairs)
                }
            }
        awaitClose { listener.remove() }
    }.flowOn(Dispatchers.IO)

    /**
     * Top artists derived from play counts.
     */
    fun streamTopArtists(limit: Int = 5): Flow<List<TopArtistEntry>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users").document(uid).collection("playCounts")
            .orderBy("count", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    return@addSnapshotListener
                }
                repositoryScope.launch {
                    val trackIds = snapshot.documents.map { it.id }
                    val resolved = resolveTrackIds(trackIds).associateBy { it.id }
                    val artistMap = mutableMapOf<String, Pair<Int, MutableList<Song>>>()

                    for (doc in snapshot.documents) {
                        val count = (doc.get("count") as? Number)?.toInt() ?: 0
                        val song = resolved[doc.id] ?: continue
                        val artistName = song.artist
                        if (artistName.isEmpty() || artistName == "Unknown Artist") continue

                        val existing = artistMap.getOrPut(artistName) { Pair(0, mutableListOf()) }
                        artistMap[artistName] = Pair(existing.first + count, existing.second.apply { add(song) })
                    }

                    val result = artistMap.entries.sortedByDescending { it.value.first }.take(limit).map { entry ->
                        val songs = entry.value.second
                        val imageUrl = songs.firstOrNull { it.imageUrl.isNotEmpty() }?.imageUrl ?: ""
                        TopArtistEntry(
                            artist = entry.key,
                            totalPlays = entry.value.first,
                            songs = songs,
                            imageUrl = imageUrl
                        )
                    }
                    trySend(result)
                }
            }
        awaitClose { listener.remove() }
    }.flowOn(Dispatchers.IO)

    /**
     * Liked songs set
     */
    fun streamLikedSongIds(): Flow<Set<String>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users").document(uid).collection("likedSongs")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val ids = snapshot.documents.map { it.id }.toSet()
                trySend(ids)
            }
        awaitClose { listener.remove() }
    }.flowOn(Dispatchers.IO)

    suspend fun toggleLikeSong(songId: String, isCurrentlyLiked: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = firestore.collection("users").document(uid).collection("likedSongs").document(songId)
        if (isCurrentlyLiked) {
            docRef.delete().await()
        } else {
            docRef.set(mapOf("likedAt" to FieldValue.serverTimestamp())).await()
        }
    }

    suspend fun fetchTracksByAlbum(album: String): List<Song> {
        if (album.isEmpty()) return emptyList()
        return try {
            val snapshot = firestore.collection("tracks")
                .whereEqualTo("album", album)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                runCatching { Song.fromFirestore(doc) }.getOrNull()
            }.sortedBy { it.trackNumber }
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "fetchTracksByAlbum error: ${e.message}")
            emptyList()
        }
    }

    suspend fun resolveTrackIds(trackIds: List<String>): List<Song> {
        if (trackIds.isEmpty()) return emptyList()

        val inMemoryMap = _allSongsFlow.value.associateBy { it.id }
        val resolvedMap = mutableMapOf<String, Song>()
        val unresolvedIds = mutableListOf<String>()

        for (id in trackIds) {
            val s = inMemoryMap[id]
            if (s != null) {
                resolvedMap[id] = s
            } else {
                unresolvedIds.add(id)
            }
        }

        if (unresolvedIds.isEmpty()) {
            return trackIds.mapNotNull { resolvedMap[it] }
        }

        val localCached = songDao.getSongsByIds(unresolvedIds).associateBy { it.id }
        for ((k, v) in localCached) {
            resolvedMap[k] = v.toSong()
        }
        val missingIds = unresolvedIds.filter { !localCached.containsKey(it) }

        if (missingIds.isNotEmpty()) {
            missingIds.chunked(30).forEach { chunk ->
                try {
                    val snap = firestore.collection("tracks")
                        .whereIn(FieldPath.documentId(), chunk)
                        .get()
                        .await()
                    snap.documents.forEach { doc: DocumentSnapshot ->
                        Song.fromFirestore(doc).let { song ->
                            resolvedMap[song.id] = song
                        }
                    }
                } catch (e: Exception) {
                    Log.w("FirebaseRepository", "Failed to resolve track chunk: ${e.message}")
                }
            }
        }

        return trackIds.mapNotNull { resolvedMap[it] }
    }

    suspend fun createPlaylist(name: String, trackIds: List<String> = emptyList()): String {
        val docRef = firestore.collection("playlists").document()
        val data = hashMapOf(
            "name" to name,
            "description" to "",
            "imageUrl" to "",
            "owner" to (auth.currentUser?.displayName ?: "User"),
            "likes" to 0,
            "trackIds" to trackIds,
            "isSpotifyOwned" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(data).await()
        return docRef.id
    }

    suspend fun addTrackToPlaylist(playlistId: String, songId: String) {
        firestore.collection("playlists").document(playlistId)
            .update("trackIds", FieldValue.arrayUnion(songId))
            .await()
    }

    suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        firestore.collection("playlists").document(playlistId)
            .update("trackIds", FieldValue.arrayUnion(*trackIds.toTypedArray()))
            .await()
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, songId: String) {
        firestore.collection("playlists").document(playlistId)
            .update("trackIds", FieldValue.arrayRemove(songId))
            .await()
    }

    suspend fun removeTracksFromPlaylist(playlistId: String, trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        firestore.collection("playlists").document(playlistId)
            .update("trackIds", FieldValue.arrayRemove(*trackIds.toTypedArray()))
            .await()
    }

    suspend fun reorderPlaylistTracks(playlistId: String, trackIds: List<String>) {
        firestore.collection("playlists").document(playlistId)
            .update("trackIds", trackIds)
            .await()
    }

    suspend fun updatePlaylistDetails(playlistId: String, name: String, description: String) {
        firestore.collection("playlists").document(playlistId)
            .update(mapOf("name" to name, "description" to description))
            .await()
    }

    suspend fun deletePlaylist(playlistId: String) {
        firestore.collection("playlists").document(playlistId).delete().await()
    }

    suspend fun incrementPlayCount(songId: String) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            val userPlayRef = firestore.collection("users").document(uid).collection("playCounts").document(songId)
            userPlayRef.set(mapOf("count" to FieldValue.increment(1)), com.google.firebase.firestore.SetOptions.merge())
        }
        firestore.collection("tracks").document(songId)
            .update("playCount", FieldValue.increment(1))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RECENTLY PLAYED & SEARCH HISTORY & PLAYLIST ORDER
    // ══════════════════════════════════════════════════════════════════════════

    suspend fun recordRecentlyPlayed(songId: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).collection("recentlyPlayed").document(songId)
            .set(mapOf("trackId" to songId, "playedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
    }

    suspend fun removeRecentlyPlayed(songId: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).collection("recentlyPlayed").document(songId).delete()
    }

    suspend fun clearRecentlyPlayed() {
        val uid = auth.currentUser?.uid ?: return
        val snap = firestore.collection("users").document(uid).collection("recentlyPlayed").get().await()
        val batch = firestore.batch()
        for (doc in snap.documents) {
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }

    fun streamRecentlyPlayed(): Flow<List<Song>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(uid).collection("recentlyPlayed")
            .orderBy("playedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(15)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val trackIds = snapshot.documents.map { it.id }
                launch {
                    val resolved = resolveTrackIds(trackIds)
                    val map = resolved.associateBy { it.id }
                    val sorted = trackIds.mapNotNull { map[it] }
                    trySend(sorted)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun addRecentSearch(songId: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(uid).collection("recentSearches").document(songId)
                .set(mapOf("trackId" to songId, "searchedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                .await()
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepo", "Error adding recent search: ${e.message}")
        }
    }

    suspend fun removeRecentSearch(songId: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(uid).collection("recentSearches").document(songId)
                .delete()
                .await()
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepo", "Error removing recent search: ${e.message}")
        }
    }

    suspend fun clearRecentSearches() {
        val uid = auth.currentUser?.uid ?: return
        val snap = firestore.collection("users").document(uid).collection("recentSearches").get().await()
        val batch = firestore.batch()
        for (doc in snap.documents) {
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }

    fun streamRecentSearches(): Flow<List<Song>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(uid).collection("recentSearches")
            .orderBy("searchedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val trackIds = snapshot.documents.map { it.id }
                launch {
                    val resolved = resolveTrackIds(trackIds)
                    val map = resolved.associateBy { it.id }
                    val sorted = trackIds.mapNotNull { map[it] }
                    trySend(sorted)
                }
            }
        awaitClose { listener.remove() }
    }

    private val prefs = context.getSharedPreferences("spotify_clone_prefs", Context.MODE_PRIVATE)

    fun getCachedPlaylistOrder(): List<String> {
        val raw = prefs.getString("playlist_order", null) ?: return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun setCachedPlaylistOrder(order: List<String>) {
        prefs.edit().putString("playlist_order", order.joinToString(",")).apply()
    }

    fun streamPlaylistOrder(): Flow<List<String>> = callbackFlow {
        val cached = getCachedPlaylistOrder()
        if (cached.isNotEmpty()) {
            trySend(cached)
        }
        val uid = auth.currentUser?.uid
        if (uid == null) {
            if (cached.isEmpty()) trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (cached.isEmpty()) trySend(emptyList())
                    return@addSnapshotListener
                }
                val order = (snapshot?.get("playlistOrder") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                if (order.isNotEmpty()) {
                    setCachedPlaylistOrder(order)
                }
                trySend(order)
            }
        awaitClose { listener.remove() }
    }

    suspend fun savePlaylistOrder(order: List<String>) {
        setCachedPlaylistOrder(order)
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(uid).set(
                mapOf("playlistOrder" to order),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            Log.d("FirebaseRepository", "Successfully saved playlistOrder (${order.size} items) to Firestore")
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Failed to save playlistOrder to Firestore: ${e.message}")
        }
    }

    suspend fun deleteTrack(trackId: String) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            // 1. Delete from Firestore tracks collection immediately
            firestore.collection("tracks").document(trackId).delete().await()

            // 2. Delete from local SQLite Room cache
            songDao.deleteSongById(trackId)

            // 3. Remove references from all playlists asynchronously
            repositoryScope.launch {
                try {
                    val playlistsSnap = firestore.collection("playlists").get().await()
                    val batch = firestore.batch()
                    for (doc in playlistsSnap.documents) {
                        val tIds = (doc.get("trackIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                        if (tIds.contains(trackId)) {
                            batch.update(doc.reference, "trackIds", FieldValue.arrayRemove(trackId))
                            batch.update(doc.reference, "track_ids", FieldValue.arrayRemove(trackId))
                            batch.update(doc.reference, "songIds", FieldValue.arrayRemove(trackId))
                        }
                    }
                    batch.commit().await()
                } catch (e: Exception) {
                    Log.w("FirebaseRepository", "Playlist reference cleanup warning: ${e.message}")
                }

                try {
                    ingestionService.deleteTrack(trackId)
                } catch (e: Exception) {
                    Log.w("FirebaseRepository", "Backend deleteTrack warning: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Failed to delete track $trackId: ${e.message}")
            throw e
        }
    }
}
