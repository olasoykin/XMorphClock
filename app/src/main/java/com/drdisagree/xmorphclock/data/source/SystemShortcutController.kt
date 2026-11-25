package com.drdisagree.xmorphclock.data.source

import com.drdisagree.xmorphclock.domain.model.LockscreenAffordance
import com.drdisagree.xmorphclock.domain.model.ShortcutSlot
import com.drdisagree.xmorphclock.domain.model.SystemShortcut

object SystemShortcutController {

    private const val SELECTIONS_URI = "content://com.android.systemui.customization/lockscreen_quickaffordance/selections"
    private const val AFFORDANCES_URI = "content://com.android.systemui.customization/lockscreen_quickaffordance/affordances"
    private val SLOT_ID_REGEX = Regex("""slot_id=([^,\s]+)""")
    private val AFFORDANCE_ID_REGEX = Regex("""affordance_id=([^,\s]+)""")
    private val ROW_PREFIX_REGEX = Regex("""^\s*Row:\s*\d+\s*""", RegexOption.IGNORE_CASE)
    private val KEY_VALUE_BOUNDARY_REGEX = Regex("""(?:^|,\s*)([a-zA-Z][a-zA-Z0-9_]*)=""")

    suspend fun apply(slot: ShortcutSlot, affordanceId: String): Boolean {
        if (XposedServiceSource.isPreviewOnly) return false
        // AOSP CustomizationProvider.delete requires selectionArgs which `content delete` CLI cannot pass;
        // `insert` replaces the slot selection directly, while clearing (NONE) is handled by LockscreenShortcutsHook via ContentResolver.
        if (affordanceId.equals(SystemShortcut.NONE.id, ignoreCase = true)) return false
        val safeAffordance = affordanceId.replace("'", "'\\''")
        val insertCmd = "content insert --uri $SELECTIONS_URI --bind slot_id:s:${slot.systemSlotId} --bind affordance_id:s:'$safeAffordance'"
        return RootShell.run(insertCmd)
    }

    suspend fun readSelections(): Map<ShortcutSlot, String>? {
        if (XposedServiceSource.isPreviewOnly) return null
        val raw = RootShell.read("content query --uri $SELECTIONS_URI --projection slot_id:affordance_id 2>/dev/null") {
            it.readBytes().decodeToString()
        }?.trim().orEmpty()
        if (raw.isEmpty() || (!raw.contains("Row:", ignoreCase = true) && !raw.contains("No result found", ignoreCase = true))) {
            return null
        }
        if (raw.contains("No result found", ignoreCase = true)) {
            return mapOf(
                ShortcutSlot.LEFT to SystemShortcut.NONE.id,
                ShortcutSlot.RIGHT to SystemShortcut.NONE.id
            )
        }
        val parsed = HashMap<ShortcutSlot, String>(2)
        raw.lineSequence().forEach { line ->
            val slotRaw = SLOT_ID_REGEX.find(line)?.groupValues?.get(1)?.trim() ?: return@forEach
            val slot = ShortcutSlot.entries.firstOrNull { it.systemSlotId == slotRaw } ?: return@forEach
            val affordanceId = AFFORDANCE_ID_REGEX.find(line)?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotEmpty() && !it.equals("NULL", ignoreCase = true) }
                ?: return@forEach
            parsed[slot] = affordanceId
        }
        if (parsed.isEmpty()) return null
        return mapOf(
            ShortcutSlot.LEFT to (parsed[ShortcutSlot.LEFT] ?: SystemShortcut.NONE.id),
            ShortcutSlot.RIGHT to (parsed[ShortcutSlot.RIGHT] ?: SystemShortcut.NONE.id)
        )
    }

    suspend fun readAvailableAffordances(): List<LockscreenAffordance>? {
        if (XposedServiceSource.isPreviewOnly) return null
        val raw = RootShell.read(
            "content query --uri $AFFORDANCES_URI --projection id:name:is_enabled:enablement_explanation 2>/dev/null"
        ) {
            it.readBytes().decodeToString()
        }?.trim().orEmpty()
        return parseAvailableAffordances(raw)
    }

    internal fun parseAvailableAffordances(raw: String): List<LockscreenAffordance>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || !trimmed.contains("Row:", ignoreCase = true)) return null

        val items = ArrayList<LockscreenAffordance>()
        items += LockscreenAffordance(id = SystemShortcut.NONE.id)
        val seenIds = HashSet<String>().apply { add(SystemShortcut.NONE.id.lowercase()) }

        trimmed.lineSequence().forEach { line ->
            val cols = parseRowColumns(line)
            val id = cols["id"]?.takeUnless { it.isEmpty() || it.equals("NULL", ignoreCase = true) }
                ?: return@forEach
            if (!seenIds.add(id.lowercase())) return@forEach

            val name = cols["name"]?.takeUnless { it.isEmpty() || it.equals("NULL", ignoreCase = true) }
            val enabledRaw = cols["is_enabled"].orEmpty()
            val isEnabled = enabledRaw != "0" && !enabledRaw.equals("false", ignoreCase = true)
            val explanation = cols["enablement_explanation"]?.takeUnless {
                it.isEmpty() || it.equals("NULL", ignoreCase = true)
            }
            items += LockscreenAffordance(
                id = id,
                name = name,
                isEnabled = isEnabled,
                explanation = explanation
            )
        }
        return if (items.size > 1) items else null
    }

    private fun parseRowColumns(line: String): Map<String, String> {
        val body = ROW_PREFIX_REGEX.replaceFirst(line, "").trim()
        if (body.isEmpty()) return emptyMap()
        val matches = KEY_VALUE_BOUNDARY_REGEX.findAll(body).toList()
        if (matches.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>(matches.size)
        for (i in matches.indices) {
            val key = matches[i].groupValues[1].lowercase()
            val valueStart = matches[i].range.last + 1
            val valueEnd = if (i + 1 < matches.size) matches[i + 1].range.first else body.length
            if (valueStart <= valueEnd) {
                result[key] = body.substring(valueStart, valueEnd).trim()
            }
        }
        return result
    }
}
