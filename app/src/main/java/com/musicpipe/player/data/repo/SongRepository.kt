package com.musicpipe.player.data.repo

import com.musicpipe.player.data.local.LibrarySnapshot
import com.musicpipe.player.data.local.LibrarySnapshotDao
import com.musicpipe.player.data.model.Song
import com.musicpipe.player.data.prefs.PrefsRepository
import com.musicpipe.player.data.remote.MusicPipeApi
import com.musicpipe.player.data.remote.dto.TracksResponse
import com.musicpipe.player.data.remote.unwrap
import com.musicpipe.player.util.SongMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Library browse / search via the router-merged gateway (docs/路由器整合报告.md §5):
 * GET /api/v1/library?q=&offset=&limit= -> {total, offset, limit, tracks:[trackBrief]}.
 * There is no random endpoint, so recommendations fetch a big page and shuffle locally.
 *
 * **v1.7.0 首屏秒开**：冷启动时首页必须等一次 library 往返才能画出内容，期间是转圈。
 * 现在每次成功加载都会把曲库落盘（[LibrarySnapshotDao]），启动时先用快照渲染，
 * 网络结果回来后再覆盖 —— 用户看到的是"一开就有"，数据仍然是最新的。
 */
class SongRepository(
    private val api: MusicPipeApi,
    private val prefs: PrefsRepository,
    private val snapshotDao: LibrarySnapshotDao
) {

    companion object {
        /** Gateway clamps limit to 500; keep the client honest too. */
        const val MAX_LIMIT = 500
        /** Recommendation pool: first N tracks, shuffled client-side. */
        const val RECOMMEND_POOL = 200
        const val SEARCH_LIMIT = 200
    }

    private suspend fun token(): String = prefs.authToken.first()

    suspend fun getLibrary(query: String? = null, offset: Int = 0, limit: Int = RECOMMEND_POOL): TracksResponse =
        api.library(
            q = query?.takeIf { it.isNotBlank() },
            offset = offset.coerceAtLeast(0),
            limit = limit.coerceIn(1, MAX_LIMIT)
        ).unwrap()

    suspend fun songs(query: String? = null, offset: Int = 0, limit: Int = RECOMMEND_POOL): List<Song> {
        val tracks = getLibrary(query, offset, limit).tracks
        if (tracks.isEmpty()) return emptyList()
        val token = token()
        return tracks.map { SongMapper.fromTrack(it, token) }
    }

    /**
     * 完整曲库加载（首页用）：网络 + 落盘快照。
     *
     * 只对"完整曲库"（无查询、offset=0、limit=MAX_LIMIT）写快照 —— 搜索结果/分页片段写进快照
     * 会把首页数据弄乱。
     */
    suspend fun loadLibrary(): List<Song> {
        val list = songs(limit = MAX_LIMIT)
        saveSnapshot(list)
        return list
    }

    /** 上次成功加载的曲库快照；没有快照时返回空（UI 仍走原有的 loading 态）。 */
    suspend fun cachedLibrary(): List<Song> = withContext(Dispatchers.IO) {
        runCatching {
            val rows = snapshotDao.all()
            if (rows.isEmpty()) return@runCatching emptyList()
            val token = token()
            rows.map { row ->
                SongMapper.fromCached(
                    songId = row.songId,
                    title = row.title,
                    artist = row.artist,
                    album = row.album,
                    durationMs = row.durationMs,
                    rawCover = row.coverPath,
                    rawStream = row.streamPath,
                    platform = row.platform,
                    token = token
                )
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun saveSnapshot(songs: List<Song>) = withContext(Dispatchers.IO) {
        runCatching {
            if (songs.isEmpty()) return@runCatching
            snapshotDao.replaceAll(
                songs.mapIndexed { index, song ->
                    LibrarySnapshot(
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        durationMs = song.durationMs,
                        // 存相对路径：绝对 URL 里的 token 会过期，读回时要按当前 token 重签
                        coverPath = SongMapper.coverPath(song.id),
                        streamPath = SongMapper.streamPath(song.id),
                        platform = song.platform,
                        position = index
                    )
                }
            )
        }
        Unit
    }

    /** 清空快照（退出登录时调用，避免换账号看到上一个人的曲库）。 */
    suspend fun clearSnapshot() = withContext(Dispatchers.IO) {
        runCatching { snapshotDao.clear() }
        Unit
    }

    /** Home recommendation = first [RECOMMEND_POOL] tracks of the library, shuffled, take [count]. */
    suspend fun getRandomSongs(count: Int = 30): List<Song> =
        songs(limit = RECOMMEND_POOL).shuffled().take(count.coerceAtLeast(1))

    /** Search over title/artist/album (server-side LIKE matching). */
    suspend fun search(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        return songs(query = query, limit = SEARCH_LIMIT)
    }

    /** Rebuild a cached history/favourites row into a playable Song (paths re-signed). */
    suspend fun fromCached(
        songId: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        coverRaw: String?,
        streamRaw: String?,
        platform: String
    ): Song = SongMapper.fromCached(
        songId = songId,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        rawCover = coverRaw,
        rawStream = streamRaw,
        platform = platform,
        token = token()
    )
}
