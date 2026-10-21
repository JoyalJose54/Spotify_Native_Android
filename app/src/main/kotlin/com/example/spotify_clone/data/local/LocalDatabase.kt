package com.example.spotify_clone.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.spotify_clone.data.models.Song
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_songs")
data class CachedSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val imageUrl: String,
    val streamUrl: String,
    val durationMs: Long,
    val playCount: Long
) {
    fun toSong() = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        imageUrl = imageUrl,
        streamUrl = streamUrl,
        durationMs = durationMs,
        playCount = playCount
    )

    companion object {
        fun fromSong(song: Song) = CachedSongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            imageUrl = song.imageUrl,
            streamUrl = song.streamUrl,
            durationMs = song.durationMs,
            playCount = song.playCount
        )
    }
}

@Dao
interface SongDao {
    @Query("SELECT * FROM cached_songs ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongs(): Flow<List<CachedSongEntity>>

    @Query("SELECT * FROM cached_songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<String>): List<CachedSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<CachedSongEntity>)

    @Query("DELETE FROM cached_songs WHERE id = :songId")
    suspend fun deleteSongById(songId: String)

    @Query("DELETE FROM cached_songs")
    suspend fun clear()
}

@Database(entities = [CachedSongEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spotify_cache.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
