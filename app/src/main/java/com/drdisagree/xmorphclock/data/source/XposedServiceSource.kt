package com.drdisagree.xmorphclock.data.source

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import com.drdisagree.xmorphclock.domain.model.FrameworkInfo
import java.io.File
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object XposedServiceSource {

    private val current = MutableStateFlow<FrameworkInfo?>(null)
    private val bindListeners = CopyOnWriteArrayList<() -> Unit>()
    private var registered = false
    private var mirror: SharedPreferences? = null
    private var sharedDir: File? = null
    private var mirrorListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    val service: StateFlow<FrameworkInfo?> = current.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (registered) return
        registered = true
        val app = context.applicationContext
        sharedDir = RemotePrefsContract.sharedDir(app)
        val prefs = app.getSharedPreferences(RemotePrefsContract.MIRROR, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == RemotePrefsContract.COMPLETE_MARKER) return@OnSharedPreferenceChangeListener
            runCatching { app.contentResolver.notifyChange(RemotePrefsContract.changedUri(key), null) }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        mirror = prefs
        mirrorListener = listener
    }

    fun addBindListener(listener: () -> Unit) {
        bindListeners += listener
        if (current.value != null) listener()
    }

    fun edit(block: (SharedPreferences.Editor) -> Unit) {
        mirror?.edit()?.also(block)?.apply()
    }

    fun readString(key: String): String? = null

    fun writeFile(name: String, block: (OutputStream) -> Unit): Boolean {
        val local = sharedFile(name) ?: return false
        val stream = try {
            local.startWrite()
        } catch (_: Throwable) {
            return false
        }
        try {
            block(stream)
            local.finishWrite(stream)
        } catch (_: Throwable) {
            local.failWrite(stream)
            return false
        }
        return true
    }

    fun deleteFile(name: String) {
        runCatching { sharedFile(name)?.delete() }
    }

    private fun sharedFile(name: String): AtomicFile? {
        val dir = sharedDir ?: return null
        if (!dir.isDirectory && !dir.mkdirs()) return null
        return AtomicFile(File(dir, name))
    }
}
