package com.drdisagree.xmorphclock.domain.repository

import com.drdisagree.xmorphclock.domain.model.ClockFace
import com.drdisagree.xmorphclock.domain.model.ClockSettings
import com.drdisagree.xmorphclock.domain.model.Composition
import com.drdisagree.xmorphclock.domain.model.LockscreenAffordance
import com.drdisagree.xmorphclock.domain.model.ShortcutSlot
import com.drdisagree.xmorphclock.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<ClockSettings>
    val availableAffordances: Flow<List<LockscreenAffordance>>
    val isOnboardingDone: Boolean
    suspend fun setClockEnabled(enabled: Boolean)
    suspend fun applyFace(style: Int, face: ClockFace)
    suspend fun setComposition(composition: Composition)
    suspend fun setBurnInProtection(enabled: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setOnboardingDone(done: Boolean)
    suspend fun setShortcut(slot: ShortcutSlot, affordanceId: String)
    suspend fun syncSystemShortcuts()
}
