package com.musicpipe.player.data.repo

import com.musicpipe.player.data.local.LyricsCache
import com.musicpipe.player.data.local.LyricsCacheDao
import com.musicpipe.player.data.model.LyricLine
import com.musicpipe.player.data.model.Song
import com.musicpipe.player.data.remote.ApiException
import com.musicpipe.player.data.remote.MusicPipeApi
import com.musicpipe.player.util.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException

/**
 * Lyrics for one track: GET /api/v1/lyrics/<trackId> (Bearer).
 *
 * Response: {id,title,artist,synced,lyrics,lines:[{start(ms),line}]}.
 *   - lines non-empty  -> mapped 1:1 onto [LyricLine] (start is already milliseconds).
 *   - synced=false, or lines empty while lyrics is non-empty -> parse the raw LRC text
 *     locally with the existing [LrcParser].
 *   - nothing at all / 404 -> [LyricsResult.NotFound]; the UI degrades gracefully.
 *
 * v1.5.0 缓存：服务端已经修好了"每首歌只抓一次"（冷 600-700ms → 热 2ms），但 App 每次进
 * 歌词页仍会发一次网络请求。这里加一层进程内 LRU：
 *   - 命中：0ms，切歌/来回进退歌词页不再有任何等待。
 *   - 未命中：照旧请求；成功才入缓存（NotFound/Error 不缓存，避免"没写歌词"被判死刑）。
 *
 * **v1.7.0 离线缓存**：内存 LRU 只活到进程结束 —— 冷启动后每首歌都要等一次网络往返，
 * 而"点开一首歌"正是用户最在意的那一下。现在多加一级 SQLite 落盘（[LyricsCacheDao]）：
 *
 *   内存 LRU (0ms)  →  SQLite (通常 1~3ms)  →  网络 (58~1600ms)
 *
 * 落盘只存 Found，规则同上（NotFound/Error 不缓存）。读 SQLite 与写 SQLite 都在 IO 线程，
 * 不占主线程也不占 UI 协程的调度时间。
 */
class LyricsRepository(
    private val api: MusicPipeApi,
    private val cacheDao: LyricsCacheDao
) {
    sealed class LyricsResult {
        data class Found(val lines: List<LyricLine>, val source: String) : LyricsResult()
        /** No lyrics available — UI must show 「未匹配到歌词」 banner, playback unaffected. */
        data object NotFound : LyricsResult()
        /** Network / server error — UI shows error banner; playback continues. */
        data class Error(val message: String) : LyricsResult()
    }

    /** trackId -> 解析后的行（只缓存 Found，NotFound/Error 不缓存）。 */
    private val cache = object : LinkedHashMap<String, List<LyricLine>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LyricLine>>) =
            size > CACHE_MAX
    }

    suspend fun fetch(song: Song): LyricsResult {
        // L1：内存
        synchronized(cache) { cache[song.id] }?.let { return LyricsResult.Found(it, "cache") }

        // L2：SQLite 落盘缓存
        readDisk(song.id)?.let { lines ->
            synchronized(cache) { cache[song.id] = lines }
            return LyricsResult.Found(lines, "disk")
        }

        // L3：网络
        return fetchRemote(song).also { result ->
            if (result is LyricsResult.Found) {
                synchronized(cache) { cache[song.id] = result.lines }
                writeDisk(song.id, result)
            }
        }
    }

    /**
     * 预热：把将要播放/已缓存的歌提前读进内存 LRU。
     *
     * 播放器切歌时会调 [fetch]，但那时用户已经在等 UI 了；预热在 IO 线程上把 SQLite 里的
     * 行读进内存，让随后的 [fetch] 命中 L1（0ms）。批量处理，单首失败不影响其它。
     */
    suspend fun prewarm(songIds: List<String>) = withContext(Dispatchers.IO) {
        songIds.forEach { id ->
            if (synchronized(cache) { cache.containsKey(id) }) return@forEach
            readDiskUnsafe(id)?.let { lines -> synchronized(cache) { cache[id] = lines } }
        }
    }

    /** 清空缓存（退出登录时调用，避免换账号看到上一个账号的歌词）。 */
    suspend fun clear() {
        synchronized(cache) { cache.clear() }
        withContext(Dispatchers.IO) { runCatching { cacheDao.clear() } }
    }

    /** 已落盘的歌词条数（设置页/诊断用）。 */
    suspend fun cachedCount(): Int = withContext(Dispatchers.IO) {
        runCatching { cacheDao.count() }.getOrDefault(0)
    }

    private suspend fun readDisk(songId: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        readDiskUnsafe(songId)
    }

    private suspend fun readDiskUnsafe(songId: String): List<LyricLine>? = runCatching {
        cacheDao.find(songId)?.let { decodeLines(it.linesJson) }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    private suspend fun writeDisk(songId: String, result: LyricsResult.Found) = withContext(Dispatchers.IO) {
        runCatching {
            cacheDao.put(
                LyricsCache(
                    songId = songId,
                    linesJson = encodeLines(result.lines),
                    source = result.source,
                    cachedAt = System.currentTimeMillis()
                )
            )
        }
        Unit
    }

    private suspend fun fetchRemote(song: Song): LyricsResult = try {
        val response = api.lyrics(song.id)
        if (response.isSuccessful) {
            val dto = response.body()
            val fromLines = dto?.lines.orEmpty()
                .mapNotNull { line ->
                    val text = line.line?.trim().orEmpty()
                    if (text.isEmpty()) null else LyricLine(timeMs = line.start, text = text)
                }
            val fromLrc = if (dto?.synced == true && fromLines.isNotEmpty()) {
                emptyList()
            } else {
                LrcParser.parse(dto?.lyrics)
            }
            when {
                fromLines.isNotEmpty() -> LyricsResult.Found(fromLines, "gateway-lines")
                fromLrc.isNotEmpty() -> LyricsResult.Found(fromLrc, "gateway-lrc")
                else -> LyricsResult.NotFound
            }
        } else if (response.code() == 404) {
            LyricsResult.NotFound
        } else {
            LyricsResult.Error(ApiException.from(response).message)
        }
    } catch (e: ApiException) {
        if (e.httpStatus == 404) LyricsResult.NotFound else LyricsResult.Error(e.message)
    } catch (_: IOException) {
        LyricsResult.Error("歌词服务不可用")
    } catch (t: Throwable) {
        LyricsResult.Error(t.message ?: "歌词服务不可用")
    }

    private companion object {
        const val CACHE_MAX = 500

        /**
         * 行数组的紧凑序列化：[["timeMs","text"], ...]。
         * 用 org.json（Android 自带，无新依赖）；文本里可能带引号/换行，交给它转义。
         */
        fun encodeLines(lines: List<LyricLine>): String {
            val arr = JSONArray()
            lines.forEach { line ->
                arr.put(JSONArray().put(line.timeMs).put(line.text))
            }
            return arr.toString()
        }

        fun decodeLines(json: String): List<LyricLine> {
            val arr = JSONArray(json)
            val out = ArrayList<LyricLine>(arr.length())
            for (i in 0 until arr.length()) {
                val pair = arr.optJSONArray(i) ?: continue
                out += LyricLine(timeMs = pair.optLong(0), text = pair.optString(1))
            }
            return out
        }
    }
}
