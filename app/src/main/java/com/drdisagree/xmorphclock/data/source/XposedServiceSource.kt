package com.drdisagree.xmorphclock.data.source

import android.content.Context
import android.content.SharedPreferences
import android.os.DeadObjectException
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import com.drdisagree.xmorphclock.domain.model.FrameworkInfo
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object XposedServiceSource {

    private val current = MutableStateFlow<FrameworkInfo?>(null)
    private val bindListeners = CopyOnWriteArrayList<() -> Unit>()
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()

    @Volatile
    private var xposedService: XposedService? = null

    @Volatile
    private var remotePrefs: SharedPreferences? = null

    @Volatile
    var isPreviewOnly: Boolean = false
        private set

    private var registered = false
    private var mirror: SharedPreferences? = null
    private var sharedDir: File? = null
    private var mirrorListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    val service: StateFlow<FrameworkInfo?> = current.asStateFlow()

    private val serviceListener = object : XposedServiceHelper.OnServiceListener {
        override fun onServiceBind(service: XposedService) {
            val prefs = runCatching { service.getRemotePreferences(PrefKeys.GROUP) }.getOrNull()
            val info = readFrameworkInfo(service)
            synchronized(lock) {
                xposedService = service
                remotePrefs = prefs
                current.value = info
            }
            executor.execute {
                bindListeners.forEach { listener -> runCatching { listener() } }
            }
        }

        override fun onServiceDied(service: XposedService) {
            synchronized(lock) {
                if (xposedService === service) {
                    xposedService = null
                    remotePrefs = null
                    current.value = null
                }
            }
        }
    }

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
        runCatching { XposedServiceHelper.registerListener(serviceListener) }
    }

    fun refresh() {
        val bound = xposedService ?: return
        executor.execute {
            try {
                val info = FrameworkInfo(
                    name = bound.frameworkName,
                    version = bound.frameworkVersion,
                    apiVersion = bound.apiVersion,
                    scope = bound.scope.toList()
                )
                if (remotePrefs == null) {
                    remotePrefs = runCatching { bound.getRemotePreferences(PrefKeys.GROUP) }.getOrNull()
                }
                current.value = info
            } catch (throwable: Throwable) {
                if (throwable.cause is DeadObjectException) {
                    synchronized(lock) {
                        if (xposedService === bound) {
                            xposedService = null
                            remotePrefs = null
                            current.value = null
                        }
                    }
                }
            }
        }
    }

    fun addBindListener(listener: () -> Unit) {
        bindListeners += listener
        if (xposedService != null) {
            executor.execute { runCatching { listener() } }
        }
    }

    fun setPreviewOnly(enabled: Boolean) {
        isPreviewOnly = enabled
    }

    fun edit(block: (SharedPreferences.Editor) -> Unit) {
        if (isPreviewOnly) return
        mirror?.edit()?.also(block)?.apply()
        runCatching { remotePrefs?.edit()?.also(block)?.apply() }
    }

    fun readString(key: String): String? =
        runCatching { remotePrefs?.getString(key, null) }.getOrNull()
            ?: mirror?.getString(key, null)

    fun writeFile(name: String, block: (OutputStream) -> Unit): Boolean {
        if (isPreviewOnly) return false
        val local = sharedFile(name)
        var localWritten = false
        if (local != null) {
            val stream = try {
                local.startWrite()
            } catch (_: Throwable) {
                null
            }
            if (stream != null) {
                try {
                    block(stream)
                    local.finishWrite(stream)
                    localWritten = true
                } catch (_: Throwable) {
                    local.failWrite(stream)
                }
            }
        }
        val remoteWritten = writeRemoteFile(name, local?.takeIf { localWritten }, block)
        return localWritten || remoteWritten
    }

    fun deleteFile(name: String) {
        if (isPreviewOnly) return
        runCatching { sharedFile(name)?.delete() }
        runCatching { xposedService?.deleteRemoteFile(name) }
    }

    private fun writeRemoteFile(
        name: String,
        source: AtomicFile?,
        block: (OutputStream) -> Unit
    ): Boolean {
        val bound = xposedService ?: return false
        return try {
            val pfd: ParcelFileDescriptor = bound.openRemoteFile(name)
            pfd.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { out ->
                    runCatching { out.channel.truncate(0L) }
                    if (source != null) {
                        source.openRead().use { input -> input.copyTo(out) }
                    } else {
                        block(out)
                    }
                    out.flush()
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun readFrameworkInfo(service: XposedService): FrameworkInfo? = try {
        FrameworkInfo(
            name = service.frameworkName,
            version = service.frameworkVersion,
            apiVersion = service.apiVersion,
            scope = service.scope.toList()
        )
    } catch (_: Throwable) {
        null
    }

    private fun sharedFile(name: String): AtomicFile? {
        val dir = sharedDir ?: return null
        if (!dir.isDirectory && !dir.mkdirs()) return null
        return AtomicFile(File(dir, name))
    }
}
