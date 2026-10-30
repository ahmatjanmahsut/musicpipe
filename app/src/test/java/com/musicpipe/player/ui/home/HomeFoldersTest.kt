package com.musicpipe.player.ui.home

import com.musicpipe.player.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.5.0 文件夹式首页的分组规则。 */
class HomeFoldersTest {

    private fun song(
        id: String,
        album: String = "专辑A",
        cover: String? = "https://x/cover/" + id
    ) = Song(
        id = id, title = "歌曲" + id, artist = "歌手", album = album,
        durationMs = 200_000, coverUrl = cover, platform = "wy", streamUrl = "https://x/stream/" + id
    )

    @Test
    fun groupsLibraryByAlbum() {
        val songs = listOf(song("1", "专辑A"), song("2", "专辑A"), song("3", "专辑B"))
        val folders = HomeFolders.build(songs, emptyList())
        assertEquals(2, folders.size)
        // 按专辑名排序（大小写无关）
        assertEquals("专辑A", folders[0].title)
        assertEquals(2, folders[0].songs.size)
        assertEquals(HomeFolder.Kind.ALBUM, folders[0].kind)
        assertEquals("专辑B", folders[1].title)
        assertEquals(1, folders[1].songs.size)
    }

    @Test
    fun sharesComeFirstAndKeepTheirMissingCount() {
        val songs = listOf(song("1", "专辑A"))
        val share = HomeCollection(id = "s1", title = "我喜欢的音乐", songs = songs, missing = 60)
        val folders = HomeFolders.build(songs, listOf(share))
        assertEquals("share:s1", folders[0].id)
        assertEquals("我喜欢的音乐", folders[0].title)
        assertEquals(HomeFolder.Kind.SHARE, folders[0].kind)
        assertEquals(60, folders[0].missing)
        // 之后才是专辑文件夹
        assertTrue(folders.drop(1).all { it.kind == HomeFolder.Kind.ALBUM })
    }

    @Test
    fun songsWithoutAlbumLandInUncategorizedFolder() {
        val songs = listOf(song("1", "专辑A"), song("2", ""), song("3", "   "))
        val folders = HomeFolders.build(songs, emptyList())
        val uncategorized = folders.find { it.id == HomeFolders.UNCATEGORIZED_ID }
        // 空串和纯空格的专辑名都要收进来，不能让歌曲从界面上消失
        assertEquals(2, uncategorized?.songs?.size)
        assertEquals("未分类", uncategorized?.title)
    }

    @Test
    fun noUncategorizedFolderWhenEverySongHasAlbum() {
        val folders = HomeFolders.build(listOf(song("1", "专辑A")), emptyList())
        assertTrue(folders.none { it.id == HomeFolders.UNCATEGORIZED_ID })
    }

    @Test
    fun coverUrlsTakeAtMostFourDistinct() {
        val songs = listOf(
            song("1", cover = "c1"), song("2", cover = "c2"),
            song("3", cover = "c3"), song("4", cover = "c4"),
            song("5", cover = "c5")
        )
        val folder = HomeFolder("f", "F", HomeFolder.Kind.ALBUM, songs)
        assertEquals(listOf("c1", "c2", "c3", "c4"), HomeFolders.coverUrls(folder))
    }

    @Test
    fun coverUrlsSkipSongsWithoutArtwork() {
        val songs = listOf(song("1", cover = null), song("2", cover = "c2"))
        val folder = HomeFolder("f", "F", HomeFolder.Kind.ALBUM, songs)
        assertEquals(listOf("c2"), HomeFolders.coverUrls(folder))
    }

    @Test
    fun emptyLibraryYieldsNoFolders() {
        assertTrue(HomeFolders.build(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun folderOrderIsStableAcrossRebuilds() {
        val songs = listOf(song("1", "B专辑"), song("2", "A专辑"), song("3", "C专辑"))
        val first = HomeFolders.build(songs, emptyList()).map { it.id }
        val second = HomeFolders.build(songs.reversed(), emptyList()).map { it.id }
        // 输入顺序不应影响文件夹顺序，否则每次 refresh 后卡片会乱跳
        assertEquals(first, second)
    }

    // ---- v1.6.0: 网格缓存对象（组合期不再重算） ----

    @Test
    fun buildFolderGridCachesSubtitleAndCovers() {
        val songs = listOf(song("1", "专辑A"), song("2", "专辑A"))
        val share = HomeCollection(id = "s1", title = "我喜欢的音乐", songs = songs, missing = 0)
        val grid: List<HomeFolderGrid> = buildFolderGrid(HomeAxis.PLAYLISTS, listOf(share), songs)
        val shareCell = grid.first { it.kind == HomeFolder.Kind.SHARE }
        assertEquals("share:s1", shareCell.id)
        assertEquals("s1", shareCell.folder.shareId)
        assertEquals("2 首", shareCell.subtitle)
        assertEquals(listOf("https://x/cover/1", "https://x/cover/2"), shareCell.covers)
    }

    @Test
    fun shareSubtitleMentionsMissingTracks() {
        val songs = listOf(song("1", "专辑A"))
        val share = HomeCollection(id = "s1", title = "歌单", songs = songs, missing = 60)
        val grid = buildFolderGrid(HomeAxis.PLAYLISTS, listOf(share), songs)
        val subtitle = grid.first { it.kind == HomeFolder.Kind.SHARE }.subtitle
        assertTrue(subtitle.contains("60"))
        assertTrue(subtitle.contains("未入库"))
    }

    @Test
    fun albumFolderCarriesNoShareId() {
        val songs = listOf(song("1", "专辑A"))
        val grid = buildFolderGrid(HomeAxis.ALBUMS, emptyList(), songs)
        assertEquals(null, grid.first { it.kind == HomeFolder.Kind.ALBUM }.folder.shareId)
    }

    @Test
    fun subtitleTextIsPureFunctionOfFolder() {
        val songs = listOf(song("1", "专辑A"), song("2", "专辑A"))
        val album = HomeFolder("album:专辑A", "专辑A", HomeFolder.Kind.ALBUM, songs)
        assertEquals(buildFolderSubtitleText(album), album.let { buildFolderSubtitleText(it) })
        assertTrue(buildFolderSubtitleText(album).startsWith("2 首"))
    }

    @Test
    fun gridOrderMatchesBuildOrder() {
        val songs = listOf(song("1", "B专辑"), song("2", "A专辑"))
        val share = HomeCollection(id = "s1", title = "歌单", songs = songs, missing = 0)
        // 每个轴各自与对应的原始分组规则对齐（两轴拼接的 build() 不再等于单个轴的网格）
        assertEquals(
            HomeFolders.buildPlaylists(listOf(share)).map { f -> f.id },
            buildFolderGrid(HomeAxis.PLAYLISTS, listOf(share), songs).map { g -> g.id }
        )
        assertEquals(
            HomeFolders.buildAlbums(songs).map { f -> f.id },
            buildFolderGrid(HomeAxis.ALBUMS, listOf(share), songs).map { g -> g.id }
        )
    }

    // ---- v1.9.0：两轴分离（首页不再重复展示同一批歌） ----

    /**
     * 这条是本次修复的核心回归测试。
     *
     * 线上真实数据：曲库 101 首全部来自同一个分享歌单，且 album 字段全被写成分享者昵称
     * （"ahmattjon"）。v1.5.0~v1.8.0 把两轴铺在同一个网格里，于是首页出现两张
     * 内容完全相同的卡片。拆轴之后：歌单轴 1 张、专辑轴 1 张，但**两者永不同屏**，
     * 且两轴的 id 集合不相交 —— 保证同一屏不会出现重复入口。
     */
    @Test
    fun axesAreDisjointSoNothingIsShownTwice() {
        val songs = (1..5).map { song(it.toString(), "ahmattjon") }
        val share = HomeCollection(id = "s1", title = "我喜欢的音乐", songs = songs, missing = 0)
        val playlists = buildFolderGrid(HomeAxis.PLAYLISTS, listOf(share), songs).map { it.id }
        val albums = buildFolderGrid(HomeAxis.ALBUMS, listOf(share), songs).map { it.id }
        assertEquals(listOf("share:s1"), playlists)
        assertEquals(listOf("album:ahmattjon"), albums)
        assertTrue("两轴不应共享任何文件夹 id", playlists.intersect(albums.toSet()).isEmpty())
    }

    @Test
    fun playlistsAxisHoldsOnlyShareFolders() {
        val songs = listOf(song("1", "专辑A"), song("2", ""))
        val share = HomeCollection(id = "s1", title = "歌单", songs = listOf(songs[0]), missing = 3)
        val grid = buildFolderGrid(HomeAxis.PLAYLISTS, listOf(share), songs)
        // 专辑轴的东西（专辑文件夹、未分类）一律不该出现在歌单轴里
        assertTrue(grid.all { it.kind == HomeFolder.Kind.SHARE })
        assertEquals(1, grid.size)
        assertEquals(3, grid[0].folder.missing)
    }

    @Test
    fun albumsAxisHoldsNoShareFolders() {
        val songs = listOf(song("1", "专辑A"), song("2", ""))
        val share = HomeCollection(id = "s1", title = "歌单", songs = songs, missing = 0)
        val grid = buildFolderGrid(HomeAxis.ALBUMS, listOf(share), songs)
        assertTrue(grid.none { it.kind == HomeFolder.Kind.SHARE })
        // 专辑 + 未分类
        assertTrue(grid.any { it.kind == HomeFolder.Kind.ALBUM })
        assertTrue(grid.any { it.kind == HomeFolder.Kind.UNCATEGORIZED })
    }

    @Test
    fun everySongStaysReachableInAlbumsAxis() {
        // 拆轴不能丢歌：专辑轴必须覆盖整个曲库（专辑 + 未分类）。
        val songs = listOf(song("1", "专辑A"), song("2", "专辑B"), song("3", ""))
        val reachable = buildFolderGrid(HomeAxis.ALBUMS, emptyList(), songs)
            .flatMap { it.folder.songs }
            .map { it.id }
            .toSet()
        assertEquals(songs.map { it.id }.toSet(), reachable)
    }

    @Test
    fun resolveAxisKeepsUserChoiceWhileItHasContent() {
        assertEquals(HomeAxis.ALBUMS, HomeFolders.resolveAxis(HomeAxis.ALBUMS, true, true))
        assertEquals(HomeAxis.PLAYLISTS, HomeFolders.resolveAxis(HomeAxis.PLAYLISTS, true, true))
    }

    @Test
    fun resolveAxisFallsBackWhenChosenAxisIsEmpty() {
        // 删掉最后一个歌单后停在歌单轴 -> 首页会空；自动切到专辑轴
        assertEquals(HomeAxis.ALBUMS, HomeFolders.resolveAxis(HomeAxis.PLAYLISTS, false, true))
        // 曲库被清空后停在专辑轴 -> 自动切回歌单轴
        assertEquals(HomeAxis.PLAYLISTS, HomeFolders.resolveAxis(HomeAxis.ALBUMS, true, false))
    }

    @Test
    fun resolveAxisKeepsCurrentWhenBothEmpty() {
        assertEquals(HomeAxis.PLAYLISTS, HomeFolders.resolveAxis(HomeAxis.PLAYLISTS, false, false))
        assertEquals(HomeAxis.ALBUMS, HomeFolders.resolveAxis(HomeAxis.ALBUMS, false, false))
    }

    @Test
    fun emptyGridWhenNothingToShow() {
        assertTrue(buildFolderGrid(HomeAxis.PLAYLISTS, emptyList(), emptyList()).isEmpty())
    }

    // ---- v1.18.0：艺人分类 ----
    @Test fun artistsAreGroupedSortedAndCoverEverySong() {
        val songs = listOf(
            song("1").copy(artist = "周杰伦"),
            song("2").copy(artist = "Adele"),
            song("3").copy(artist = "周杰伦"),
            song("4").copy(artist = " ")
        )
        val grid = buildFolderGrid(HomeAxis.ARTISTS, emptyList(), songs)
        assertEquals(listOf("Adele", "周杰伦", "未知艺人"), grid.map { it.title })
        assertEquals(2, grid[1].folder.songs.size)
        assertEquals(songs.map { it.id }.toSet(), grid.flatMap { it.folder.songs }.map { it.id }.toSet())
        assertTrue(grid.all { it.kind == HomeFolder.Kind.ARTIST && it.folder.shareId == null })
    }

    @Test fun artistAxisSurvivesRefreshAndDoesNotDuplicateIds() {
        val songs = listOf(song("1").copy(artist = "周杰伦"))
        assertEquals(HomeAxis.ARTISTS, HomeFolders.resolveAxis(HomeAxis.ARTISTS, true, true, true))
        assertEquals("artist:周杰伦", buildFolderGrid(HomeAxis.ARTISTS, emptyList(), songs).single().id)
    }

}
