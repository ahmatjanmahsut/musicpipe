package com.musicpipe.player.media.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.16.0 均衡器的纯逻辑回归。
 *
 * 重点在"曲线到设备"的那一步：预设是 5 段写死的，而真实设备的段数与中心频率千奇百怪，
 * 这里一旦算错，用户听到的就是"高音没了"或者"低音糊成一团"，且很难在 UI 上看出来。
 */
class EqModelTest {

    @Test
    fun canonicalBandsAreSortedAndUnique() {
        val hz = EqBands.canonical.map { it.hz }
        assertEquals(EqBands.COUNT, hz.size)
        assertEquals(hz.sorted(), hz)
        assertEquals(hz.distinct().size, hz.size)
    }

    @Test
    fun clampGainHonoursLimitsAndZeroesTinyValues() {
        assertEquals(EqBands.MAX_DB, EqBands.clampGain(99f), 0.001f)
        assertEquals(EqBands.MIN_DB, EqBands.clampGain(-99f), 0.001f)
        assertEquals(0f, EqBands.clampGain(0.01f), 0.001f)
        assertEquals(0f, EqBands.clampGain(Float.NaN), 0.001f)
        assertEquals(3f, EqBands.clampGain(3f), 0.001f)
    }

    @Test
    fun normalizePadsAndTruncatesToCanonicalLength() {
        assertEquals(5, EqBands.normalize(listOf(1f, 2f)).size)
        assertEquals(0f, EqBands.normalize(listOf(1f, 2f))[4], 0.001f)
        val tooMany = EqBands.normalize(listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f))
        assertEquals(5, tooMany.size)
        assertEquals(5f, tooMany[4], 0.001f)
        assertEquals(EqBands.zero, EqBands.normalize(emptyList()))
    }

    /** 设备恰好就是标准 5 段时，曲线必须原样落地（不能因为重采样被改掉）。 */
    @Test
    fun resampleIsIdentityOnStandardFiveBands() {
        val gains = listOf(-3f, -1.5f, 2f, 3f, -1.5f)
        val out = EqBands.resample(gains, EqBands.canonical.map { it.hz })
        gains.forEachIndexed { i, g -> assertEquals(g, out[i], 0.05f) }
    }

    @Test
    fun resampleInterpolatesOnDenserDevice() {
        val gains = listOf(0f, 0f, 0f, 0f, 0f)
        assertEquals(0f, EqBands.resample(gains, listOf(60, 300, 1000, 3000, 6000, 12000))[2], 0.001f)

        // 单段抬升：在 910Hz 与 3600Hz 之间应当被插出一段"缓坡"，而不是台阶。
        val bump = listOf(0f, 0f, 6f, 0f, 0f)
        val out = EqBands.resample(bump, listOf(910, 1800, 3600))
        assertEquals(6f, out[0], 0.001f)
        assertTrue("中间点应介于 0 与 6 之间，实际 " + out[1], out[1] > 0.1f && out[1] < 5.9f)
        assertEquals(0f, out[2], 0.001f)
    }

    @Test
    fun resampleClampsAtBothEndsWithoutExtrapolating() {
        val gains = listOf(-2f, 0f, 0f, 0f, 5f)
        val out = EqBands.resample(gains, listOf(20, 40000))
        assertEquals(-2f, out[0], 0.001f)
        assertEquals(5f, out[1], 0.001f)
    }

    @Test
    fun millibelConversionRoundTrips() {
        assertEquals(300, EqBands.dbToMillibel(3f))
        assertEquals(3f, EqBands.millibelToDb(300), 0.001f)
        assertEquals(-150, EqBands.dbToMillibel(-1.5f))
    }
}
