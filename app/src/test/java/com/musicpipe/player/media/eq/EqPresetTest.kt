package com.musicpipe.player.media.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.16.0 预设目录与持久化编码。
 *
 * 需求原文要求"至少包含连接蓝牙车机时的预设（如 Mazda）与连接蓝牙耳机时的预设（如 SONY 1000XM5）"，
 * 这里直接把那条需求钉成测试：预设没了 / 曲线长度错了 / 推荐指到别的预设，CI 就会红。
 */
class EqPresetTest {

    @Test
    fun builtInsContainCarAndHeadphonePresetsForTheNamedDevices() {
        val mazda = EqPresets.byId(EqPresets.CAR_MAZDA_ID)
        assertNotNull("缺少 Mazda 车载预设", mazda)
        assertTrue(mazda!!.name.contains("Mazda"))
        assertEquals(EqTargetDevice.CAR, mazda.hint)

        val sony = EqPresets.byId(EqPresets.HP_SONY)
        assertNotNull("缺少 SONY 1000XM5 预设", sony)
        assertTrue(sony!!.name.contains("1000XM5"))
        assertEquals(EqTargetDevice.HEADPHONE, sony.hint)

        assertTrue("车载预设至少两个", EqPresets.builtIns.count { it.hint == EqTargetDevice.CAR } >= 2)
        assertTrue("耳机预设至少两个", EqPresets.builtIns.count { it.hint == EqTargetDevice.HEADPHONE } >= 2)
    }

    @Test
    fun everyPresetHasAValidFiveBandCurve() {
        EqPresets.builtIns.forEach { p ->
            assertEquals(p.id, EqBands.COUNT, p.gains.size)
            p.gains.forEach { g ->
                assertTrue(p.id + " 增益越界: " + g, g >= EqBands.MIN_DB && g <= EqBands.MAX_DB)
            }
            assertTrue(p.id + " makeup 越界", p.makeupDb >= 0f && p.makeupDb <= EqBands.MAX_MAKEUP_DB)
            assertTrue(p.id + " 缺 tagline", p.tagline.isNotBlank())
        }
    }

    @Test
    fun flatPresetIsSilentAndIsTheDefault() {
        val flat = EqPresets.default()
        assertEquals(EqPresets.FLAT_ID, flat.id)
        assertEquals(EqBands.zero, flat.gains)
        assertEquals(0f, flat.makeupDb, 0.001f)
    }

    @Test
    fun recommendationFollowsTheDetectedDevice() {
        assertEquals(EqPresets.CAR_MAZDA_ID, EqPresets.forOutput(EqTargetDevice.CAR, "Mazda").id)
        assertEquals(EqPresets.CAR_MAZDA_ID, EqPresets.forOutput(EqTargetDevice.CAR, "MZD Connect").id)
        assertEquals(EqPresets.CAR_GENERIC_ID, EqPresets.forOutput(EqTargetDevice.CAR, "Car Multimedia").id)
        assertEquals(EqPresets.HP_SONY, EqPresets.forOutput(EqTargetDevice.HEADPHONE, "WH-1000XM5").id)
        assertEquals(EqPresets.HP_GENERIC_ID, EqPresets.forOutput(EqTargetDevice.HEADPHONE, "Buds").id)
        assertEquals(EqPresets.FLAT_ID, EqPresets.forOutput(EqTargetDevice.GENERIC, "").id)
    }

    @Test
    fun gainsCodecRoundTripsAndRejectsGarbage() {
        val gains = listOf(-2.5f, 0f, 1.5f, 3f, -1f)
        assertEquals(gains, EqPresetCodec.decodeGains(EqPresetCodec.encodeGains(gains)))
        assertEquals(EqBands.zero, EqPresetCodec.decodeGains(null))
        assertEquals(EqBands.zero, EqPresetCodec.decodeGains(""))
        assertEquals(EqBands.zero, EqPresetCodec.decodeGains("a,b,c"))
        // 越界值要被钳住，而不是原样落盘
        assertEquals(EqBands.MAX_DB, EqPresetCodec.decodeGains("40")[0], 0.001f)
    }

    @Test
    fun customPresetCodecSurvivesNamesWithSeparatorsAndNewlines() {
        val presets = listOf(
            EqPreset("user_1", "我的\t夜间", "自定义 · 60-3", listOf(-3f, 0f, 1f, 2f, 0f), 1f, EqTargetDevice.GENERIC, builtIn = false),
            EqPreset("user_2", "车机\n重低音", "自定义 · 增强", listOf(4f, 2f, 0f, 0f, -1f), 0f, EqTargetDevice.CAR, builtIn = false)
        )
        val decoded = EqPresetCodec.decodeCustom(EqPresetCodec.encodeCustom(presets))
        assertEquals(2, decoded.size)
        assertEquals("user_1", decoded[0].id)
        assertEquals("我的 夜间", decoded[0].name)
        assertEquals(presets[0].gains, decoded[0].gains)
        assertEquals(1f, decoded[0].makeupDb, 0.001f)
        assertEquals("user_2", decoded[1].id)
        assertEquals("车机 重低音", decoded[1].name)
        assertTrue(decoded.all { !it.builtIn })
    }

    @Test
    fun customPresetCodecToleratesEmptyAndBrokenInput() {
        assertTrue(EqPresetCodec.decodeCustom(null).isEmpty())
        assertTrue(EqPresetCodec.decodeCustom("").isEmpty())
        assertTrue(EqPresetCodec.decodeCustom("garbage-line").isEmpty())
        assertTrue(EqPresetCodec.decodeCustom("\n\n").isEmpty())
    }

    @Test
    fun nameOfFallsBackToCustomWhenIdIsUnknown() {
        assertEquals("平直", EqPresets.nameOf(EqPresets.FLAT_ID))
        assertEquals("自定义", EqPresets.nameOf(null))
        assertEquals("自定义", EqPresets.nameOf("nope"))
        val custom = listOf(EqPreset("user_9", "我的", "", EqBands.zero, 0f, EqTargetDevice.GENERIC, builtIn = false))
        assertEquals("我的", EqPresets.nameOf("user_9", custom))
        assertNull(EqPresets.byId("user_9"))
    }
}
