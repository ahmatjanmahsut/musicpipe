package com.musicpipe.player.media.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 输出设备分类（v1.16.0）。
 *
 * 这一层决定两件事：车机要不要显示歌词、EQ 该推荐哪套预设。
 * 判错不会崩，但会让人在车里对着屏幕怀疑人生，所以把已知的设备名都钉成用例。
 */
class AudioOutputClassifierTest {

    @Test
    fun mazdaHeadUnitsAreRecognisedAsCar() {
        listOf("Mazda", "MAZDA", "MZD Connect", "Mazda6", "CMU").forEach { name ->
            val s = AudioOutputClassifier.classify("bluetooth_a2dp", name, true)
            assertEquals(name, AudioOutputKind.BT_CAR, s.kind)
            assertTrue(s.isCar)
            assertEquals(EqTargetMapper.car(), s.kind.toEqTarget())
        }
    }

    @Test
    fun otherCarBrandsAndChineseNamesAreAlsoCar() {
        listOf("Toyota", "BMW 5", "BYD Han", "车机", "Car Multimedia Head Unit").forEach { name ->
            assertEquals(name, AudioOutputKind.BT_CAR, AudioOutputClassifier.guessFromName(name))
        }
    }

    @Test
    fun sonyHeadphonesAreHeadphonesNotCar() {
        listOf("WH-1000XM5", "SONY WH-1000XM4", "WF-1000XM5", "Buds", "AirPods Pro", "FreeBuds 5").forEach { name ->
            assertEquals(name, AudioOutputKind.BT_HEADPHONE, AudioOutputClassifier.guessFromName(name))
        }
    }

    @Test
    fun unknownBluetoothFallsBackToOtherInsteadOfPretendingToBeACar() {
        val s = AudioOutputClassifier.classify("bluetooth_a2dp", "BT-SPEAKER-8812", true)
        assertEquals(AudioOutputKind.BT_OTHER, s.kind)
        assertFalse(s.isCar)
        assertEquals(EqTargetMapper.generic(), s.kind.toEqTarget())
    }

    @Test
    fun wiredAndSpeakerOutputsAreClassifiedWithoutBluetooth() {
        assertEquals(
            AudioOutputKind.SPEAKER,
            AudioOutputClassifier.classify("builtin_speaker", "", false).kind
        )
        assertEquals(
            AudioOutputKind.WIRED,
            AudioOutputClassifier.classify("wired_headset", "3.5mm", false).kind
        )
    }

    @Test
    fun emptyNameYieldsUnknownRatherThanCar() {
        assertEquals(AudioOutputKind.UNKNOWN, AudioOutputClassifier.classify("", "", false).kind)
    }

    /** 分类 → EQ 场景 的映射（车机→CEL，耳机/有线→耳机，其它→通用）。 */
    private object EqTargetMapper {
        fun car() = com.musicpipe.player.media.eq.EqTargetDevice.CAR
        fun generic() = com.musicpipe.player.media.eq.EqTargetDevice.GENERIC
    }
}
