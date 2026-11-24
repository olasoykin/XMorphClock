package com.drdisagree.xmorphclock.domain.usecase

import com.drdisagree.xmorphclock.domain.repository.StatusRepository

class RefreshFrameworkUseCase(private val repository: StatusRepository) {
    operator fun invoke() = repository.refreshFramework()
}
