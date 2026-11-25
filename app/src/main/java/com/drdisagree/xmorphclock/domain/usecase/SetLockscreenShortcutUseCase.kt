package com.drdisagree.xmorphclock.domain.usecase

import com.drdisagree.xmorphclock.domain.model.ShortcutSlot
import com.drdisagree.xmorphclock.domain.model.SystemShortcut
import com.drdisagree.xmorphclock.domain.repository.SettingsRepository

class SetLockscreenShortcutUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(slot: ShortcutSlot, shortcut: SystemShortcut) =
        repository.setShortcut(slot, shortcut)
}
