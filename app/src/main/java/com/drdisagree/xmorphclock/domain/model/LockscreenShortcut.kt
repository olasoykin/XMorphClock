package com.drdisagree.xmorphclock.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.drdisagree.xmorphclock.R

enum class ShortcutSlot(val systemSlotId: String) {
    LEFT("bottom_start"),
    RIGHT("bottom_end")
}

enum class SystemShortcut(
    val id: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int
) {
    NONE("none", R.string.shortcut_none, R.drawable.ic_shortcut_none),
    CAMERA("camera", R.string.shortcut_camera, R.drawable.ic_shortcut_camera),
    FLASHLIGHT("flashlight", R.string.shortcut_flashlight, R.drawable.ic_shortcut_flashlight),
    DO_NOT_DISTURB("do_not_disturb", R.string.shortcut_dnd, R.drawable.ic_shortcut_dnd),
    QR_CODE_SCANNER("qr_code_scanner", R.string.shortcut_qr_scanner, R.drawable.ic_shortcut_qr),
    WALLET("wallet", R.string.shortcut_wallet, R.drawable.ic_shortcut_wallet),
    HOME("home", R.string.shortcut_home, R.drawable.ic_shortcut_home),
    MUTE("mute", R.string.shortcut_mute, R.drawable.ic_shortcut_mute),
    VIDEO_CAMERA("video_camera", R.string.shortcut_video_camera, R.drawable.ic_shortcut_video);

    companion object {
        val DEFAULT_LEFT = FLASHLIGHT
        val DEFAULT_RIGHT = CAMERA

        fun fromId(raw: String?): SystemShortcut? =
            raw?.trim()?.lowercase()?.let { normalized ->
                entries.firstOrNull { it.id == normalized }
            }

        fun resolve(raw: String?, fallback: SystemShortcut): SystemShortcut =
            fromId(raw) ?: fallback
    }
}

data class LockscreenAffordance(
    val id: String,
    val name: String? = null,
    val isEnabled: Boolean = true,
    val explanation: String? = null
) {
    val knownShortcut: SystemShortcut?
        get() = SystemShortcut.fromId(id)

    val iconRes: Int
        get() = knownShortcut?.iconRes ?: when (id.trim().lowercase()) {
            "create_note", "note" -> R.drawable.ic_document
            "dnd" -> R.drawable.ic_shortcut_dnd
            "device_controls", "controls" -> R.drawable.ic_shortcut_home
            "qr_scanner" -> R.drawable.ic_shortcut_qr
            else -> R.drawable.ic_lock_open
        }

    val isNone: Boolean
        get() = id.equals(SystemShortcut.NONE.id, ignoreCase = true)

    fun displayLabel(fallbackLabel: String?): String =
        name?.takeIf { it.isNotBlank() && !it.equals(id, ignoreCase = true) }
            ?: fallbackLabel
            ?: name?.takeIf { it.isNotBlank() }
            ?: id.replace('_', ' ').replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }

    companion object {
        fun defaults(): List<LockscreenAffordance> =
            SystemShortcut.entries.map { LockscreenAffordance(id = it.id) }
    }
}
