package com.drdisagree.xmorphclock.domain.usecase

import com.drdisagree.xmorphclock.domain.repository.SettingsRepository

class SyncLockscreenShortcutsUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke() = repository.syncSystemShortcuts()
}
