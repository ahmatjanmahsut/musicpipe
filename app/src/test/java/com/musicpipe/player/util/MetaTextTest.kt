package com.musicpipe.player.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.14.0：列表行的 " · " 元信息拼接从 listOf().filter{}.joinToString() 换成零中间集合的
 * [MetaText.metaText]。这是滚动路径上的高频调用，行为必须与旧实现完全一致，
 * 否则会出现"某一行少了个点"这类只有肉眼能发现的回归。
 */
class MetaTextTest {

    @Test
    fun joinsPartsWithSeparator() {
        assertEquals("a · b", metaText { add("a"); add("b") })
    }

    @Test
    fun skipsNullAndBlankParts() {
        assertEquals("a", metaText { add("a"); add(null); add(""); add("  ") })
    }

    @Test
    fun allBlankYieldsEmptyString() {
        assertEquals("", metaText { add(null); add("") })
        assertEquals("", metaText { })
    }

    @Test
    fun customSeparator() {
        assertEquals("a — b", metaText(" — ") { add("a"); add("b") })
    }

    @Test
    fun separatorNeverLeadsOrTrails() {
        // 旧实现的 joinToString 同样不会出现首尾分隔符，这里锁住这个不变量。
        assertEquals("只一个", metaText { add(null); add("只一个"); add(null) })
    }

    @Test
    fun preservesOrder() {
        assertEquals("1 · 2 · 3", metaText { add("1"); add("2"); add("3") })
    }
}
