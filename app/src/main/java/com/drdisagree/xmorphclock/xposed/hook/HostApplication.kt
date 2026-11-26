package com.drdisagree.xmorphclock.xposed.hook

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context

object HostApplication {

    private const val ACTIVITY_THREAD = "android.app.ActivityThread"
    private const val CURRENT_APPLICATION = "currentApplication"
    private const val CURRENT_THREAD = "currentActivityThread"
    private const val SYSTEM_CONTEXT = "getSystemContext"
    private const val SYSTEMUI = "com.android.systemui"

    @SuppressLint("PrivateApi")
    fun get(): Application? = try {
        Class.forName(ACTIVITY_THREAD).getMethod(CURRENT_APPLICATION).invoke(null) as? Application
    } catch (_: Throwable) {
        null
    }

    @SuppressLint("PrivateApi")
    fun systemContext(): Context? = try {
        val thread = Class.forName(ACTIVITY_THREAD).getMethod(CURRENT_THREAD).invoke(null)
        thread?.javaClass?.getMethod(SYSTEM_CONTEXT)?.invoke(thread) as? Context
    } catch (_: Throwable) {
        null
    }

    fun context(): Context? = get() ?: try {
        val pkg = HookEnv.packageName ?: SYSTEMUI
        systemContext()?.createPackageContext(pkg, 0)
    } catch (_: Throwable) {
        null
    }
}
