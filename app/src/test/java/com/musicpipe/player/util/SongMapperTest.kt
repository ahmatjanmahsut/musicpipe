package com.musicpipe.player.util

import com.musicpipe.player.data.remote.ServerConfig
import com.musicpipe.player.data.remote.dto.TrackDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Gateway trackBrief -> domain Song mapping (router-merge contract).
 * Live probe of GET /api/v1/library showed duration in SECONDS and relative stream/cover paths.
 */
class SongMapperTest {

    private val track = TrackDto(
        id = "mv7nbhxhf3dfca88b612",
        title = "Aygul",
        artist = "羊音乐",
        album = "ahmattjon",
        quality = "flac24bit",
        duration = 124L,
        size = 28740395L,
        path = "ahmattjon/06 - Aygul.flac",
        status = "imported",
        platform = "wy",
        streamUrl = "/api/v1/stream/mv7nbhxhf3dfca88b612",
        coverUrl = "/api/v1/cover/mv7nbhxhf3dfca88b612",
        lyricsUrl = "/api/v1/lyrics/mv7nbhxhf3dfca88b612"
    )

    @Before
    fun setUp() {
        ServerConfig.restBaseUrl = "http://192.168.1.10:7800"
    }

    @Test
    fun gatewayDurationIsSecondsAndMapsToMillis() {
        val song = SongMapper.fromTrack(track, "tok")
        assertEquals(124_000L, song.durationMs)
        assertEquals("mv7nbhxhf3dfca88b612", song.id)
        assertEquals("Aygul", song.title)
        assertEquals("wy", song.platform)
    }

    @Test
    fun streamAndCoverAreAbsoluteAndTokenSigned() {
        val song = SongMapper.fromTrack(track, "abc123")
        assertEquals(
            "http://192.168.1.10:7800/api/v1/stream/mv7nbhxhf3dfca88b612?token=abc123",
            song.streamUrl
        )
        assertEquals(
            "http://192.168.1.10:7800/api/v1/cover/mv7nbhxhf3dfca88b612?token=abc123",
            song.coverUrl
        )
    }

    @Test
    fun emptyTokenKeepsUrlsUnsigned() {
        val song = SongMapper.fromTrack(track, "")
        assertEquals("http://192.168.1.10:7800/api/v1/stream/mv7nbhxhf3dfca88b612", song.streamUrl)
    }

    @Test
    fun missingCoverDegradesToNull() {
        val song = SongMapper.fromTrack(track.copy(coverUrl = null), "tok")
        assertNull(song.coverUrl)
    }

    @Test
    fun cachedRowsRebuildPathsFromSongIdAndReSign() {
        val song = SongMapper.fromCached(
            songId = "x1", title = "T", artist = "A", album = "B",
            durationMs = 1000L, rawCover = "/api/v1/cover/x1",
            rawStream = null, platform = "musicpipe", token = "tk"
        )
        assertEquals("http://192.168.1.10:7800/api/v1/stream/x1?token=tk", song.streamUrl)
        assertEquals("http://192.168.1.10:7800/api/v1/cover/x1?token=tk", song.coverUrl)
        // legacy absolute URL cached from an older build -> rebuilt from the id, never replayed
        val legacy = SongMapper.fromCached(
            songId = "x2", title = "T", artist = "A", album = "B",
            durationMs = 1L, rawCover = "http://old:4533/coverArt.jpg?id=7",
            rawStream = "http://old:4533/rest/stream?u=a", platform = "musicpipe", token = "tk"
        )
        assertEquals("http://192.168.1.10:7800/api/v1/stream/x2?token=tk", legacy.streamUrl)
    }
}
