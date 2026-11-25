package com.drdisagree.xmorphclock.ui.home

import com.drdisagree.xmorphclock.domain.editor.LockTarget
import com.drdisagree.xmorphclock.domain.model.ShortcutSlot

internal data class HomeLocalState(
    val mode: PreviewMode,
    val editing: Int?,
    val tab: FaceTab,
    val target: LockTarget?,
    val item: Int?,
    val focus: String?,
    val dragging: Boolean,
    val shortcutSlot: ShortcutSlot = ShortcutSlot.LEFT
)
