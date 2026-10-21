package com.example.spotify_clone.data.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class YouTubeSearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationText: String
)

data class IngestionResult(
    val success: Boolean,
    val message: String,
    val trackId: String = "",
    val error: String? = null,
    val isDuplicate: Boolean = false
)

data class CsvEntry(
    val title: String,
    val artist: String,
    val playlistName: String = ""
)

class IngestionService(private val context: Context) {
    companion object {
        const val CLOUDFLARE_URL = "https://your-tunnel.trycloudflare.com"
        const val LOCAL_WIFI_URL = "http://10.0.2.2:8080"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences("ingestion_prefs", Context.MODE_PRIVATE)

    var backendUrl: String
        get() {
            val saved = prefs.getString("backend_url", null)
            if (saved.isNullOrBlank()) {
                return CLOUDFLARE_URL
            }
            return saved
        }
        set(value) {
            var clean = value.trim()
            if (clean.isNotEmpty() && !clean.startsWith("http://") && !clean.startsWith("https://")) {
                clean = "https://$clean"
            }
            clean = clean.removeSuffix("/")
            prefs.edit().putString("backend_url", clean).apply()
        }

    suspend fun checkStatus(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$backendUrl/ping")
                .header("ngrok-skip-browser-warning", "true")
                .header("User-Agent", "SpotifyClone/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.w("IngestionService", "ping failed: ${e.message}")
            false
        }
    }

    suspend fun searchYouTube(query: String): List<YouTubeSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("$backendUrl/search?q=$encodedQuery")
                .header("ngrok-skip-browser-warning", "true")
                .header("User-Agent", "SpotifyClone/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                
                val array = if (body.trim().startsWith("[")) {
                    JSONArray(body)
                } else {
                    val obj = JSONObject(body)
                    obj.optJSONArray("results") ?: JSONArray()
                }

                val results = mutableListOf<YouTubeSearchResult>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rawVid = obj.optString("video_id")
                        .takeIf { it.isNotEmpty() }
                        ?: obj.optString("videoId")
                            .takeIf { it.isNotEmpty() }
                        ?: obj.optString("id")
                            .takeIf { it.isNotEmpty() }
                        ?: "yt_result_$i"

                    val title = obj.optString("title", "Unknown Title")
                    val artist = obj.optString("artist")
                        .takeIf { it.isNotEmpty() }
                        ?: obj.optString("channel")
                            .takeIf { it.isNotEmpty() }
                        ?: obj.optString("uploader", "Unknown Artist")

                    val thumb = obj.optString("thumbnail_url")
                        .takeIf { it.isNotEmpty() }
                        ?: obj.optString("thumbnail")
                            .takeIf { it.isNotEmpty() }
                        ?: obj.optString("cover_url", "")

                    val durSec = if (obj.has("duration_sec")) {
                        obj.optInt("duration_sec", 0)
                    } else {
                        obj.optInt("duration", 0)
                    }

                    val durText = if (durSec > 0) {
                        val m = durSec / 60
                        val s = durSec % 60
                        "%d:%02d".format(m, s)
                    } else {
                        obj.optString("duration", "")
                    }

                    results.add(
                        YouTubeSearchResult(
                            videoId = rawVid,
                            title = title,
                            artist = artist,
                            thumbnailUrl = thumb,
                            durationText = durText
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            Log.w("IngestionService", "searchYouTube error: ${e.message}")
            emptyList()
        }
    }

    suspend fun ingestTrack(
        title: String,
        artist: String,
        videoId: String,
        thumbnailUrl: String,
        playlistId: String? = null,
        onProgressUpdate: ((String) -> Unit)? = null
    ): IngestionResult = withContext(Dispatchers.IO) {
        try {
            onProgressUpdate?.invoke("Checking library...")
            val json = JSONObject().apply {
                put("title", title)
                put("artist", artist)
                put("video_id", videoId)
                put("thumbnail_url", thumbnailUrl)
                if (!playlistId.isNullOrEmpty()) {
                    put("playlist_id", playlistId)
                }
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toString().toRequestBody(mediaType)

            onProgressUpdate?.invoke("Downloading audio...")
            val request = Request.Builder()
                .url("$backendUrl/ingest")
                .header("ngrok-skip-browser-warning", "true")
                .header("User-Agent", "SpotifyClone/1.0")
                .post(requestBody)
                .build()

            onProgressUpdate?.invoke("Uploading audio...")
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val resJson = runCatching { JSONObject(bodyStr) }.getOrNull()
                    val trackId = resJson?.optString("track_id", "") ?: ""
                    val isDup = resJson?.optBoolean("duplicate", false) == true
                    onProgressUpdate?.invoke("Saved to library!")
                    IngestionResult(
                        success = true,
                        message = if (isDup) "Already in library" else "Added to library!",
                        trackId = trackId,
                        isDuplicate = isDup
                    )
                } else {
                    val errJson = runCatching { JSONObject(bodyStr) }.getOrNull()
                    val errMsg = errJson?.optString("error", "HTTP ${response.code}") ?: "HTTP ${response.code}"
                    IngestionResult(success = false, message = errMsg, error = errMsg)
                }
            }
        } catch (e: Exception) {
            IngestionResult(success = false, message = "Network error: ${e.message}", error = e.message)
        }
    }

    fun parseCsv(csv: String): List<CsvEntry> {
        val lines = csv.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val entries = mutableListOf<CsvEntry>()
        val startIdx = if (lines.first().lowercase().contains("title") || lines.first().lowercase().contains("track")) 1 else 0

        for (i in startIdx until lines.size) {
            val parts = lines[i].split(",").map { it.trim().trim('"', '\'') }
            if (parts.isNotEmpty()) {
                val title = parts.getOrNull(0) ?: ""
                val artist = parts.getOrNull(1) ?: "Unknown Artist"
                val plName = parts.getOrNull(2) ?: ""
                if (title.isNotBlank()) {
                    entries.add(CsvEntry(title = title, artist = artist, playlistName = plName))
                }
            }
        }
        return entries
    }

    suspend fun deleteTrack(trackId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().put("track_id", trackId)
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$backendUrl/delete")
                .post(body)
                .build()
            client.newCall(req).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.w("IngestionService", "deleteTrack network error: ${e.message}")
            false
        }
    }
}
