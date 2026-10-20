package com.musicpipe.player.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * musicpipe.db v2 — the legacy remote id column was replaced by streamPath
 * (gateway-relative "/api/v1/stream/<id>"); coverUrl likewise stores "/api/v1/cover/<id>".
 * Plain addColumn migration: MIGRATION_1_2 in AppDatabase.
 */
@Entity(tableName = "history_tracks")
data class HistoryTrack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUrl: String?,
    val platform: String = "musicpipe",
    @ColumnInfo(name = "streamPath") val streamPath: String?,
    val playedAt: Long
)

@Entity(tableName = "favorite_tracks")
data class FavoriteTrack(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUrl: String?,
    val platform: String = "musicpipe",
    @ColumnInfo(name = "streamPath") val streamPath: String?,
    val addedAt: Long
)
/**
 * v1.7.0 需求2：歌词离线缓存。
 *
 * 此前 [com.musicpipe.player.data.repo.LyricsRepository] 只有进程内 LRU —— 杀进程重进、
 * 或者开机第一次进歌词页，仍然要等一次网络往返（冷路径 600-1600ms）。用户明确的诉求是
 * "音乐点开要快"，而点开的那一瞬间最长的等待通常就是歌词。
 *
 * 这里落盘，命中即 0 网络。只缓存 Found：
 *  - NotFound 不缓存（"这首歌暂时没歌词"在服务端可能明天就有，缓存了等于判死刑）；
 *  - Error 不缓存（网络问题不该被当成结论）。
 *
 * linesJson 存已解析好的行（[timeMs, text] 数组的 JSON），避免每次都重新解析 LRC 文本；
 * 体积上 500 首 LRC 约 1-2MB，SQLite 完全吃得下。
 */
@Entity(tableName = "lyrics_cache")
data class LyricsCache(
    @PrimaryKey val songId: String,
    val linesJson: String,
    val source: String,
    val cachedAt: Long
)

/**
 * v1.7.0 需求2：曲库快照（首屏秒开）。
 *
 * 冷启动时首页要等 GET /api/v1/library 往返之后才能画出任何东西，期间是一块空白或转圈。
 * 这里把上次成功加载的曲库落盘，下次启动先拿它渲染，网络结果回来再覆盖 —— 用户感觉是
 * "一开就有内容"，实际数据仍然是最新的。
 *
 * 只存静态字段：stream/cover 的绝对 URL 里带着会过期的 token，读取时按当前 token 重新签，
 * 所以这里存的是相对路径（或沿用 SongMapper.fromCached 的规则）。
 */
@Entity(tableName = "library_snapshot")
data class LibrarySnapshot(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverPath: String?,
    val streamPath: String?,
    val platform: String,
    val position: Int
)
