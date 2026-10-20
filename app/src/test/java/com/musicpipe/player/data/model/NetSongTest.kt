package com.musicpipe.player.data.model

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** NetSong is a safe read-only view over raw gateway search JSON. */
class NetSongTest {

    private val full = JsonParser.parseString(
        """
        {
          "source": "kw",
          "id": "123",
          "name": "晴天",
          "singer": "周杰伦",
          "album": "叶惠美",
          "duration": 269,
          "img": "http://x/cover.jpg",
          "types": [{"type": "flac"}, {"type": "320k"}]
        }
        """.trimIndent()
    ).asJsonObject

    @Test
    fun readsAllDisplayFields() {
        val s = NetSong(full)
        assertEquals("kw:123", s.key)
        assertEquals("晴天", s.name)
        assertEquals("周杰伦", s.singer)
        assertEquals("叶惠美", s.album)
        assertEquals(269L, s.durationSec)
        assertEquals("http://x/cover.jpg", s.img)
        assertEquals(listOf("flac", "320k"), s.qualityTypes)
    }

    @Test
    fun missingFieldsDegradeGracefully() {
        val s = NetSong(JsonObject())
        assertEquals(":", s.key)
        assertEquals("", s.name)
        assertEquals("", s.singer)
        assertEquals(0L, s.durationSec)
        assertNull(s.img)
        assertTrue(s.qualityTypes.isEmpty())
    }

    @Test
    fun wrongTypesDegradeToDefaults() {
        val o = JsonObject()
        o.addProperty("source", "tx")
        o.addProperty("img", "   ")
        o.add("duration", JsonObject()) // not a primitive
        o.addProperty("types", "not-an-array")
        val s = NetSong(o)
        assertEquals(0L, s.durationSec)
        assertNull(s.img)
        assertTrue(s.qualityTypes.isEmpty())
    }

    @Test
    fun originalJsonIsKeptForRoundTrip() {
        val s = NetSong(full)
        assertEquals(full, s.json) // download() sends this object back verbatim
    }
}
