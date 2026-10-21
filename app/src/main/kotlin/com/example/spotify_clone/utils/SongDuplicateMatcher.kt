package com.example.spotify_clone.utils

import com.example.spotify_clone.data.models.Song

data class MatchResult(
    val isMatch: Boolean,
    val matchedWords: List<String> = emptyList(),
    val reason: String = ""
)

data class DuplicateGroup(
    val songs: List<Song>,
    val matchedWords: List<String>
)

object SongDuplicateMatcher {

    /**
     * Stop words and ingestion noise words that should not trigger a duplicate match on their own.
     * Contains English grammatical particles and common YouTube/audio descriptors.
     */
    val STOP_WORDS: Set<String> = hashSetOf(
        // Common grammatical English words
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "with", "by", "from",
        "of", "is", "it", "as", "be", "this", "that", "are", "was", "were", "will", "would",
        "into", "over", "after", "under", "about", "your", "my", "his", "her", "its", "our", "their",
        "you", "he", "she", "they", "we", "me", "him", "them", "us", "am", "so", "no", "not",
        // Ingestion & YouTube noise tags
        "song", "songs", "video", "audio", "official", "music", "lyrics", "lyric", "visualizer",
        "ost", "soundtrack", "theme", "original", "version", "mix", "remix", "edit", "remastered",
        "hd", "4k", "8k", "full", "part", "teaser", "trailer", "promo", "short", "shorts",
        "feat", "ft", "featuring", "prod", "cover", "live", "session", "performance", "episode",
        "ep", "lofi", "slowed", "reverb", "bass", "boosted", "high", "quality", "mp3", "mp4",
        "stream", "release", "exclusive", "drop", "premiere", "director", "cut", "special", "bgm",
        "album", "records", "record", "label", "audio track", "music video"
    )

    /**
     * Cleans text by removing brackets, punctuation, and extra whitespace.
     */
    fun cleanText(text: String): String {
        return text.lowercase()
            .replace('[', ' ')
            .replace(']', ' ')
            .replace('(', ' ')
            .replace(')', ' ')
            .replace('{', ' ')
            .replace('}', ' ')
            .replace('|', ' ')
            .replace('-', ' ')
            .replace('_', ' ')
            .replace('/', ' ')
            .replace('\\', ' ')
            .replace(':', ' ')
            .replace(';', ' ')
            .replace(',', ' ')
            .replace('.', ' ')
            .replace('!', ' ')
            .replace('?', ' ')
            .replace('"', ' ')
            .replace('\'', ' ')
            .replace('`', ' ')
            .replace('~', ' ')
            .replace('@', ' ')
            .replace('#', ' ')
            .replace('$', ' ')
            .replace('%', ' ')
            .replace('^', ' ')
            .replace('&', ' ')
            .replace('*', ' ')
            .replace('+', ' ')
            .replace('=', ' ')
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Extracts significant alphanumeric words (length >= 3, not in STOP_WORDS, containing letters).
     */
    fun extractSignificantWords(text: String): Set<String> {
        val cleaned = cleanText(text)
        if (cleaned.isBlank()) return emptySet()

        return cleaned.split(" ")
            .map { it.trim() }
            .filter { token ->
                token.length >= 3 &&
                token !in STOP_WORDS &&
                token.any { it.isLetter() }
            }
            .toSet()
    }

    /**
     * Checks if two song titles match according to strict word-level or containment similarity.
     */
    fun checkTitleSimilarity(title1: String, title2: String): MatchResult {
        val clean1 = cleanText(title1)
        val clean2 = cleanText(title2)

        if (clean1.isNotEmpty() && clean1 == clean2) {
            return MatchResult(
                isMatch = true,
                matchedWords = listOf(clean1),
                reason = "Exact title match"
            )
        }

        // Substring containment for meaningful phrases
        if (clean1.length >= 4 && clean2.length >= 4) {
            if (clean1.contains(clean2)) {
                return MatchResult(
                    isMatch = true,
                    matchedWords = listOf(clean2),
                    reason = "Title contains \"$clean2\""
                )
            }
            if (clean2.contains(clean1)) {
                return MatchResult(
                    isMatch = true,
                    matchedWords = listOf(clean1),
                    reason = "Title contains \"$clean1\""
                )
            }
        }

        val words1 = extractSignificantWords(title1)
        val words2 = extractSignificantWords(title2)

        val intersection = words1.intersect(words2)
        if (intersection.isNotEmpty()) {
            val matchedList = intersection.sorted()
            return MatchResult(
                isMatch = true,
                matchedWords = matchedList,
                reason = "Matched word(s): " + matchedList.joinToString(", ") { "\"$it\"" }
            )
        }

        return MatchResult(isMatch = false)
    }

    /**
     * Finds all duplicate clusters in the provided library using Disjoint Set Union (DSU).
     * Strictly groups songs if even a single significant title word matches.
     */
    fun findDuplicatesInLibrary(songs: List<Song>): List<DuplicateGroup> {
        if (songs.size < 2) return emptyList()

        val n = songs.size
        val parent = IntArray(n) { it }

        fun find(i: Int): Int {
            var root = i
            while (root != parent[root]) {
                root = parent[root]
            }
            var curr = i
            while (curr != root) {
                val next = parent[curr]
                parent[curr] = root
                curr = next
            }
            return root
        }

        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) {
                parent[rootI] = rootJ
            }
        }

        // Inverted index: word -> list of song indices
        val wordIndex = mutableMapOf<String, MutableList<Int>>()
        val cleanTitleIndex = mutableMapOf<String, MutableList<Int>>()

        for (i in songs.indices) {
            val song = songs[i]
            val cleaned = cleanText(song.title)
            if (cleaned.isNotEmpty()) {
                cleanTitleIndex.getOrPut(cleaned) { mutableListOf() }.add(i)
            }
            val words = extractSignificantWords(song.title)
            for (w in words) {
                wordIndex.getOrPut(w) { mutableListOf() }.add(i)
            }
        }

        // Union exact cleaned matches
        for ((_, indices) in cleanTitleIndex) {
            if (indices.size > 1) {
                val first = indices[0]
                for (k in 1 until indices.size) {
                    union(first, indices[k])
                }
            }
        }

        // Union matching significant words
        for ((_, indices) in wordIndex) {
            if (indices.size > 1) {
                val first = indices[0]
                for (k in 1 until indices.size) {
                    union(first, indices[k])
                }
            }
        }

        // Group songs by parent root
        val clusters = mutableMapOf<Int, MutableList<Song>>()
        for (i in songs.indices) {
            val root = find(i)
            clusters.getOrPut(root) { mutableListOf() }.add(songs[i])
        }

        // Only return clusters that contain more than 1 song
        return clusters.values
            .filter { it.size > 1 }
            .map { groupSongs ->
                // Calculate all matched significant words across this group
                val allGroupWords = groupSongs.flatMap { extractSignificantWords(it.title) }
                val wordCounts = allGroupWords.groupingBy { it }.eachCount()
                val sharedWords = wordCounts.filter { it.value > 1 }.keys.sorted()
                DuplicateGroup(
                    songs = groupSongs,
                    matchedWords = if (sharedWords.isNotEmpty()) sharedWords else listOf(cleanText(groupSongs.first().title))
                )
            }
            .sortedByDescending { it.songs.size }
    }

    /**
     * Checks a target query/title against existing songs in the database.
     * Returns all matching songs and their match reasons.
     */
    fun findMatchesInLibrary(targetTitle: String, allSongs: List<Song>): List<Pair<Song, MatchResult>> {
        if (targetTitle.isBlank() || allSongs.isEmpty()) return emptyList()

        val results = mutableListOf<Pair<Song, MatchResult>>()
        for (song in allSongs) {
            val match = checkTitleSimilarity(targetTitle, song.title)
            if (match.isMatch) {
                results.add(Pair(song, match))
            }
        }
        return results
    }
}
