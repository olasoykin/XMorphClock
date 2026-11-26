package com.drdisagree.xmorphclock.xposed

import com.drdisagree.xmorphclock.xposed.hook.HookEnv
import com.drdisagree.xmorphclock.xposed.systemui.SystemUiModule
import com.drdisagree.xmorphclock.xposed.wallpaper.WallpaperPickerModule
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class ModuleMain : XposedModule() {

    private var processName: String? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return
        when {
            param.packageName == SystemUiModule.PACKAGE -> {
                HookEnv.init(this, param.classLoader, param.packageName)
                if (processName == null || processName == SystemUiModule.PACKAGE) {
                    SystemUiModule.start()
                } else {
                    SystemUiModule.startPreviewOnly()
                }
            }
            param.packageName in WallpaperPickerModule.PACKAGES -> {
                HookEnv.init(this, param.classLoader, param.packageName)
                WallpaperPickerModule.start()
            }
        }
    }
}
