package com.drdisagree.xmorphclock.domain.usecase

import com.drdisagree.xmorphclock.domain.model.LockscreenAffordance
import com.drdisagree.xmorphclock.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class ObserveAvailableAffordancesUseCase(private val repository: SettingsRepository) {
    operator fun invoke(): Flow<List<LockscreenAffordance>> = repository.availableAffordances
}
