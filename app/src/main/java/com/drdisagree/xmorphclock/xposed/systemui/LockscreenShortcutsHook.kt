package com.drdisagree.xmorphclock.xposed.systemui

import android.content.ContentValues
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.drdisagree.xmorphclock.domain.model.ShortcutSlot
import com.drdisagree.xmorphclock.domain.model.SystemShortcut
import com.drdisagree.xmorphclock.xposed.hook.HookEnv
import com.drdisagree.xmorphclock.xposed.hook.HookSafety
import com.drdisagree.xmorphclock.xposed.hook.HostApplication
import com.drdisagree.xmorphclock.xposed.hook.findMethod
import com.drdisagree.xmorphclock.xposed.hook.hookAfter
import com.drdisagree.xmorphclock.xposed.prefs.ModulePrefs
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object LockscreenShortcutsHook {

    private const val HOOK_ID = "lockscreen_shortcuts"
    private const val REPOSITORY_CLASS = "com.android.systemui.keyguard.data.repository.KeyguardQuickAffordanceRepository"
    private const val SELECTIONS_URI = "content://com.android.systemui.customization/lockscreen_quickaffordance/selections"
    private const val COL_SLOT_ID = "slot_id"
    private const val COL_AFFORDANCE_ID = "affordance_id"
    private const val RETRY_MS = 1_500L
    private const val MAX_TRIES = 10

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "XMorph-Shortcuts").apply { isDaemon = true }
    }
    private val applying = AtomicBoolean(false)

    @Volatile
    private var repoRef: WeakReference<Any>? = null

    @Volatile
    private var setSelectionsMethod: Method? = null

    @Volatile
    private var lastAppliedLeft: String? = null

    @Volatile
    private var lastAppliedRight: String? = null

    fun install() {
        runCatching {
            val repoClass = HookEnv.findClass(REPOSITORY_CLASS) ?: return@runCatching
            setSelectionsMethod = repoClass.findMethod("setSelections") { method ->
                method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == String::class.java &&
                    List::class.java.isAssignableFrom(method.parameterTypes[1])
            }
            repoClass.declaredConstructors.forEach { ctor ->
                ctor.isAccessible = true
                ctor.hookAfter { chain, _ ->
                    val instance = chain.thisObject ?: return@hookAfter
                    repoRef = WeakReference(instance)
                    scheduleApply()
                }
            }
        }.onFailure {
            HookEnv.log("KeyguardQuickAffordanceRepository hook skipped", it)
        }
        ModulePrefs.addListener(::scheduleApply)
        attach(0)
    }

    private fun attach(tries: Int) {
        if (HookSafety.isTripped(HOOK_ID)) return
        val host = HostApplication.get()
        if (host == null && repoRef?.get() == null) {
            if (tries < MAX_TRIES) mainHandler.postDelayed({ attach(tries + 1) }, RETRY_MS)
            return
        }
        scheduleApply()
    }

    private fun scheduleApply() {
        if (HookSafety.isTripped(HOOK_ID)) return
        worker.execute(::apply)
    }

    private fun apply() {
        if (!applying.compareAndSet(false, true)) return
        try {
            HookSafety.runGuarded(HOOK_ID, maxCallsPerSecond = 5, maxConsecutiveErrors = 3) {
                val left = ModulePrefs.shortcutLeft
                val right = ModulePrefs.shortcutRight
                if (left == null && right == null) return@runGuarded

                if (left != null && left != lastAppliedLeft) {
                    if (applySlot(ShortcutSlot.LEFT, left)) {
                        lastAppliedLeft = left
                    }
                }
                if (right != null && right != lastAppliedRight) {
                    if (applySlot(ShortcutSlot.RIGHT, right)) {
                        lastAppliedRight = right
                    }
                }
            }
        } finally {
            applying.set(false)
        }
    }

    private fun applySlot(slot: ShortcutSlot, shortcutId: String): Boolean {
        val repo = repoRef?.get()
        val method = setSelectionsMethod
        if (repo != null && method != null) {
            val repoResult = runCatching {
                val ids = if (shortcutId == SystemShortcut.NONE.id) emptyList() else listOf(shortcutId)
                method.invoke(repo, slot.systemSlotId, ids)
            }
            if (repoResult.isSuccess) return true
            HookEnv.log("Failed setting ${slot.name.lowercase()} shortcut on repository", repoResult.exceptionOrNull())
        }

        val resolver = HostApplication.get()?.contentResolver ?: return false
        val uri = Uri.parse(SELECTIONS_URI)
        return runCatching {
            deleteSlotSelections(resolver, uri, slot.systemSlotId)
            if (shortcutId != SystemShortcut.NONE.id) {
                val values = ContentValues().apply {
                    put(COL_SLOT_ID, slot.systemSlotId)
                    put(COL_AFFORDANCE_ID, shortcutId)
                }
                resolver.insert(uri, values)
            }
            true
        }.onFailure {
            HookEnv.log("Failed setting ${slot.name.lowercase()} shortcut via provider", it)
        }.getOrDefault(false)
    }

    private fun deleteSlotSelections(
        resolver: android.content.ContentResolver,
        uri: Uri,
        slotId: String
    ) {
        val oneArg = runCatching {
            resolver.delete(uri, "$COL_SLOT_ID=?", arrayOf(slotId))
        }
        if (oneArg.isSuccess) return

        val existingIds = ArrayList<String>(2)
        runCatching {
            resolver.query(uri, arrayOf(COL_SLOT_ID, COL_AFFORDANCE_ID), null, null, null)?.use { cursor ->
                val slotCol = cursor.getColumnIndex(COL_SLOT_ID)
                val affCol = cursor.getColumnIndex(COL_AFFORDANCE_ID)
                if (slotCol >= 0 && affCol >= 0) {
                    while (cursor.moveToNext()) {
                        if (cursor.getString(slotCol) == slotId) {
                            cursor.getString(affCol)?.let(existingIds::add)
                        }
                    }
                }
            }
        }
        existingIds.forEach { existingId ->
            runCatching {
                resolver.delete(uri, "$COL_SLOT_ID=? AND $COL_AFFORDANCE_ID=?", arrayOf(slotId, existingId))
            }
        }
    }
}
