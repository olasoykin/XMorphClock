package com.drdisagree.xmorphclock.xposed.wallpaper

import com.drdisagree.xmorphclock.engine.FaceFont
import com.drdisagree.xmorphclock.engine.FaceImages
import com.drdisagree.xmorphclock.xposed.clock.RemoteFonts
import com.drdisagree.xmorphclock.xposed.clock.RemoteImages
import com.drdisagree.xmorphclock.xposed.hook.HookEnv
import com.drdisagree.xmorphclock.xposed.hook.HookSafety
import com.drdisagree.xmorphclock.xposed.hook.HostApplication
import com.drdisagree.xmorphclock.xposed.prefs.ModulePrefs

object WallpaperPickerModule {

    val PACKAGES = setOf(
        "com.android.wallpaper",
        "com.google.android.apps.wallpaper",
        "com.google.android.apps.wallpaper.pixel"
    )

    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        val module = HookEnv.module
        if (HookSafety.beginSession(HostApplication.get())) {
            return
        }
        ModulePrefs.init(module)
        FaceImages.init(RemoteImages::load)
        FaceFont.setUserFonts(RemoteFonts::open)
        WallpaperPreviewHook.install()
    }
}
