package com.drdisagree.xmorphclock.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.ui.components.AppIcon
import com.drdisagree.xmorphclock.ui.components.ClockFacePreview
import com.drdisagree.xmorphclock.ui.components.ExpressiveTonalButton
import com.drdisagree.xmorphclock.ui.components.LocalWallpaper
import com.drdisagree.xmorphclock.ui.components.LockNotification
import com.drdisagree.xmorphclock.ui.components.LockscreenBackground
import com.drdisagree.xmorphclock.ui.components.restartApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val WAKE_HOLD_MS = 1_500L
private const val AUTO_WAKE_MS = 1_200L
private const val SLEEP_DIM = 0.72f
private const val UNLOCK_FRACTION = 0.18f

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onFinished: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var stage by rememberSaveable { mutableStateOf(OnboardingStage.ASLEEP) }
    val swipe = remember { Animatable(0f) }

    LaunchedEffect(stage) {
        when (stage) {
            OnboardingStage.AWAKE -> {
                delay(WAKE_HOLD_MS)
                stage = OnboardingStage.SETUP
            }

            OnboardingStage.SETUP -> Unit
            OnboardingStage.ASLEEP -> {
                delay(AUTO_WAKE_MS)
                stage = OnboardingStage.AWAKE
            }
        }
    }

    LifecycleResumeEffect(stage) {
        viewModel.onRefreshFramework()
        if (stage == OnboardingStage.SETUP) {
            viewModel.onCheckRoot()
            viewModel.onCheckSystemUi()
        }
        onPauseOrDispose { }
    }

    val sleep = animateFloatAsState(
        if (stage == OnboardingStage.ASLEEP) 1f else 0f,
        tween(WAKE_MS, easing = LinearEasing),
        label = "sleep"
    )
    val large = animateFloatAsState(
        if (stage == OnboardingStage.SETUP) 0f else 1f,
        tween(800, easing = Emphasized),
        label = "large"
    )

    Box(Modifier.fillMaxSize()) {
        LockscreenBackground(LocalWallpaper.current, Modifier.fillMaxSize())
        OnboardingOverlay(state, stage, { stage = it }, { sleep.value }, { large.value }, swipe, viewModel, onFinished)
    }
}

@Composable
private fun OnboardingOverlay(
    state: OnboardingUiState,
    stage: OnboardingStage,
    onStage: (OnboardingStage) -> Unit,
    sleep: () -> Float,
    large: () -> Float,
    swipe: Animatable<Float, AnimationVector1D>,
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black, alpha = SLEEP_DIM * sleep()) }
            .pointerInput(stage) {
                if (stage == OnboardingStage.ASLEEP) {
                    detectTapGestures { onStage(OnboardingStage.AWAKE) }
                }
            }
    ) {
        val maxClockHeight = maxHeight * 0.5f
        val height = with(LocalDensity.current) { maxHeight.toPx() }

        fun unlock(preview: Boolean = false) {
            scope.launch {
                swipe.animateTo(-height, tween(420, easing = Emphasized))
                if (preview) viewModel.onFinishPreview(onFinished) else viewModel.onFinish(onFinished)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = swipe.value
                    alpha = 1f - (-swipe.value / height).coerceIn(0f, 1f)
                }
                .pointerInput(stage, state.isReady) {
                    if (stage == OnboardingStage.ASLEEP || !state.isReady) return@pointerInput
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (-swipe.value > height * UNLOCK_FRACTION) unlock()
                            else scope.launch { swipe.animateTo(0f, spring(0.7f, 400f)) }
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            scope.launch { swipe.snapTo((swipe.value + amount).coerceAtMost(0f)) }
                        }
                    )
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            state.face?.let { preset ->
                ClockFacePreview(
                    face = preset.face,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxClockHeight)
                        .padding(horizontal = 8.dp, vertical = 24.dp),
                    largeProgress = large,
                    sleepProgress = sleep,
                    aodProgress = sleep
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val visible = stage == OnboardingStage.SETUP
                Staggered(visible, 0) {
                    LockNotification(
                        title = stringResource(R.string.requirement_root),
                        body = when {
                            state.rootGranted == true -> stringResource(R.string.notification_root_ok)
                            state.rootChecking || state.rootGranted == null -> stringResource(R.string.notification_checking)
                            else -> stringResource(R.string.notification_root_missing)
                        },
                        ok = if (state.rootChecking) null else state.rootGranted,
                        actionLabel = if (state.rootGranted == false && !state.rootChecking) {
                            stringResource(R.string.action_restart_app)
                        } else null,
                        onAction = { restartApp(context) }
                    )
                }
                Staggered(visible, 1) {
                    val framework = state.framework
                    LockNotification(
                        title = stringResource(R.string.requirement_framework),
                        body = framework?.let {
                            stringResource(R.string.notification_framework_ok, it.name, it.version.substringBefore(' '), it.apiVersion)
                        } ?: stringResource(R.string.notification_framework_missing),
                        ok = framework != null
                    )
                }
                Staggered(visible, 2) {
                    LockNotification(
                        title = stringResource(R.string.requirement_scope),
                        body = stringResource(
                            if (state.isScoped) R.string.notification_scope_ok else R.string.notification_scope_missing
                        ),
                        ok = state.framework?.let { state.isScoped }
                    )
                }
                Staggered(visible, 3) {
                    val checkingSystemUi = state.isScoped && state.systemUiChecking
                    LockNotification(
                        title = stringResource(
                            if (state.isSystemUiLoaded) R.string.status_step_systemui else R.string.action_restart_systemui
                        ),
                        body = when {
                            state.isSystemUiLoaded -> {
                                val version = state.report?.systemUiVersion?.takeIf { it.isNotBlank() }
                                if (version != null) {
                                    stringResource(R.string.notification_systemui_version_ok, version)
                                } else {
                                    stringResource(R.string.notification_systemui_ok)
                                }
                            }
                            state.restarting -> stringResource(R.string.restarting)
                            checkingSystemUi -> stringResource(R.string.notification_checking)
                            else -> stringResource(R.string.notification_restart)
                        },
                        ok = when {
                            state.isSystemUiLoaded -> true
                            state.restarting || checkingSystemUi || !state.isScoped -> null
                            else -> false
                        },
                        actionLabel = if (state.isScoped && !state.isSystemUiLoaded && !state.restarting && !checkingSystemUi) {
                            stringResource(R.string.action_restart)
                        } else null,
                        onAction = viewModel::onRestartSystemUi
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Hint(
                stage = stage,
                ready = state.isReady,
                onUnlock = { unlock(preview = false) },
                onUnlockPreview = { unlock(preview = true) }
            )
        }
    }
}

@Composable
private fun Staggered(visible: Boolean, index: Int, content: @Composable () -> Unit) {
    val state = animateFloatAsState(
        if (visible) 1f else 0f,
        tween(520, delayMillis = if (visible) STAGGER_START_MS + index * STAGGER_STEP_MS else 0, easing = Emphasized),
        label = "stagger"
    )
    val shown by remember { derivedStateOf { state.value > 0f } }
    if (!shown) return
    val offset = with(LocalDensity.current) { 28.dp.toPx() }
    Box(
        Modifier.graphicsLayer {
            val progress = state.value
            alpha = progress
            translationY = (1f - progress) * offset
            scaleX = 0.96f + 0.04f * progress
            scaleY = 0.96f + 0.04f * progress
        }
    ) { content() }
}

@Composable
private fun Hint(
    stage: OnboardingStage,
    ready: Boolean,
    onUnlock: () -> Unit,
    onUnlockPreview: () -> Unit
) {
    var bypassTaps by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(ready) {
        if (ready) bypassTaps = 0
    }
    val pulse by rememberInfiniteTransition(label = "hint").animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400), RepeatMode.Reverse),
        label = "pulse"
    )
    AnimatedVisibility(
        visible = stage != OnboardingStage.ASLEEP,
        enter = fadeIn(tween(600, delayMillis = 400)) + slideInVertically(tween(700, easing = Emphasized)) { it / 2 },
        exit = fadeOut(tween(240))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            val buttonColors = if (ready) {
                ButtonDefaults.filledTonalButtonColors()
            } else {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ExpressiveTonalButton(
                onClick = {
                    if (ready) {
                        onUnlock()
                    } else {
                        val next = bypassTaps + 1
                        if (next >= PREVIEW_BYPASS_TAPS) {
                            bypassTaps = 0
                            onUnlockPreview()
                        } else {
                            bypassTaps = next
                        }
                    }
                },
                colors = buttonColors,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                modifier = Modifier.testTag("onboarding_continue_button")
            ) {
                AnimatedVisibility(
                    visible = ready,
                    enter = fadeIn(tween(320)) + scaleIn(tween(420, easing = Emphasized), initialScale = 0.6f) + expandVertically(tween(420, easing = Emphasized)),
                    exit = fadeOut(tween(180)) + scaleOut(tween(240), targetScale = 0.6f) + shrinkVertically(tween(320, easing = Emphasized))
                ) {
                    AppIcon(
                        R.drawable.ic_lock_open,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        size = 20.dp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                val label = when {
                    ready -> stringResource(R.string.hint_swipe_to_open)
                    bypassTaps > 0 -> {
                        val remaining = PREVIEW_BYPASS_TAPS - bypassTaps
                        pluralStringResource(R.plurals.hint_preview_taps_remaining, remaining, remaining)
                    }
                    else -> stringResource(R.string.hint_finish_setup)
                }
                AnimatedContent(
                    targetState = label,
                    transitionSpec = { fadeIn(tween(240, delayMillis = 40)) togetherWith fadeOut(tween(140)) },
                    label = "hintText"
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.graphicsLayer { alpha = if (ready) pulse else 1f }
                    )
                }
            }
        }
    }
}

private const val PREVIEW_BYPASS_TAPS = 5
private const val STAGGER_START_MS = 820
private const val STAGGER_STEP_MS = 110
private const val WAKE_MS = 1_400
