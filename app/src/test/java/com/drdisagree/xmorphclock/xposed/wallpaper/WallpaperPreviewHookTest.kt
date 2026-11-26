package com.drdisagree.xmorphclock.xposed.wallpaper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPreviewHookTest {

    @Test
    fun matchesClockDigitsAndPreviewDatesWithoutFalsePositives() {
        assertTrue(WallpaperPreviewHook.isPreviewClockOrDateText("11"))
        assertTrue(WallpaperPreviewHook.isPreviewClockOrDateText("17"))
        assertTrue(WallpaperPreviewHook.isPreviewClockOrDateText("11:17"))
        assertTrue(WallpaperPreviewHook.isPreviewClockOrDateText("11:17 AM"))
        assertTrue(WallpaperPreviewHook.isPreviewClockOrDateText("jue, 26 nov"))
        assertTrue(WallpaperPreviewHook.isPreviewClockOrDateText("Thu, Nov 26"))

        assertFalse(WallpaperPreviewHook.isPreviewClockOrDateText(""))
        assertFalse(WallpaperPreviewHook.isPreviewClockOrDateText("Reloj: color, tamaño"))
        assertFalse(WallpaperPreviewHook.isPreviewClockOrDateText("Cambiar fondo de pantalla"))
        assertFalse(WallpaperPreviewHook.isPreviewClockOrDateText("Tap to customize clock"))
        assertFalse(WallpaperPreviewHook.isPreviewClockOrDateText("4/4"))
    }
}
