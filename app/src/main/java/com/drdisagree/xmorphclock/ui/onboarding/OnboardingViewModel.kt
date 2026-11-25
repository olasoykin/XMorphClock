package com.drdisagree.xmorphclock.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.xmorphclock.domain.usecase.CheckRootUseCase
import com.drdisagree.xmorphclock.domain.usecase.GetInstallTimeUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveFacesUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveFrameworkUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveHookReportUseCase
import com.drdisagree.xmorphclock.domain.usecase.RefreshFrameworkUseCase
import com.drdisagree.xmorphclock.domain.usecase.RequestHookReportUseCase
import com.drdisagree.xmorphclock.domain.usecase.RestartSystemUiUseCase
import com.drdisagree.xmorphclock.domain.usecase.SetOnboardingDoneUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class OnboardingViewModel(
    observeFaces: ObserveFacesUseCase,
    observeFramework: ObserveFrameworkUseCase,
    private val observeHookReport: ObserveHookReportUseCase,
    private val getInstallTime: GetInstallTimeUseCase,
    private val refreshFramework: RefreshFrameworkUseCase,
    private val requestHookReport: RequestHookReportUseCase,
    private val checkRoot: CheckRootUseCase,
    private val restartSystemUi: RestartSystemUiUseCase,
    private val setOnboardingDone: SetOnboardingDoneUseCase,
    private val setPreviewOnly: (Boolean) -> Unit = {}
) : ViewModel() {

    private val root = MutableStateFlow<Boolean?>(null)
    private val restarting = MutableStateFlow(false)
    private val rootChecking = MutableStateFlow(false)
    private val systemUiChecking = MutableStateFlow(false)
    private var checkSystemUiJob: Job? = null

    val uiState: StateFlow<OnboardingUiState> = combine(
        combine(observeFaces(), observeFramework(), observeHookReport()) { faces, framework, report ->
            Triple(faces, framework, report)
        },
        combine(root, rootChecking, systemUiChecking, restarting) { rootGranted, checkingRoot, checkingSystemUi, isRestarting ->
            LocalChecks(rootGranted, checkingRoot, checkingSystemUi, isRestarting)
        }
    ) { (faces, framework, report), checks ->
        OnboardingUiState(
            face = faces.firstOrNull(),
            rootGranted = checks.rootGranted,
            rootChecking = checks.rootChecking,
            framework = framework,
            report = report,
            installedAt = getInstallTime(),
            systemUiChecking = checks.systemUiChecking,
            restarting = checks.restarting
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), OnboardingUiState())

    init {
        viewModelScope.launch {
            observeFramework()
                .map { it?.scope?.contains(SYSTEMUI) == true }
                .distinctUntilChanged()
                .filter { it }
                .collect { onCheckSystemUi() }
        }
    }

    fun onRefreshFramework() {
        refreshFramework()
    }

    fun onCheckRoot() {
        if (rootChecking.value || root.value == true) return
        rootChecking.value = true
        viewModelScope.launch {
            root.value = checkRoot()
            rootChecking.value = false
        }
    }

    fun onCheckSystemUi() {
        if (restarting.value || uiState.value.isSystemUiLoaded) return
        if (checkSystemUiJob?.isActive == true) return
        checkSystemUiJob = viewModelScope.launch {
            systemUiChecking.value = true
            withTimeoutOrNull(REPORT_CHECK_TIMEOUT) {
                observeHookReport()
                    .onStart { requestHookReport() }
                    .first { it != null && !it.isStale(getInstallTime()) }
            }
            systemUiChecking.value = false
        }
    }

    fun onRestartSystemUi() {
        if (restarting.value) return
        checkSystemUiJob?.cancel()
        systemUiChecking.value = false
        restarting.value = true
        viewModelScope.launch {
            val restartAt = System.currentTimeMillis()
            if (restartSystemUi()) {
                delay(RESTART_INITIAL_WAIT)
                withTimeoutOrNull(RESTART_TIMEOUT) {
                    coroutineScope {
                        val poller = launch {
                            while (isActive) {
                                requestHookReport()
                                delay(RESTART_POLL_INTERVAL)
                            }
                        }
                        try {
                            observeHookReport().first { report ->
                                report != null && report.isFreshSince(restartAt, getInstallTime(), CLOCK_SLACK)
                            }
                        } finally {
                            poller.cancel()
                        }
                    }
                }
            }
            restarting.value = false
        }
    }

    fun onFinish(then: () -> Unit) {
        viewModelScope.launch {
            setPreviewOnly(false)
            setOnboardingDone(true)
            then()
        }
    }

    fun onFinishPreview(then: () -> Unit) {
        setPreviewOnly(true)
        then()
    }

    private data class LocalChecks(
        val rootGranted: Boolean?,
        val rootChecking: Boolean,
        val systemUiChecking: Boolean,
        val restarting: Boolean
    )
}

private const val STOP_TIMEOUT = 5_000L
private const val REPORT_CHECK_TIMEOUT = 1_800L
private const val RESTART_INITIAL_WAIT = 2_500L
private const val RESTART_POLL_INTERVAL = 1_500L
private const val RESTART_TIMEOUT = 12_000L
private const val CLOCK_SLACK = 1_000L
private const val SYSTEMUI = "com.android.systemui"
