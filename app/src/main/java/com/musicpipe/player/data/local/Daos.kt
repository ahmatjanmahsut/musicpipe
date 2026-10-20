package com.musicpipe.player.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_tracks ORDER BY playedAt DESC")
    fun observeAll(): Flow<List<HistoryTrack>>

    @Query("SELECT * FROM history_tracks WHERE songId = :songId LIMIT 1")
    suspend fun findBySongId(songId: String): HistoryTrack?

    @Insert
    suspend fun insert(track: HistoryTrack): Long

    @Update
    suspend fun update(track: HistoryTrack)

    @Query("DELETE FROM history_tracks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_tracks")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_tracks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteTrack>>

    @Query("SELECT songId FROM favorite_tracks")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tracks WHERE songId = :songId)")
    fun observeIsFavorite(songId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: FavoriteTrack)

    @Query("DELETE FROM favorite_tracks WHERE songId = :songId")
    suspend fun deleteBySongId(songId: String)
}
/**
 * v1.7.0 需求2：歌词离线缓存。
 * 用 INSERT OR REPLACE 做"只增不删"的覆盖写，另外提供按时间清理旧记录的口子
 * （避免用户换服务器后一堆再也命中不到的旧歌词常驻）。
 */
@Dao
interface LyricsCacheDao {
    @Query("SELECT * FROM lyrics_cache WHERE songId = :songId LIMIT 1")
    suspend fun find(songId: String): LyricsCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: LyricsCache)

    @Query("DELETE FROM lyrics_cache")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM lyrics_cache")
    suspend fun count(): Int
}

/**
 * v1.7.0 需求2：曲库快照（首屏秒开）。
 *
 * 写入用"事务里先清空再整批插入"而不是逐条 upsert：曲库会删歌，逐条 upsert 会把已删除的
 * 歌永久留在快照里，用户会看到一首点了就报 404 的幽灵歌曲。整表替换语义最直接。
 */
@Dao
interface LibrarySnapshotDao {
    @Query("SELECT * FROM library_snapshot ORDER BY position ASC")
    suspend fun all(): List<LibrarySnapshot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<LibrarySnapshot>)

    @Query("DELETE FROM library_snapshot")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM library_snapshot")
    suspend fun count(): Int

    @Transaction
    suspend fun replaceAll(rows: List<LibrarySnapshot>) {
        clear()
        if (rows.isNotEmpty()) insertAll(rows)
    }
}