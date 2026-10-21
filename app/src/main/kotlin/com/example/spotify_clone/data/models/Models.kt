package com.example.spotify_clone.data.models

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

@Immutable
data class Song(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val artistId: String = "",
    val album: String = "",
    val albumId: String = "",
    val imageUrl: String = "",
    val streamUrl: String = "", // Cloudinary secure_url
    val durationMs: Long = 0L,
    val isExplicit: Boolean = false,
    val trackNumber: Int = 1,
    val videoId: String = "",
    val playCount: Long = 0L,
    val createdAt: Date? = null
) {
    companion object {
        fun fromFirestore(doc: DocumentSnapshot): Song {
            val data = doc.data ?: emptyMap<String, Any>()

            val streamUrl = (data["secure_url"] as? String)
                ?: (data["audioUrl"] as? String)
                ?: (data["audio_url"] as? String)
                ?: (data["url"] as? String)
                ?: ""

            val imageUrl = (data["imageUrl"] as? String)
                ?: (data["image_url"] as? String)
                ?: (data["thumbnail_url"] as? String)
                ?: (data["thumbnail"] as? String)
                ?: (data["coverUrl"] as? String)
                ?: ""

            val durationMs = when (val dur = data["duration_ms"] ?: data["durationMs"]) {
                is Number -> dur.toLong()
                is String -> dur.toLongOrNull() ?: 0L
                else -> 0L
            }

            val playCount = when (val count = data["playCount"] ?: data["plays"]) {
                is Number -> count.toLong()
                is String -> count.toLongOrNull() ?: 0L
                else -> 0L
            }

            val trackNumber = when (val num = data["trackNumber"] ?: data["track_number"]) {
                is Number -> num.toInt()
                else -> 1
            }

            return Song(
                id = doc.id,
                title = data["title"] as? String ?: "Unknown Title",
                artist = data["artist"] as? String ?: "Unknown Artist",
                artistId = data["artistId"] as? String ?: "",
                album = data["album"] as? String ?: "",
                albumId = data["albumId"] as? String ?: "",
                imageUrl = imageUrl,
                streamUrl = streamUrl,
                durationMs = durationMs,
                isExplicit = data["isExplicit"] as? Boolean ?: false,
                trackNumber = trackNumber,
                videoId = data["videoId"] as? String ?: "",
                playCount = playCount,
                createdAt = (data["createdAt"] as? Timestamp)?.toDate()
            )
        }
    }
}

@Immutable
data class Playlist(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val owner: String = "",
    val likes: Int = 0,
    val trackIds: List<String> = emptyList(),
    val tracks: List<Song> = emptyList(),
    val isSpotifyOwned: Boolean = false,
    val createdAt: Date? = null
) {
    companion object {
        fun fromFirestore(doc: DocumentSnapshot, resolvedTracks: List<Song> = emptyList()): Playlist {
            val data = doc.data ?: emptyMap<String, Any>()

            val rawTrackIds = when (val ids = data["trackIds"] ?: data["track_ids"] ?: data["songIds"]) {
                is List<*> -> ids.mapNotNull { it?.toString() }
                else -> emptyList()
            }

            var rawOwner = data["owner"] as? String ?: ""
            if ((rawOwner.length >= 20 && !rawOwner.contains(" ")) || rawOwner == "local_user") {
                rawOwner = ""
            }

            return Playlist(
                id = doc.id,
                name = data["name"] as? String ?: "Untitled Playlist",
                description = data["description"] as? String ?: "",
                imageUrl = data["imageUrl"] as? String ?: "",
                owner = rawOwner,
                likes = (data["likes"] as? Number)?.toInt() ?: 0,
                trackIds = rawTrackIds,
                tracks = resolvedTracks,
                isSpotifyOwned = data["isSpotifyOwned"] as? Boolean ?: false,
                createdAt = (data["createdAt"] as? Timestamp)?.toDate()
            )
        }
    }

    val trackCount: Int get() = if (tracks.isNotEmpty()) tracks.size else trackIds.size
}

@Immutable
data class Artist(
    val id: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val followers: Long = 0L,
    val genres: List<String> = emptyList()
) {
    companion object {
        fun fromFirestore(doc: DocumentSnapshot): Artist {
            val data = doc.data ?: emptyMap<String, Any>()
            val genresList = (data["genres"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            return Artist(
                id = doc.id,
                name = data["name"] as? String ?: "",
                imageUrl = data["imageUrl"] as? String ?: "",
                followers = (data["followers"] as? Number)?.toLong() ?: 0L,
                genres = genresList
            )
        }
    }
}
