package com.musicpipe.player.ui.netsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicpipe.player.data.model.NetSong
import com.musicpipe.player.di.AppContainer
import com.musicpipe.player.util.GatewayUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Net search over GET /api/v1/search — queries up to four platforms (kw/kg/tx/wy)
 * and can enqueue any result into the router download queue (POST /api/v1/download).
 * Raw song JsonObjects are kept inside NetSong for loss-less round-trip.
 */
class NetSearchViewModel(private val container: AppContainer) : ViewModel() {

    data class PlatformGroup(
        val platform: String,
        val name: String,
        val ok: Boolean,
        val error: String?,
        val songs: List<NetSong>
    )

    data class UiState(
        val query: String = "",
        val selectedPlatforms: Set<String> = emptySet(),
        val quality: String = "auto",
        val searching: Boolean = false,
        val loadingMore: Boolean = false,
        val page: Int = 1,
        val groups: List<PlatformGroup> = emptyList(),
        val searched: Boolean = false,
        val error: String? = null,
        val toast: String? = null,
        /** 正在下载的曲目 key；详情弹窗用它显示进度，连续下载时不会互相打架。 */
        val downloadingKeys: Set<String> = emptySet(),
        /** 详情弹窗：null 表示未打开。 */
        val detail: NetSong? = null,
        /** 详情弹窗选中的音质（默认取全局音质）。 */
        val detailQuality: String = "auto",
        /** 「全部下载」进行中，用于禁用按钮、避免重复触发多轮入队。 */
        val downloadingAll: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val pageLimit = 20

    init {
        _state.value = _state.value.copy(quality = container.prefs.downloadQualityNow())
    }

    fun onQueryChange(q: String) { _state.value = _state.value.copy(query = q) }

    fun consumeToast() { _state.value = _state.value.copy(toast = null) }

    fun togglePlatform(code: String) {
        val cur = _state.value.selectedPlatforms
        _state.value = _state.value.copy(
            selectedPlatforms = if (code in cur) cur - code else cur + code
        )
    }

    /**
     * 音质选择写入 prefs —— 旧实现只改内存，
     * 切到「下载任务」再回来就跳回"自动"，用户无从判断实际用了什么音质。
     */
    fun setQuality(q: String) {
        _state.value = _state.value.copy(quality = q)
        viewModelScope.launch { container.prefs.setDownloadQuality(q) }
    }

    fun openDetail(song: NetSong) {
        _state.value = _state.value.copy(detail = song, detailQuality = _state.value.quality)
    }

    fun closeDetail() { _state.value = _state.value.copy(detail = null) }

    fun setDetailQuality(q: String) { _state.value = _state.value.copy(detailQuality = q) }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty() || _state.value.searching) return
        _state.value = _state.value.copy(searching = true, error = null, page = 1)
        viewModelScope.launch {
            try {
                val resp = container.netRepository.netSearch(
                    q = q, page = 1, limit = pageLimit,
                    platforms = _state.value.selectedPlatforms.toList()
                )
                _state.value = _state.value.copy(
                    searching = false, searched = true, error = null,
                    groups = resp.results.map { r ->
                        PlatformGroup(r.platform, r.name, r.ok, r.error, r.songs.map { NetSong(it) })
                    }
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(searching = false, searched = true, error = t.message ?: "搜索失败")
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.searching || s.loadingMore || s.query.isBlank() || s.groups.isEmpty()) return
        val next = s.page + 1
        _state.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            try {
                val resp = container.netRepository.netSearch(
                    q = s.query.trim(), page = next, limit = pageLimit,
                    platforms = s.selectedPlatforms.toList()
                )
                val merged = s.groups.toMutableList()
                for (r in resp.results) {
                    val idx = merged.indexOfFirst { it.platform == r.platform }
                    val songs = r.songs.map { NetSong(it) }
                    if (idx >= 0) {
                        val g = merged[idx]
                        val seen = g.songs.map { it.key }.toSet()
                        merged[idx] = g.copy(songs = g.songs + songs.filter { it.key !in seen })
                    } else {
                        merged.add(PlatformGroup(r.platform, r.name, r.ok, r.error, songs))
                    }
                }
                _state.value = _state.value.copy(loadingMore = false, page = next, groups = merged)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loadingMore = false, toast = t.message ?: "加载更多失败")
            }
        }
    }

    fun download(song: NetSong) = download(song, _state.value.quality)

    /**
     * 入队下载。v1.6.0：
     * - 同一首曲目重复点击会被去重（旧实现每次点击都发一个任务，连点十次就是十个任务，
     *   之后的同时下载并发才是"连续下载后闪退"的诱因之一）；
     * - quality 明确传入，详情弹窗可以用与全局不同的音质下载。
     */
    fun download(song: NetSong, quality: String) {
        val key = song.key
        if (key in _state.value.downloadingKeys) return
        val label = song.name.ifBlank { "该歌曲" }
        _state.value = _state.value.copy(downloadingKeys = _state.value.downloadingKeys + key)
        viewModelScope.launch {
            try {
                enqueueSlots.acquire()
                val ack = container.netRepository.download(listOf(song.json), quality)
                _state.value = _state.value.copy(
                    downloadingKeys = _state.value.downloadingKeys - key,
                    detail = _state.value.detail.takeIf { it?.key != key },
                    toast = if (ack.ok) "已加入下载队列：" + label + "（" + GatewayUtil.qualityLabel(ack.quality) + "）"
                    else "加入下载队列失败"
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    downloadingKeys = _state.value.downloadingKeys - key,
                    toast = t.message ?: "加入下载队列失败"
                )
            } finally {
                enqueueSlots.release()
            }
        }
    }

    /**
     * v1.6.0 需求2：把"连续下载"变成有节奏的排队，而不是十几条并发。
     *
     * 用户连点时旧实现会同时发出十几个 POST /download，路由器那边会同时起十几个下载任务，
     * 把 WebDAV 与局域网打满；期间任何一条连接被重置，都会让 App 侧出现空闲连接上的
     * 未捕获异常（闪退）。现在限制在 [MAX_PARALLEL_ENQUEUE] 路，其余在本地排队，
     * 界面上能看到"还有 N 首待入队"。
     */
    private val enqueueSlots = kotlinx.coroutines.sync.Semaphore(MAX_PARALLEL_ENQUEUE)

    /**
     * 「全部下载」。
     *
     * v1.6.0 需求2 修复两个问题：
     * 1. **绕过限流**：旧实现直接一次 POST 把搜索结果里所有歌（最多 4 平台 × 20 条 = 80 首）
     *    塞进一个请求，既不走 [enqueueSlots]，也不分批 —— 单个 JSON 请求体可达数百 KB，
     *    序列化与网络同时冲高，成绩就是"连续下载后闪退"。现在按 [DOWNLOAD_BATCH] 首
     *    分片、逐片过信号量，且每片之间让出一次调度。
     * 2. **一首失败全批失败**：旧实现任一异常就整批放弃（用户看到的是"加入失败"，
     *    实际前面已经入队了一部分）。现在逐片容错，最后汇总"成功 N 首，失败 M 首"。
     */
    fun downloadAll() {
        val all = _state.value.groups.flatMap { it.songs }
        if (all.isEmpty() || _state.value.downloadingAll) return
        val quality = _state.value.quality
        _state.value = _state.value.copy(downloadingAll = true)
        viewModelScope.launch {
            var ok = 0
            var fail = 0
            try {
                all.chunked(DOWNLOAD_BATCH).forEach { batch ->
                    enqueueSlots.acquire()
                    try {
                        val ack = container.netRepository.download(batch.map { it.json }, quality)
                        if (ack.ok) ok += ack.taskIds.size.coerceAtLeast(batch.size) else fail += batch.size
                    } catch (t: Throwable) {
                        fail += batch.size
                    } finally {
                        enqueueSlots.release()
                    }
                    // 让出一次：连续几十个请求时避免把主线程调度饿死（界面卡住也会被当成闪退）。
                    kotlinx.coroutines.yield()
                }
            } finally {
                _state.value = _state.value.copy(
                    downloadingAll = false,
                    toast = if (fail == 0) "已加入 " + ok + " 个下载任务"
                    else "已加入 " + ok + " 首，失败 " + fail + " 首（可稍后重试）"
                )
            }
        }
    }

    companion object {
        /** 同时最多几路入队请求；再多只会把路由器打满，对用户没有收益。 */
        const val MAX_PARALLEL_ENQUEUE = 3
        /** 「全部下载」每个请求最多带几首：分片降低单请求体大小与内存峰值。 */
        const val DOWNLOAD_BATCH = 10

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NetSearchViewModel(container) as T
        }
    }
}