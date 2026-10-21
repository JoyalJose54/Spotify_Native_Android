package com.example.spotify_clone.utils

import com.example.spotify_clone.data.models.Playlist

object PlaylistOrderUtils {

    private val defaultOrder = listOf(
        "🥹🫶🏻",
        "🥹🫶",
        "🫶🏻🫠",
        "🫶🫠",
        "⚡️⚡️",
        "⚡⚡",
        "QiCFfgk2kGBVXFQfOGBU",
        "🫠 🥹",
        "🫠🥹",
        "Sangeeetham!!",
        "🏋️‍♀️🏋️‍♀️"
    )

    fun getPlaylistOrderIndex(playlist: Playlist, userOrder: List<String>): Int {
        if (userOrder.isNotEmpty()) {
            val idx = userOrder.indexOfFirst { o ->
                o.trim().equals(playlist.id.trim(), ignoreCase = true) ||
                o.trim().equals(playlist.name.trim(), ignoreCase = true)
            }
            if (idx != -1) return idx
        } else {
            val idx = defaultOrder.indexOfFirst { o ->
                o.trim().equals(playlist.id.trim(), ignoreCase = true) ||
                o.trim().equals(playlist.name.trim(), ignoreCase = true)
            }
            if (idx != -1) return idx
        }
        return 9999
    }

    fun sortPlaylistsByOrder(playlists: List<Playlist>, order: List<String>): List<Playlist> {
        return playlists.sortedWith(
            compareBy<Playlist> { getPlaylistOrderIndex(it, order) }
                .thenBy { it.name.lowercase() }
        )
    }
}
