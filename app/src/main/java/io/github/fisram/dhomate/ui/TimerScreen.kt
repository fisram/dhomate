package io.github.fisram.dhomate.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.AmbientTickEffect
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.material3.Text
import io.github.fisram.dhomate.R
import io.github.fisram.dhomate.domain.SessionSettings
import io.github.fisram.dhomate.domain.TimerMode
import io.github.fisram.dhomate.domain.TimerPhase
import io.github.fisram.dhomate.domain.TimerState
import io.github.fisram.dhomate.domain.formatRemaining
import kotlinx.coroutines.delay

/**
 * Re-reads the wall clock on the second boundary while this destination is
 * interactive, and on the platform's low-frequency ticks in ambient mode.
 *
 * This decides only *when to repaint*; it never advances the timer. If the
 * system defers it, the next read is still correct, because what is shown is
 * always `deadline - now`.
 */
@Composable
private fun rememberNow(running: Boolean): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val ambientModeManager = LocalAmbientModeManager.current
    val ambientMode = ambientModeManager?.currentAmbientMode

    if (running) {
        ambientModeManager?.AmbientTickEffect {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                now.longValue = System.currentTimeMillis()
            }
        }
    }

    LaunchedEffect(running, ambientMode, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            now.longValue = System.currentTimeMillis()
            if (!running || ambientMode is AmbientMode.Ambient) return@repeatOnLifecycle

            while (true) {
                delay(1_000L - (now.longValue % 1_000L))
                now.longValue = System.currentTimeMillis()
            }
        }
    }
    return now
}

@Composable
fun TimerScreen(
    state: TimerState,
    settings: SessionSettings,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit,
    onSwitchMode: (TimerMode) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val now = rememberNow(state.running)
    val accent = accentFor(state)
    val accentSoft = accentSoftFor(state)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        // Full-bleed ring at the very edge, the way the system timer draws it.
        EdgeRing(state = state, now = now, accent = accent)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 22.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(R.drawable.ic_refresh, R.string.action_reset, onReset)
                Spacer(Modifier.width(6.dp))
                Pill(R.drawable.ic_skip, R.string.action_skip, onSkip, tint = accent)
                Spacer(Modifier.width(6.dp))
                Pill(R.drawable.ic_more, R.string.settings, onOpenSettings)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PhaseLabel(state, settings, accent, onSwitchMode)
                CountdownText(state = state, now = now)
            }

            PlayButton(running = state.running, fill = accentSoft, onClick = onToggle)
        }
    }
}

@Composable
private fun CountdownText(state: TimerState, now: State<Long>) {
    Text(
        text = formatRemaining(state.remainingMillis(now.value)),
        fontSize = 46.sp,
        fontWeight = FontWeight.Light,
        color = TextPrimary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PhaseLabel(
    state: TimerState,
    settings: SessionSettings,
    accent: Color,
    onSwitchMode: (TimerMode) -> Unit,
) {
    // Mode can only change on a focus phase that has not started yet.
    val canSwitch = !state.running && state.phase == TimerPhase.FOCUS
    val name = stringResource(phaseLabelRes(state))

    // The session count rides on this line rather than taking a row of its own;
    // there is not enough height on a 192dp screen for both.
    val label = if (state.mode == TimerMode.POMODORO && state.phase == TimerPhase.FOCUS) {
        "$name · ${state.completedFocusSessions + 1}/${settings.sessionsBeforeLongBreak}"
    } else {
        name
    }

    val base = Modifier.clip(RoundedCornerShape(10.dp))
    Text(
        text = if (canSwitch) "$label  ⌄" else label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = accent,
        textAlign = TextAlign.Center,
        modifier = (
            if (canSwitch) {
                base.clickable {
                    onSwitchMode(
                        if (state.mode == TimerMode.POMODORO) TimerMode.DEEP_WORK
                        else TimerMode.POMODORO,
                    )
                }
            } else {
                base
            }
            ).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun Pill(
    icon: Int,
    description: Int,
    onClick: () -> Unit,
    tint: Color = TextPrimary,
) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = stringResource(description),
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** The big squircle at the bottom, mirroring the system timer's primary control. */
@Composable
private fun PlayButton(running: Boolean, fill: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 78.dp, height = 52.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(if (running) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = stringResource(
                if (running) R.string.action_pause else R.string.action_start,
            ),
            colorFilter = ColorFilter.tint(Background),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun EdgeRing(state: TimerState, now: State<Long>, accent: Color) {
    Spacer(
        Modifier
            .fillMaxSize()
            .padding(2.dp)
            .drawWithCache {
                val stroke = 7.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                val strokeStyle = Stroke(width = stroke, cap = StrokeCap.Round)

                onDrawBehind {
                    drawArc(
                        color = Track,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = strokeStyle,
                    )

                    val progress = state.progress(now.value)
                    if (progress > 0f) {
                        drawArc(
                            color = accent,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = strokeStyle,
                        )
                    }
                }
            },
    )
}

private fun accentFor(state: TimerState): Color = when {
    state.phase != TimerPhase.FOCUS -> Leaf
    state.mode == TimerMode.DEEP_WORK -> Amber
    else -> Tomato
}

private fun accentSoftFor(state: TimerState): Color = when {
    state.phase != TimerPhase.FOCUS -> LeafSoft
    state.mode == TimerMode.DEEP_WORK -> AmberSoft
    else -> TomatoSoft
}

internal fun phaseLabelRes(state: TimerState): Int = when {
    state.mode == TimerMode.DEEP_WORK && state.phase == TimerPhase.FOCUS ->
        R.string.phase_deep_work
    state.phase == TimerPhase.FOCUS -> R.string.phase_focus
    state.phase == TimerPhase.SHORT_BREAK -> R.string.phase_short_break
    else -> R.string.phase_long_break
}
