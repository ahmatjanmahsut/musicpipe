package com.musicpipe.player.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.13.0 多风格切换：规格表的自洽性。
 *
 * 这里锁的不是"好不好看"（那要看真机），而是三类**会直接让某个风格看起来是坏掉的**错误：
 *  1. 六套风格缺一或重复 —— 设置页会少一项/选不到；
 *  2. 强调色与底色太接近 —— 该风格下"播放中""选中"几乎看不见；
 *  3. 明暗两套里某一套忘了定义/写反了 —— 深色模式下出现白字白底。
 *
 * 纯 JVM 断言：Color 与 Dp 都是 Compose 的值类，不需要 Android 运行时。
 */
class StyleSpecsTest {

    private val all = AppStyle.entries

    @Test
    fun everyStyleHasSpec() {
        assertEquals(7, all.size)
        all.forEach { style ->
            val spec = StyleSpecs.of(style)
            assertEquals(style, spec.style)
        }
    }

    @Test
    fun fromIdRoundTripsAndFallsBack() {
        all.forEach { style ->
            assertEquals(style, AppStyle.fromId(style.id))
        }
        // 未知/空/旧数据一律回落到默认风格，不能崩、不能变成 null
        assertEquals(AppStyle.NETEASE, AppStyle.fromId(null))
        assertEquals(AppStyle.NETEASE, AppStyle.fromId(""))
        assertEquals(AppStyle.NETEASE, AppStyle.fromId("no-such-style"))
    }

    @Test
    fun idsAreUniqueAndStable() {
        // id 已持久化进 DataStore，改了等于把老用户的设置清掉。
        val ids = all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            listOf("netease", "awwwards", "classic", "material", "cupertino", "shadcn", "brutal"),
            ids
        )
    }

    @Test
    fun accentContrastsWithPaperInBothModes() {
        // 不做 WCAG 精确计算，只挡"强调色和底色几乎一样"这种灾难：
        // 用两色亮度差做粗判，阈值取一个宽松值，只为拦住写错色值的情况。
        all.forEach { style ->
            listOf(true, false).forEach { dark ->
                val pal = StyleSpecs.of(style).palette(dark)
                val diff = kotlin.math.abs(luma(pal.accent) - luma(pal.paper))
                assertTrue(
                    "${style.id} dark=$dark 强调色与底色太接近 (diff=$diff)",
                    diff > 0.08f
                )
            }
        }
    }

    @Test
    fun inkContrastsWithPaperInBothModes() {
        all.forEach { style ->
            listOf(true, false).forEach { dark ->
                val pal = StyleSpecs.of(style).palette(dark)
                val diff = kotlin.math.abs(luma(pal.ink) - luma(pal.paper))
                assertTrue("${style.id} dark=$dark 正文色与底色太接近 (diff=$diff)", diff > 0.3f)
            }
        }
    }

    @Test
    fun darkAndLightPalettesDifferForEveryStyle() {
        all.forEach { style ->
            val spec = StyleSpecs.of(style)
            assertNotEquals("${style.id} 的明暗两套底色相同", spec.dark.paper, spec.light.paper)
        }
    }

    @Test
    fun shapesStayWithinDocumentedRanges() {
        // DESIGN.md 对 Editorial 硬约束：圆角 2-4dp；Brutalist 必须 0；其余是各自真实值。
        assertEquals(2f, StyleSpecs.of(AppStyle.AWWWARDS).shape.card.value)
        assertEquals(0f, StyleSpecs.of(AppStyle.BRUTAL).shape.card.value)
        assertEquals(0f, StyleSpecs.of(AppStyle.BRUTAL).shape.input.value)
        assertEquals(6f, StyleSpecs.of(AppStyle.SHADCN).shape.card.value)
        assertEquals(16f, StyleSpecs.of(AppStyle.MATERIAL).shape.card.value)
        // Brutalist 靠粗描边立命，其它风格不该给卡片描边。
        assertEquals(2f, StyleSpecs.of(AppStyle.BRUTAL).shape.border.value)
        assertEquals(0f, StyleSpecs.of(AppStyle.MATERIAL).shape.border.value)
    }

    @Test
    fun chromeFlagsMatchEachLanguage() {
        // 噪点只有编辑风与 Brutalist 有；Material/Cupertino/shadcn 要干净的平面。
        assertTrue(StyleSpecs.of(AppStyle.AWWWARDS).chrome.grain)
        assertTrue(StyleSpecs.of(AppStyle.BRUTAL).chrome.grain)
        assertTrue(!StyleSpecs.of(AppStyle.MATERIAL).chrome.grain)
        assertTrue(!StyleSpecs.of(AppStyle.CUPPERTINO).chrome.grain)
        // 全大写标签只有编辑风与 Brutalist 有（Apple/Google/shadcn 都不做 all-caps）。
        assertTrue(StyleSpecs.of(AppStyle.AWWWARDS).type.uppercaseLabels)
        assertTrue(!StyleSpecs.of(AppStyle.CUPPERTINO).type.uppercaseLabels)
        assertTrue(!StyleSpecs.of(AppStyle.MATERIAL).type.uppercaseLabels)
        // 底栏选中态三种画法都要被用到，否则等于这个开关没实现。
        val indicators = all.map { StyleSpecs.of(it).chrome.tabIndicator }.toSet()
        assertEquals(3, indicators.size)
    }

    @Test
    fun densityStaysNearUnity() {
        all.forEach { style ->
            val d = StyleSpecs.of(style).density
            assertTrue("${style.id} density=$d 超出合理范围", d in 0.8f..1.2f)
        }
    }

    @Test
    fun motionDurationsGrowFastAndSlow() {
        all.forEach { style ->
            val m = StyleSpecs.of(style).motion
            assertTrue("${style.id} fast=${m.fast} base=${m.base}", m.fast < m.base)
            assertTrue("${style.id} base=${m.base} slow=${m.slow}", m.base < m.slow)
        }
    }

    /** sRGB 相对亮度的粗略近似（0..1），够用来挡明显的对比灾难。 */
    private fun luma(c: androidx.compose.ui.graphics.Color): Float =
        0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
}