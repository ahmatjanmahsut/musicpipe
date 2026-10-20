package com.musicpipe.player.ui.home

import com.musicpipe.player.data.model.Song
import com.musicpipe.player.data.remote.dto.ShareListItem
import com.musicpipe.player.data.remote.dto.ShareSongDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for home section building (share playlists -> library songs). */
class HomeSectionsTest {

    private fun song(id: String) = Song(
        id = id, title = "T-" + id, artist = "A", album = "B",
        durationMs = 1000L, coverUrl = null, platform = "wy",
        streamUrl = "http://192.168.1.10:7800/api/v1/stream/" + id
    )

    private fun item(no: Int, trackId: String?) = ShareSongDto(
        no = no, extKey = "k" + no, title = "S" + no, artist = "A",
        state = if (trackId == null) "preview" else "done",
        inLib = if (trackId == null) 0 else 1,
        taskId = null, trackId = trackId, addedAt = null
    )

    private fun share(id: String, title: String?, url: String?) =
        ShareListItem(id = id, url = url, title = title)

    @Test
    fun itemsSortedByNoAndMappedToLibrarySongs() {
        val library = listOf(song("t1"), song("t2"), song("t3"))
        val byShare = mapOf("s1" to listOf(item(2, "t2"), item(1, "t1"), item(3, "t3")))
        val cols = HomeSections.buildCollections(listOf(share("s1", "晴天歌单", null)), byShare, library)
        assertEquals(1, cols.size)
        assertEquals("晴天歌单", cols[0].title)
        assertEquals(listOf("t1", "t2", "t3"), cols[0].songs.map { it.id })
        assertEquals(0, cols[0].missing)
    }

    @Test
    fun unlinkedAndUnknownTracksCountAsMissing() {
        val library = listOf(song("t1"))
        val byShare = mapOf("s1" to listOf(item(1, "t1"), item(2, null), item(3, "gone")))
        val cols = HomeSections.buildCollections(listOf(share("s1", "x", null)), byShare, library)
        assertEquals(listOf("t1"), cols[0].songs.map { it.id })
        assertEquals(2, cols[0].missing)
    }

    @Test
    fun duplicateTrackIdsCollapseToOneSong() {
        val library = listOf(song("t1"))
        val byShare = mapOf("s1" to listOf(item(1, "t1"), item(2, "t1")))
        val cols = HomeSections.buildCollections(listOf(share("s1", null, null)), byShare, library)
        assertEquals(1, cols[0].songs.size)
        assertEquals(0, cols[0].missing)
    }

    @Test
    fun titleFallsBackToUrlThenDefault() {
        assertEquals("我的歌单", HomeSections.collectionTitle(share("s", "我的歌单", "http://u")))
        assertEquals("http://u", HomeSections.collectionTitle(share("s", null, "http://u")))
        assertEquals("http://u", HomeSections.collectionTitle(share("s", "   ", "http://u")))
        assertEquals("分享歌单", HomeSections.collectionTitle(share("s", null, null)))
        assertEquals("分享歌单", HomeSections.collectionTitle(share("s", "", "")))
    }

    @Test
    fun missingSongsEntryDegradesToEmptyCollection() {
        val cols = HomeSections.buildCollections(
            listOf(share("s1", "t", null)), emptyMap(), listOf(song("t1"))
        )
        assertEquals(1, cols.size)
        assertTrue(cols[0].songs.isEmpty())
        assertEquals(0, cols[0].missing)
    }

    @Test
    fun noSharesNoCollections() {
        assertTrue(HomeSections.buildCollections(emptyList(), emptyMap(), emptyList()).isEmpty())
    }
@Test
    fun existsRowsWithoutTrackIdResolveByTitleAndArtist() {
        val libSong = song("t9").copy(title = "知我", artist = "国风堂、哦漏")
        val library = listOf(libSong)
        val existsRow = ShareSongDto(
            no = 1, extKey = "k1", title = "知我", artist = "国风堂、哦漏",
            state = "exists", inLib = 1, taskId = null, trackId = "", addedAt = null
        )
        val cols = HomeSections.buildCollections(
            listOf(share("s1", "歌单", null)),
            mapOf("s1" to listOf(existsRow)),
            library
        )
        assertEquals(listOf("t9"), cols[0].songs.map { it.id })
        assertEquals(0, cols[0].missing)
    }

    @Test
    fun titleFallbackIgnoresSpacesAndCase() {
        val libSong = song("t9").copy(title = "Kalbim（翻自 svvbi）", artist = "eKRaM")
        val row = ShareSongDto(
            no = 1, extKey = "k1", title = " Kalbim（翻自 SVVBI） ", artist = "ekram",
            state = "exists", inLib = 1, taskId = null, trackId = null, addedAt = null
        )
        val cols = HomeSections.buildCollections(
            listOf(share("s1", "歌单", null)), mapOf("s1" to listOf(row)), listOf(libSong)
        )
        assertEquals(listOf("t9"), cols[0].songs.map { it.id })
        assertEquals(0, cols[0].missing)
    }

    @Test
    fun unknownTitleWithoutTrackIdStaysMissing() {
        val row = ShareSongDto(
            no = 1, extKey = "k1", title = "不存在的歌", artist = "X",
            state = "preview", inLib = 0, taskId = null, trackId = null, addedAt = null
        )
        val cols = HomeSections.buildCollections(
            listOf(share("s1", "歌单", null)), mapOf("s1" to listOf(row)), listOf(song("t1"))
        )
        assertTrue(cols[0].songs.isEmpty())
        assertEquals(1, cols[0].missing)
    }
}
