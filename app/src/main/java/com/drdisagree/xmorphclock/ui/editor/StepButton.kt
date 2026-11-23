package com.drdisagree.xmorphclock.ui.editor

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.ui.components.AppIcon
import com.drdisagree.xmorphclock.ui.components.expressivePillShape
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun StepButton(@DrawableRes icon: Int, description: String, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val shape = expressivePillShape(interaction, restCorner = STEP_SIZE / 2, pressedCorner = 10.dp)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, tween(PRESS_MS), label = "stepScale")
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(STEP_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .indication(interaction, ripple())
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    step()
                    true
                }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val press = PressInteraction.Press(down.position)
                        launch { interaction.emit(press) }
                        pressed = true
                        step()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val repeat = launch {
                            delay(REPEAT_DELAY)
                            while (isActive) {
                                step()
                                delay(REPEAT_INTERVAL)
                            }
                        }
                        val up = waitForUpOrCancellation()
                        repeat.cancel()
                        pressed = false
                        launch {
                            interaction.emit(
                                if (up != null) PressInteraction.Release(press) else PressInteraction.Cancel(press)
                            )
                        }
                    }
                }
            }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AppIcon(icon, tint = MaterialTheme.colorScheme.onSecondaryContainer, size = ICON_SIZE)
        }
    }
}

private val STEP_SIZE = 36.dp
private val ICON_SIZE = 16.dp
private const val PRESSED_SCALE = 0.92f
private const val PRESS_MS = 120
private const val REPEAT_DELAY = 400L
private const val REPEAT_INTERVAL = 50L
