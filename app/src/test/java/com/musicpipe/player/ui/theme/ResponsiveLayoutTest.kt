package com.musicpipe.player.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutTest {
    @Test fun wideBaojunDisplayUsesCarLayout() { assertTrue(ResponsiveLayout.isWideCar(960, 360)) }
    @Test fun portraitPhoneKeepsPhoneLayout() { assertFalse(ResponsiveLayout.isWideCar(360, 800)) }
    @Test fun modernLandscapePhoneMayUseWideLayout() { assertTrue(ResponsiveLayout.isWideCar(740, 360)) }
}
