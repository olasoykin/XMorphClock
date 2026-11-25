package com.drdisagree.xmorphclock.data.source

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.drdisagree.xmorphclock.data.mapper.CompositionJsonMapper
import com.drdisagree.xmorphclock.domain.model.Composition
import com.drdisagree.xmorphclock.domain.model.ClockSettings
import com.drdisagree.xmorphclock.domain.model.ShortcutSlot
import com.drdisagree.xmorphclock.domain.model.SystemShortcut
import com.drdisagree.xmorphclock.domain.model.ThemeMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

class LocalPrefsSource(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PrefKeys.GROUP, Context.MODE_PRIVATE)

    val current: ClockSettings
        get() = ClockSettings(
            clockEnabled = prefs.getBoolean(PrefKeys.CLOCK_ENABLED, PrefKeys.DEFAULT_CLOCK_ENABLED),
            faceStyle = prefs.getInt(PrefKeys.FACE_STYLE, ClockFaces.STYLE_BASE),
            burnInProtection = prefs.getBoolean(PrefKeys.BURN_IN, PrefKeys.DEFAULT_BURN_IN),
            themeMode = prefs.getString(PrefKeys.THEME_MODE, null)
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            composition = CompositionJsonMapper.fromJson(prefs.getString(PrefKeys.COMPOSITION, null)),
            shortcutLeft = prefs.getString(PrefKeys.SHORTCUT_LEFT, null)?.trim()?.takeIf { it.isNotEmpty() } ?: SystemShortcut.DEFAULT_LEFT.id,
            shortcutRight = prefs.getString(PrefKeys.SHORTCUT_RIGHT, null)?.trim()?.takeIf { it.isNotEmpty() } ?: SystemShortcut.DEFAULT_RIGHT.id
        )

    val hasConfiguredShortcuts: Boolean
        get() = prefs.contains(PrefKeys.SHORTCUT_LEFT) || prefs.contains(PrefKeys.SHORTCUT_RIGHT)

    val isOnboardingDone: Boolean
        get() = prefs.getBoolean(PrefKeys.ONBOARDING_DONE, false)

    val settings: Flow<ClockSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in SETTINGS_KEYS) trySend(current)
        }
        trySend(current)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun setClockEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(PrefKeys.CLOCK_ENABLED, enabled) }
    }

    val faceJson: String?
        get() = prefs.getString(PrefKeys.FACE_JSON, null)

    fun setFace(style: Int, json: String) {
        prefs.edit {
            putInt(PrefKeys.FACE_STYLE, style)
            putString(PrefKeys.FACE_JSON, json)
        }
    }

    fun setComposition(composition: Composition) {
        prefs.edit { putString(PrefKeys.COMPOSITION, CompositionJsonMapper.toJson(composition)) }
    }

    fun setBurnInProtection(enabled: Boolean) {
        prefs.edit { putBoolean(PrefKeys.BURN_IN, enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(PrefKeys.THEME_MODE, mode.name) }
    }

    fun setOnboardingDone(done: Boolean) {
        prefs.edit { putBoolean(PrefKeys.ONBOARDING_DONE, done) }
    }

    val shortcutLeftStamp: Long
        get() = prefs.getLong(PrefKeys.SHORTCUT_LEFT_STAMP, 0L)

    val shortcutRightStamp: Long
        get() = prefs.getLong(PrefKeys.SHORTCUT_RIGHT_STAMP, 0L)

    fun setShortcut(slot: ShortcutSlot, affordanceId: String): Long {
        val key = if (slot == ShortcutSlot.LEFT) PrefKeys.SHORTCUT_LEFT else PrefKeys.SHORTCUT_RIGHT
        val stampKey = if (slot == ShortcutSlot.LEFT) PrefKeys.SHORTCUT_LEFT_STAMP else PrefKeys.SHORTCUT_RIGHT_STAMP
        val nextStamp = maxOf(System.currentTimeMillis(), prefs.getLong(stampKey, 0L) + 1L)
        prefs.edit {
            putString(key, affordanceId)
            putLong(stampKey, nextStamp)
        }
        return nextStamp
    }

    fun syncShortcuts(left: String?, right: String?) {
        prefs.edit {
            left?.let { putString(PrefKeys.SHORTCUT_LEFT, it) }
            right?.let { putString(PrefKeys.SHORTCUT_RIGHT, it) }
        }
    }
}

private val SETTINGS_KEYS = setOf(
    PrefKeys.CLOCK_ENABLED,
    PrefKeys.FACE_STYLE,
    PrefKeys.BURN_IN,
    PrefKeys.THEME_MODE,
    PrefKeys.COMPOSITION,
    PrefKeys.SHORTCUT_LEFT,
    PrefKeys.SHORTCUT_RIGHT
)
