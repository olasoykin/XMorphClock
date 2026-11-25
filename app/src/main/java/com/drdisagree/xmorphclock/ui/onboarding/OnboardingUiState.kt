package com.drdisagree.xmorphclock.ui.onboarding

import com.drdisagree.xmorphclock.domain.model.FacePreset
import com.drdisagree.xmorphclock.domain.model.FrameworkInfo
import com.drdisagree.xmorphclock.domain.model.HookReport

data class OnboardingUiState(
    val face: FacePreset? = null,
    val rootGranted: Boolean? = null,
    val rootChecking: Boolean = false,
    val framework: FrameworkInfo? = null,
    val report: HookReport? = null,
    val installedAt: Long = 0L,
    val systemUiChecking: Boolean = false,
    val restarting: Boolean = false
) {
    val isScoped: Boolean
        get() = framework?.scope?.contains(SYSTEMUI) == true

    val isSystemUiLoaded: Boolean
        get() = isScoped && report != null && !report.isStale(installedAt)

    val isReady: Boolean
        get() = rootGranted == true && framework != null && isScoped
}

private const val SYSTEMUI = "com.android.systemui"
