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

object LockscreenShortcutsHook {

    private const val HOOK_ID = "lockscreen_shortcuts"
    private const val REPOSITORY_CLASS = "com.android.systemui.keyguard.data.repository.KeyguardQuickAffordanceRepository"
    private const val SELECTIONS_URI = "content://com.android.systemui.customization/lockscreen_quickaffordance/selections"
    private const val STATE_PREFS = "xmorph_shortcuts_state"
    private const val KEY_APPLIED_LEFT_STAMP = "applied_left_stamp"
    private const val KEY_APPLIED_RIGHT_STAMP = "applied_right_stamp"
    private const val COL_SLOT_ID = "slot_id"
    private const val COL_AFFORDANCE_ID = "affordance_id"
    private const val RETRY_MS = 1_500L
    private const val MAX_TRIES = 10

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "XMorph-Shortcuts").apply { isDaemon = true }
    }

    @Volatile
    private var repoRef: WeakReference<Any>? = null

    @Volatile
    private var setSelectionsMethod: Method? = null

    @Volatile
    private var memLeftStamp: Long = 0L

    @Volatile
    private var memRightStamp: Long = 0L

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
        HookSafety.runGuarded(HOOK_ID, maxCallsPerSecond = 5, maxConsecutiveErrors = 3) {
            val left = ModulePrefs.shortcutLeft
            val leftStamp = ModulePrefs.shortcutLeftStamp
            val right = ModulePrefs.shortcutRight
            val rightStamp = ModulePrefs.shortcutRightStamp

            if (left != null && leftStamp > 0L && leftStamp != appliedStamp(ShortcutSlot.LEFT)) {
                if (applySlot(ShortcutSlot.LEFT, left)) {
                    saveAppliedStamp(ShortcutSlot.LEFT, leftStamp)
                }
            }
            if (right != null && rightStamp > 0L && rightStamp != appliedStamp(ShortcutSlot.RIGHT)) {
                if (applySlot(ShortcutSlot.RIGHT, right)) {
                    saveAppliedStamp(ShortcutSlot.RIGHT, rightStamp)
                }
            }
        }
    }

    private fun appliedStamp(slot: ShortcutSlot): Long {
        val mem = if (slot == ShortcutSlot.LEFT) memLeftStamp else memRightStamp
        if (mem > 0L) return mem
        val key = if (slot == ShortcutSlot.LEFT) KEY_APPLIED_LEFT_STAMP else KEY_APPLIED_RIGHT_STAMP
        val stored = runCatching {
            HostApplication.context()
                ?.getSharedPreferences(STATE_PREFS, android.content.Context.MODE_PRIVATE)
                ?.getLong(key, 0L)
        }.getOrNull() ?: 0L
        if (stored > 0L) {
            if (slot == ShortcutSlot.LEFT) memLeftStamp = stored else memRightStamp = stored
        }
        return stored
    }

    private fun saveAppliedStamp(slot: ShortcutSlot, stamp: Long) {
        if (slot == ShortcutSlot.LEFT) memLeftStamp = stamp else memRightStamp = stamp
        val key = if (slot == ShortcutSlot.LEFT) KEY_APPLIED_LEFT_STAMP else KEY_APPLIED_RIGHT_STAMP
        runCatching {
            HostApplication.context()
                ?.getSharedPreferences(STATE_PREFS, android.content.Context.MODE_PRIVATE)
                ?.edit()
                ?.putLong(key, stamp)
                ?.apply()
        }
    }

    private fun applySlot(slot: ShortcutSlot, shortcutId: String): Boolean {
        val isNone = shortcutId.equals(SystemShortcut.NONE.id, ignoreCase = true)
        val repo = repoRef?.get()
        val method = setSelectionsMethod
        if (repo != null && method != null) {
            val repoResult = runCatching {
                val ids = if (isNone) emptyList() else listOf(shortcutId)
                method.invoke(repo, slot.systemSlotId, ids)
            }
            if (repoResult.isSuccess) return true
            HookEnv.log("Failed setting ${slot.name.lowercase()} shortcut on repository", repoResult.exceptionOrNull())
        }

        val resolver = HostApplication.get()?.contentResolver ?: return false
        val uri = Uri.parse(SELECTIONS_URI)
        return runCatching {
            deleteSlotSelections(resolver, uri, slot.systemSlotId)
            if (!isNone) {
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
