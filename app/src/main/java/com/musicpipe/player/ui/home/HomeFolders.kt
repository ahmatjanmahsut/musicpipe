package com.musicpipe.player.ui.home

import com.musicpipe.player.data.model.Song

/**
 * v1.9.0 主页轴（axis）—— 解决"同一份内容在首页出现两次"。
 *
 * 背景：v1.5.0 把首页做成一个文件夹网格，网格里同时放了两种东西：
 *   · 分享歌单（用户导入的入口，来源是 sharelinks 表）
 *   · 专辑（曲库按 album 字段聚合的投影）
 * 当曲库里的歌全部来自同一个歌单时，这两者内容是**同一批歌**，
 * 于是首页出现两张内容一样的卡片：一张叫歌单名，一张叫专辑名（实际是分享者昵称）。
 *
 * 参考同类 App 的通行做法，歌单与专辑本就不是"同一个网格里的两类卡片"：
 *   · Apple Music 资料库把 播放列表 / 艺术家 / 专辑 / 歌曲 分成各自独立的入口；
 *   · Spotify 首页是各轴（最近播放 / 歌单 / 专辑）的横向分区，资料库里有独立 tab；
 *   · Navidrome 首页是多个横向 carousel（最近添加 / 专辑 / 播放列表）；
 *   · DsoMusic 也是把 歌曲 / 专辑 / 艺术家 / 歌单 分成不同 tab。
 * 共同点：**一次只展示一个轴**，用户想看另一个轴就切换，而不是把两轴铺在一起。
 *
 * 所以这里把网格拆成两个轴，UI 用分段控件切换，同一屏永不出现重复入口。
 */
enum class HomeAxis(val label: String) {
    /** 分享歌单轴：一个分享歌单一张卡片，可管理（删除/合并）。 */
    PLAYLISTS("歌单"),

    /** 专辑轴：曲库按专辑聚合 + 未分类兜底，不可管理（它是曲库投影）。 */
    ALBUMS("专辑"),

    /** 艺人轴：曲库按 artist 聚合，空艺人名归入未知艺人。 */
    ARTISTS("艺人")
}

/**
 * v1.5.0 主页文件夹式布局的数据模型。
 *
 * 用户要求「主页的几个栏目改成文件夹式，不要列表式」。这里把首页看成一层**文件夹网格**：
 *   1. 每个分享歌单 -> 一个文件夹（原有栏目，保留语义与命名）。
 *   2. 曲库本身 -> 按专辑聚合成若干文件夹（专辑是音乐 App 里最自然的"文件夹"粒度，
 *      且与 DsoMusic 的专辑页一致）。
 *   3. 无专辑信息的歌曲汇总成一个「未分类」文件夹，保证任何歌曲都能被找到，不会凭空消失。
 *
 * v1.9.0 起 1 与 2/3 分属两个轴（[HomeAxis]），不再同屏展示。
 *
 * 纯函数、零 Android 依赖 —— 可以直接在 app/src/test 里断言分组规则（见 HomeFoldersTest）。
 */
data class HomeFolder(
    val id: String,
    val title: String,
    /** 「分享」/「专辑」/「未分类」，UI 用来显示角标与选图标。 */
    val kind: Kind,
    val songs: List<Song>,
    /** 仅分享文件夹有意义：已入库 N 首之外还有多少首没入库。 */
    val missing: Int = 0,
    /**
     * 该文件夹代表的分享歌单 id（仅 SHARE 文件夹非空）。
     * 歌单管理（删除 / 合并）直接用这个 id 调 /api/v1/shares/:id，不必从 "share:" 前缀里再切字符串。
     */
    val shareId: String? = null
) {
    enum class Kind { SHARE, ALBUM, ARTIST, UNCATEGORIZED }
}

object HomeFolders {

    /** 未分类文件夹的固定 id（避免与专辑名撞车）。 */
    const val UNCATEGORIZED_ID = "__uncategorized__"

    /**
     * 歌单轴：一个分享歌单一个文件夹。顺序沿用 collections（分享创建顺序），不做额外排序。
     */
    fun buildPlaylists(collections: List<HomeCollection>): List<HomeFolder> =
        collections.map { col ->
            HomeFolder(
                id = "share:" + col.id,
                title = col.title,
                kind = HomeFolder.Kind.SHARE,
                songs = col.songs,
                missing = col.missing,
                shareId = col.id
            )
        }

    /**
     * 专辑轴：曲库按专辑聚合成文件夹 + 未分类兜底。
     *
     * 顺序保证稳定：按专辑名排序（大小写无关），未分类永远在最后。
     * 稳定顺序很重要 —— 否则每次 refresh 后网格卡片乱跳，看起来像 bug。
     */
    fun buildAlbums(songs: List<Song>): List<HomeFolder> {
        val folders = mutableListOf<HomeFolder>()
        songs.groupBy { it.album.trim() }
            .filterKeys { it.isNotEmpty() }
            .toSortedMap(compareBy { it.lowercase() })
            .forEach { (album, list) ->
                folders += HomeFolder(
                    id = "album:" + album,
                    title = album,
                    kind = HomeFolder.Kind.ALBUM,
                    songs = list
                )
            }
        // 没有专辑名的歌收进「未分类」，不让它们从界面上消失
        val uncategorized = songs.filter { it.album.isBlank() }
        if (uncategorized.isNotEmpty()) {
            folders += HomeFolder(
                id = UNCATEGORIZED_ID,
                title = "未分类",
                kind = HomeFolder.Kind.UNCATEGORIZED,
                songs = uncategorized
            )
        }
        return folders
    }

    /** 艺人轴：按艺人名排序，空艺人名归入「未知艺人」，不丢歌。 */
    fun buildArtists(songs: List<Song>): List<HomeFolder> {
        val folders = songs.groupBy { it.artist.trim() }
            .filterKeys { it.isNotEmpty() }
            .toSortedMap(compareBy { it.lowercase() })
            .map { (artist, list) ->
                HomeFolder(id = "artist:" + artist, title = artist, kind = HomeFolder.Kind.ARTIST, songs = list)
            }.toMutableList()
        val unknown = songs.filter { it.artist.isBlank() }
        if (unknown.isNotEmpty()) {
            folders += HomeFolder(id = "__unknown_artist__", title = "未知艺人", kind = HomeFolder.Kind.ARTIST, songs = unknown)
        }
        return folders
    }

    /**
     * 两个轴拼接（歌单在前，专辑在后）。
     *
     * v1.9.0 起**首页不再使用**它（那样会重复展示），保留是为了：
     *   1) 兼容既有单测与"全量视图"这类需要一次性看全的场景；
     *   2) 让"两轴拼起来恰好覆盖全部内容、不丢歌"这条性质可以被断言。
     */
    fun build(songs: List<Song>, collections: List<HomeCollection>): List<HomeFolder> =
        buildPlaylists(collections) + buildAlbums(songs)

    /**
     * 文件夹封面的代表图：取前 4 首有封面的歌曲。
     *
     * 网格卡片用「封面拼图」比纯文字更有辨识度；只取 4 张是因为 2x2 拼图正好，
     * 也避免为了一张卡片触发几十次图片请求（这正是掉帧的来源之一）。
     */
    fun coverUrls(folder: HomeFolder, max: Int = 4): List<String> =
        folder.songs.asSequence()
            .mapNotNull { it.coverUrl }
            .distinct()
            .take(max)
            .toList()

    /**
     * 刷新后决定当前该显示哪个轴。
     *
     * 规则：用户选的轴还有内容就保持不动（不打断操作）；
     * 该轴空了就自动切到另一个有内容的轴 —— 否则用户会看到一个空首页，
     * 以为曲库/歌单丢了。两轴都空时保持原选择。
     */
    fun resolveAxis(current: HomeAxis, hasPlaylists: Boolean, hasAlbums: Boolean, hasArtists: Boolean = hasAlbums): HomeAxis =
        when {
            current == HomeAxis.PLAYLISTS && hasPlaylists -> HomeAxis.PLAYLISTS
            current == HomeAxis.ALBUMS && hasAlbums -> HomeAxis.ALBUMS
            current == HomeAxis.ARTISTS && hasArtists -> HomeAxis.ARTISTS
            hasPlaylists -> HomeAxis.PLAYLISTS
            hasAlbums -> HomeAxis.ALBUMS
            hasArtists -> HomeAxis.ARTISTS
            else -> current
        }
}

/**
 * v1.6.0：生成 Indexed 版表格（带封面拼图与副标题）。
 *
 * 为什么不在 Composable 里调用 [HomeFolders.build] 之后现算：
 * 网格卡片每帧都会读 folder.title / subtitle / 封面拼图，若这些字符串与列表在组合期现算，
 * 每个卡片每帧都要分配新对象（实测 60 个卡片 ≈ 每帧 200+ 次分配 → GC 抖动 → 掉帧）。
 * 这里把表格预先算好挂在 UiState 上，refresh 或切轴时只做一次，滚动期间零分配。
 */
fun buildFolderGrid(axis: HomeAxis, collections: List<HomeCollection>, library: List<Song>): List<HomeFolderGrid> {
    val folders = when (axis) {
        HomeAxis.PLAYLISTS -> HomeFolders.buildPlaylists(collections)
        HomeAxis.ALBUMS -> HomeFolders.buildAlbums(library)
        HomeAxis.ARTISTS -> HomeFolders.buildArtists(library)
    }
    return folders.map { folder ->
        HomeFolderGrid(
            folder = folder,
            covers = HomeFolders.coverUrls(folder),
            subtitle = buildFolderSubtitleText(folder)
        )
    }
}

/** 副标题：N 首，分享文件夹额外说明未入库数量。纯函数，便于单测。 */
fun buildFolderSubtitleText(folder: HomeFolder): String {
    val base = folder.songs.size.toString() + " 首"
    return if (folder.kind == HomeFolder.Kind.SHARE && folder.missing > 0) {
        base + " · " + folder.missing + " 首未入库"
    } else {
        base
    }
}

/**
 * 网格卡片所需的全部显示数据，全部是值类型。
 * 卡片的 recomposition 参数只有它 + 一个 onClick，且强跳过下 instance 相等即可跳过。
 */
data class HomeFolderGrid(
    val folder: HomeFolder,
    val covers: List<String>,
    val subtitle: String
) {
    val id: String get() = folder.id
    val title: String get() = folder.title
    val kind: HomeFolder.Kind get() = folder.kind
}

/**
 * v1.6.0 需求3（主页页面管理）的纯规则：哪些歌单能删、能不能合并、合并方向。
 *
 * 全部做成无状态纯函数，UI 只负责把结果画出来 —— 这样"能不能点"的判断和"点了之后
 * 发什么请求"不会漂移，也便于单测锁住边界（尤其是「删歌单绝不碰曲库音频」这条）。
 */
object PlaylistManageRules {

    /** 只有分享歌单（sharelinks）可管理；专辑/未分类是曲库派生物，没有独立实体可删。 */
    fun isManageable(folder: HomeFolder): Boolean =
        folder.kind == HomeFolder.Kind.SHARE && !folder.shareId.isNullOrBlank()

    /**
     * 卡片是否处于选中态。
     *
     * v1.7.0 修复：`selected` 集合装的是 shareId（sharelinks.id，如 `mv6kz...`），
     * 而 [HomeFolder.id] 是网格内部带前缀的 folderId（`share:mv6kz...`）。
     * 之前 UI 与 [mergeTarget] 都用 `folder.id in selected` 判断，于是恒为 false ——
     * 表现为"管理态下点卡片没反应"和"合并对话框默认目标没选中"。
     * 统一走这一个函数，避免再出现"同一集合、两种键"的漂移。
     */
    fun isSelected(folder: HomeFolder, selected: Set<String>): Boolean =
        folder.shareId?.let { it in selected } ?: false

    fun canDelete(selected: Set<String>): Boolean = selected.isNotEmpty()

    /**
     * 合并至少需要两个歌单：一个是保留的目标，其余是并入的来源。
     * 只选一个时「合并」按钮必须不可点，否则会退化成"把歌单并进自己"。
     */
    fun canMerge(selected: Set<String>): Boolean = selected.size >= 2

    /**
     * 默认目标 = folderList 顺序里第一个被选中的歌单。
     * 用 folderList 的顺序而不是 Set 的迭代顺序，保证每次打开对话框默认值一致。
     */
    fun mergeTarget(folderList: List<HomeFolder>, selected: Set<String>): String? =
        folderList.firstOrNull { isSelected(it, selected) }?.shareId

    /**
     * 来源 = 选中集合去掉目标。
     *
     * v1.7.0：选中集合里存的**就是** sharelinks.id（不带 "share:" 前缀），所以这里不再做
     * removePrefix / 拼前缀的字符串操作 —— 之前那套写法假设集合里是 folderId，与新约定冲突，
     * 会在合并时把目标自己当成来源一起发出去。现在直接用集合本身。
     */
    fun mergeSources(targetId: String?, selected: Set<String>): List<String> =
        selected.filter { it != targetId }.sorted()

    /**
     * 数据刷新后收敛选择集：只保留仍然存在、且仍然是可管理分享歌单的 id。
     * 否则删完歌单底栏还显示"已选 2"，用户会以为有幽灵选择。
     *
     * v1.10.0（D1）：**修正语义**。旧实现除了过滤存活 id，还要求 startsWith("share:")
     * —— 那是 v1.7.0 之前的旧键约定（集合装 folderId）。现在集合装的是裸 sharelinks.id，
     * 这个前缀判断会让函数**永远返回空集**：一旦有人把它接到真实流程上，
     * 用户的选择会被静默清空且毫无提示。
     * 管理态那套逻辑（HomeViewModel.prunePlaylistSelection）已经是正确实现，
     * 这里同步成同一语义，避免两份"同名不同义"的代码继续漂移。
     */
    fun pruneSelection(selected: Set<String>, aliveShareIds: Set<String>): Set<String> =
        selected.filter { it in aliveShareIds }.toSet()
}