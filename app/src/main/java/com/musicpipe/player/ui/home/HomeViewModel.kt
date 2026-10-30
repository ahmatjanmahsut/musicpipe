package com.musicpipe.player.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicpipe.player.data.model.Song
import com.musicpipe.player.data.repo.SongRepository
import com.musicpipe.player.di.AppContainer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        /**
         * v1.15.0：本地快照是否已经读过。
         *
         * 冷启动转圈的直接原因：refresh() 在网络请求**之前**就无条件把 loading 置 true，
         * 而这时快照还没读出来（另一条协程在跑），hasContent 仍为 false →
         * 首页门（loading && !hasContent）必然先命中，计数器一直转到网络返回。
         *
         * 现在用这个字段区分"还没看过本地"和"本地已空、在等网络"：
         * 只有前者才允许显示循环计数器；后者显示静止的同步态。
         */
        val bootstrapped: Boolean = false,
        /** Full router library, newest first (GET /api/v1/library). */
        val songs: List<Song> = emptyList(),
        /** One section per share playlist, in share-creation order. */
        val collections: List<HomeCollection> = emptyList(),
        val error: String? = null,
        val manageMode: Boolean = false,
        val selected: Set<String> = emptySet(),
        val deleting: Boolean = false,
        /**
         * v1.5.0 文件夹式首页：为 null 时显示"文件夹网格"总览；非 null 时显示该文件夹内部
         * 的曲目列表。用一个可空字段做二级页面，而不是新增 NavHost 路由 —— 这样
         * 「返回」天然由 Compose 的 BackHandler 处理，也不会污染顶层的 tab 导航栈。
         */
        val openFolderId: String? = null,
        /**
         * v1.6.0：预先算好的网格表格。**必须缓存**——旧实现在 folders 的 getter 里每次
         * 读取都调用一次 HomeFolders.build()，而网格每次组合/滚动都会读它：
         * 68 首曲库意味着每个滚动帧都在重新分组、拼 subtitle、抽封面 URL，
         * 这是首页上下滑动不流畅的直接原因（分配压力导致 GC 抖动）。
         */
        val folders: List<HomeFolderGrid> = emptyList(),
        /**
         * v1.9.0：当前展示的轴（歌单 / 专辑）。
         *
         * 首页拆两轴的目的是**杜绝重复展示**：v1.5.0~v1.8.0 把分享歌单与专辑铺在同一个网格里，
         * 当曲库的歌全部来自同一个歌单时，两边的卡片内容是同一批歌（实测 101/101 完全重合），
         * 用户看到的就是"重复了"。同类 App（Apple Music / Spotify / Navidrome / DsoMusic）
         * 都是把这两个轴做成独立入口，一次只展示一个。
         */
        val axis: HomeAxis = HomeAxis.PLAYLISTS,
        /** 歌单轴缓存的表格（永远算好：管理功能与角标数量都要用，即使当前在专辑轴）。 */
        val playlistFolders: List<HomeFolderGrid> = emptyList(),
        /** 专辑轴缓存的表格。 */
        val albumFolders: List<HomeFolderGrid> = emptyList(),
        /** 艺人轴缓存，与歌单/专辑一样在刷新时预计算，滚动期零分组。 */
        val artistFolders: List<HomeFolderGrid> = emptyList(),
        /** 歌单管理模式：网格上叠加删除 / 合并操作。 */
        val playlistManageMode: Boolean = false,
        /** 批量选中的歌单 id（share ids）。 */
        val selectedPlaylists: Set<String> = emptySet(),
        /** 歌单操作进行中（合并/删除）。 */
        val playlistBusy: Boolean = false
    ) {
        /**
         * v1.10.0（P6）：openFolderId -> 文件夹 的索引表。
         *
         * 旧实现是 getter 里 folders.find{...}，O(n) 线性扫描；而 HomeScreen 在组合期
         * 一共读 openFolder 9 次（面板、顶栏、可见性判断、播放全部…），每次重组都扫 9 遍。
         * 这里改成一次性建 map（construction 时付一次代价），读取退化为 O(1)。
         */
        private val folderById: Map<String, HomeFolder> by lazy {
            folders.associate { it.folder.id to it.folder }
        }

        /** 当前打开的文件夹（openFolderId 解析后的数据）。 */
        val openFolder: HomeFolder?
            get() = openFolderId?.let { folderById[it] }

        /** 当前二级页面可见的歌曲（文件夹内 = 该文件夹曲目；总览 = 全曲库）。 */
        val visibleSongs: List<Song>
            get() = openFolder?.songs ?: songs

        /**
         * 可作为合并/删除目标的分享歌单。
         *
         * v1.10.0（P7）：从 getter 改成 lazy 缓存。getter 每次读取都 map 出一个新 List，
         * 而 HomeScreen 原来在组合期反复读（且 memo 的 key 用的是 folders，键值不一致，
         * 切换轴时不重建）。改成 lazy 后同一 UiState 实例永远返回同一个 List 引用，
         * Compose 的 === 比较才能命中跳过。
         *
         * 注意读的是 [playlistFolders] 而不是 [folders]：切到专辑轴时 [folders] 里没有歌单，
         * 但管理条、合并对话框、角标数量仍需要完整的歌单列表。
         */
        val shareFolders: List<HomeFolder> by lazy { playlistFolders.map { it.folder } }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        // v1.7.0 首屏秒开：先把上次的曲库快照画出来（SQLite 读一次通常几毫秒），
        // 网络结果回来后由 refresh() 覆盖。没有快照时行为与以前完全一致（转圈等网络）。
        viewModelScope.launch {
            val cached = container.songRepository.cachedLibrary()
            // 无论有没有快照，读完就把 bootstrapped 置 true —— 这一瞬间之后，
            // 我们"知道本地有什么"了，就没必要再用循环计数器挡屏：
            // 有快照直接渲染（loading 也置回 false，避免误显示下拉刷新指示器），
            // 没快照则交给静止的同步态。
            _state.value = withFolders(
                _state.value.copy(
                    songs = if (cached.isNotEmpty() && _state.value.songs.isEmpty()) cached
                    else _state.value.songs,
                    bootstrapped = true,
                    loading = if (cached.isNotEmpty()) false else _state.value.loading
                )
            )
        }
        refresh()
    }

    /**
     * Library + share playlists in parallel. A failing share endpoint degrades to
     * "no collections" so the library still renders. Results are merged with copy()
     * (not a fresh UiState) so manageMode/selected survive refreshes that complete
     * while the user is managing, and a loading guard dedupes resume/pull triggers.
     *
     * @param showIndicator v1.15.0：是否为这次刷新显示加载指示器。
     *
     * LifecycleResumeEffect 每次回到前台都会调 refresh()，而 refresh 一进来就把
     * loading 置 true —— 首页的 PullToRefreshBox 用 state.loading && hasContent 当
     * isRefreshing，于是每次切回 App 顶部都会转一个刷新圈，即使数据是几秒前刚拉的。
     * 回前台属于顺手对一下，用 silent 刷新：照样拉数据、失败也不弹错误条，
     * 但不动 loading，界面上什么都不会闪。
     */
    /** v1.15.0：silent 刷新不置 loading，原来的 loading guard 就管不到它，另立一个在途标记。 */
    private var refreshInFlight = false

    /** 上次成功拿到曲库的时刻（uptimeMillis）。用于回前台时的节流。 */
    private var lastLoadedAt = 0L

    fun refresh(showIndicator: Boolean = true) {
        if (refreshInFlight || _state.value.loading) return
        // 回前台的 silent 刷新做节流：数据是十几秒内刚拉的就没必要再打一次接口。
        // 用户反复切出切回时（看歌词、回微信），这条会省掉大量无谓请求与重组。
        if (!showIndicator && lastLoadedAt > 0L) {
            val age = android.os.SystemClock.elapsedRealtime() - lastLoadedAt
            if (age < RESUME_REFRESH_MIN_AGE_MS) return
        }
        refreshInFlight = true
        if (showIndicator) {
            _state.value = _state.value.copy(loading = true, error = null)
        }
        viewModelScope.launch {
            val sharesDeferred = async {
                runCatching { container.netRepository.shareList() }.getOrDefault(emptyList())
            }
            try {
                // loadLibrary = songs(MAX_LIMIT) + 落盘快照（下次冷启动秒开用）
                val songs = container.songRepository.loadLibrary()
                val shares = sharesDeferred.await()
                val songsByShare = shares.map { share ->
                    async {
                        share.id to runCatching { container.netRepository.shareSongs(share.id) }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll().toMap()
                val collections = HomeSections.buildCollections(shares, songsByShare, songs)
                _state.value = prunePlaylistSelection(
                    withFolders(
                        _state.value.copy(
                            loading = false,
                            songs = songs,
                            collections = collections,
                            error = null
                        )
                    )
                )
                lastLoadedAt = android.os.SystemClock.elapsedRealtime()
                // v1.7.0 预加载：曲库一到位就在后台把封面与歌词喂进缓存。
                // 顺序按界面里的出现顺序（分享歌单在前，然后曲库），先预热的先被看到。
                val ordered = _state.value.folders.flatMap { it.folder.songs }.distinctBy { it.id }
                container.prewarmer.warm(
                    scope = viewModelScope,
                    covers = ordered.mapNotNull { it.coverUrl },
                    songIds = ordered.map { it.id }
                )
            } catch (t: Throwable) {
                // silent 刷新（回前台自动触发）失败时保持界面原样：用户没主动要求刷新，
                // 弹一条错误横幅只会让人以为出事了；有内容的界面继续显示旧数据即可。
                _state.value = _state.value.copy(
                    loading = false,
                    error = if (showIndicator) (t.message ?: "加载失败") else _state.value.error
                )
            } finally {
                refreshInFlight = false
            }
        }
    }

    /**
     * 重算两轴表格并让 [UiState.folders] 指向当前轴。
     *
     * 两轴表格在 refresh 时各算一次并缓存；切轴只是换个指针，不重新分组
     * （切轴是用户点击、不是滚动帧，但仍然没有必要重复算）。
     */
    private fun withFolders(s: UiState): UiState {
        val playlists = buildFolderGrid(HomeAxis.PLAYLISTS, s.collections, s.songs)
        val albums = buildFolderGrid(HomeAxis.ALBUMS, s.collections, s.songs)
        val artists = buildFolderGrid(HomeAxis.ARTISTS, s.collections, s.songs)
        val axis = HomeFolders.resolveAxis(s.axis, playlists.isNotEmpty(), albums.isNotEmpty(), artists.isNotEmpty())
        return s.copy(
            axis = axis,
            playlistFolders = playlists,
            albumFolders = albums,
            artistFolders = artists,
            folders = when (axis) {
                HomeAxis.PLAYLISTS -> playlists
                HomeAxis.ALBUMS -> albums
                HomeAxis.ARTISTS -> artists
            }
        )
    }

    /**
     * 切换首页轴（歌单 / 专辑）。
     *
     * 切轴会关闭已打开的文件夹并退出歌单管理态 —— 否则用户切到专辑轴时，
     * 管理条会挂在没有可管理卡片的界面上，看着像 bug。
     */
    fun setAxis(axis: HomeAxis) {
        val s = _state.value
        if (s.axis == axis) return
        _state.value = s.copy(
            axis = axis,
            folders = when (axis) {
                HomeAxis.PLAYLISTS -> s.playlistFolders
                HomeAxis.ALBUMS -> s.albumFolders
                HomeAxis.ARTISTS -> s.artistFolders
            },
            openFolderId = null,
            manageMode = false,
            selected = emptySet(),
            playlistManageMode = false,
            selectedPlaylists = emptySet()
        )
    }

    /** 进入某个文件夹（HomeFolder.id）。 */
    fun openFolder(id: String) {
        _state.value = _state.value.copy(openFolderId = id, manageMode = false, selected = emptySet())
    }

    /** 从文件夹返回网格总览。 */
    fun closeFolder() {
        _state.value = _state.value.copy(openFolderId = null, manageMode = false, selected = emptySet())
    }

    fun play(index: Int) {
        val songs = _state.value.songs
        if (index in songs.indices) container.playerRepository.playQueue(songs, index)
    }

    /** Play a whole share-playlist section as the queue. */
    fun playCollection(collectionId: String, index: Int) {
        val col = _state.value.collections.find { it.id == collectionId } ?: return
        if (index in col.songs.indices) container.playerRepository.playQueue(col.songs, index)
    }

    /** 播放某个文件夹的全部（从第 index 首开始）。 */
    fun playFolder(folderId: String, index: Int) {
        // v1.6.0：从缓存的表格里取，不再每次重建（旧实现每次点击都重新分组整个曲库）。
        val folder = _state.value.folders.find { it.id == folderId }?.folder ?: return
        if (index in folder.songs.indices) container.playerRepository.playQueue(folder.songs, index)
    }

    fun toggleManage() {
        val s = _state.value
        _state.value = s.copy(manageMode = !s.manageMode, selected = emptySet())
    }

    fun toggleSelect(id: String) {
        val cur = _state.value.selected
        _state.value = _state.value.copy(selected = if (id in cur) cur - id else cur + id)
    }

    fun selectAll() {
        val s = _state.value
        val visible = s.visibleSongs
        _state.value = s.copy(
            selected = if (s.selected.size == visible.size && visible.isNotEmpty()) emptySet()
            else visible.map { it.id }.toSet()
        )
    }

    /** POST /api/v1/tracks/bulk-delete — removes audio + .lrc + .jpg sidecars on the router disk. */
    fun deleteSelected(onDone: (String?) -> Unit) {
        val s = _state.value
        if (s.selected.isEmpty() || s.deleting) return
        _state.value = s.copy(deleting = true)
        viewModelScope.launch {
            try {
                val n = s.selected.size
                container.netRepository.deleteTracks(s.selected.toList())
                _state.value = _state.value.copy(deleting = false, manageMode = false, selected = emptySet())
                refresh()
                onDone("已删除 " + n + " 首")
            } catch (t: Throwable) {
                _state.value = _state.value.copy(deleting = false)
                onDone(t.message ?: "删除失败")
            }
        }
    }

    // ── v1.6.0：歌单（文件夹）管理：删除 / 合并 ──────────────────────────────
    //
    // 这里的"文件夹"在数据层对应两种东西：
    //   · SHARE 文件夹  ←→ sharelinks 表里的一条分享歌单（可删、可合并）
    //   · ALBUM / 未分类 ← 曲库按专辑/无专辑的投影（不可删，删了等于删歌）
    // 所以管理模式只在有分享歌单时才允许进入；专辑文件夹在管理态下显示为不可选。

    fun togglePlaylistManage() {
        val s = _state.value
        if (s.playlistBusy) return
        _state.value = s.copy(
            playlistManageMode = !s.playlistManageMode,
            selectedPlaylists = emptySet()
        )
    }

    /**
     * 选中/取消选中一个分享歌单。
     *
     * 入参用卡片上的 shareId（= sharelinks.id，如 "mv6kz..."），**不是** 网格内部
     * 用的 "share:" + id 那种带前缀的 folderId —— 服务端接口只认前者，
     * 若把带前缀的值传给 /shares/:id/delete 会 404 且用户只看到"删除失败"。
     */
    fun togglePlaylistSelect(shareId: String) {
        if (!_state.value.playlistManageMode || _state.value.playlistBusy) return
        val cur = _state.value.selectedPlaylists
        _state.value = _state.value.copy(
            selectedPlaylists = if (shareId in cur) cur - shareId else cur + shareId
        )
    }

    /** 数据刷新后把手动选择收敛到仍然存在的歌单上，避免"幽灵选择"。 */
    private fun prunePlaylistSelection(s: UiState): UiState {
        if (s.selectedPlaylists.isEmpty()) return s
        val alive = s.shareFolders.mapNotNull { it.shareId }.toSet()
        return s.copy(selectedPlaylists = s.selectedPlaylists.filter { it in alive }.toSet())
    }

    /**
     * 删除一个分享歌单。
     *
     * 语义与 Web 控制台一致：只删歌单本身（sharelinks + sharelink_items），
     * **不动曲库里已经下载好的音频**——用户删的是"这个歌单入口"，不是"这些歌"。
     * 若想让歌曲也从磁盘消失，进文件夹用歌曲管理模式删除。
     */
    fun deletePlaylist(shareId: String, onDone: (String?) -> Unit) {
        val s = _state.value
        if (s.playlistBusy) return
        _state.value = s.copy(playlistBusy = true)
        viewModelScope.launch {
            try {
                container.netRepository.deleteShare(shareId)
                _state.value = _state.value.copy(
                    playlistBusy = false,
                    playlistManageMode = false,
                    selectedPlaylists = emptySet()
                )
                refresh()
                onDone("已删除歌单")
            } catch (t: Throwable) {
                _state.value = _state.value.copy(playlistBusy = false)
                onDone(t.message ?: "删除歌单失败")
            }
        }
    }

    /** 批量删除选中的分享歌单（逐个删，任一条失败不打断其余，最后汇总）。 */
    fun deleteSelectedPlaylists(onDone: (String?) -> Unit) {
        val s = _state.value
        if (s.playlistBusy || s.selectedPlaylists.isEmpty()) return
        _state.value = s.copy(playlistBusy = true)
        viewModelScope.launch {
            val ids = s.selectedPlaylists.toList()
            var ok = 0
            var fail = 0
            for (id in ids) {
                try {
                    container.netRepository.deleteShare(id)
                    ok++
                } catch (t: Throwable) {
                    fail++
                }
            }
            _state.value = _state.value.copy(
                playlistBusy = false,
                playlistManageMode = false,
                selectedPlaylists = emptySet()
            )
            refresh()
            onDone(
                if (fail == 0) "已删除 " + ok + " 个歌单"
                else "已删除 " + ok + " 个，失败 " + fail + " 个"
            )
        }
    }

    /**
     * 把 [sourceId] 并入 [targetId]。
     *
     * 服务端语义（POST /api/v1/shares/:id/merge，:id 是保留的那个 target）：
     * 源歌单的曲目去重后并入目标，源歌单随后被删除，曲库里的音频不动。
     * 合并方向固定为"选中项 → 列表里第一个没被选中的歌单"这类隐式目标很容易误操作，
     * 所以 UI 会显式让用户选目标，这里只负责执行。
     */
    fun mergePlaylists(targetId: String, sourceIds: List<String>, onDone: (String?) -> Unit) {
        val s = _state.value
        if (s.playlistBusy || sourceIds.isEmpty()) return
        val src = sourceIds.filter { it != targetId }
        if (src.isEmpty()) {
            onDone("至少要选择另一个歌单作为合并来源")
            return
        }
        _state.value = s.copy(playlistBusy = true)
        viewModelScope.launch {
            var moved = 0
            var skipped = 0
            var fail = 0
            for (id in src) {
                try {
                    val r = container.netRepository.mergeShare(targetId, id)
                    moved += r.inserted
                    skipped += r.skipped
                } catch (t: Throwable) {
                    fail++
                }
            }
            _state.value = _state.value.copy(
                playlistBusy = false,
                playlistManageMode = false,
                selectedPlaylists = emptySet()
            )
            refresh()
            onDone(
                when {
                    fail > 0 -> "合并部分失败：成功 " + (src.size - fail) + " 个，失败 " + fail + " 个"
                    else -> "已合并 " + src.size + " 个歌单，新增 " + moved + " 首"
                }
            )
        }
    }

    companion object {
        /** 回前台自动刷新之间的最小间隔：数据比这更新就不重复请求。 */
        const val RESUME_REFRESH_MIN_AGE_MS = 20_000L

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(container) as T
        }
    }
}