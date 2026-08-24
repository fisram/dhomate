package io.github.fisram.dhomate.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Text
import io.github.fisram.dhomate.R
import io.github.fisram.dhomate.domain.SessionSettings

/**
 * A rotating-crown scrollable list, dismissed by swiping right rather than by a
 * button at the bottom. Both are what a Wear user expects, and neither was true
 * of the first version.
 */
@Composable
fun SettingsScreen(
    settings: SessionSettings,
    onUpdate: ((SessionSettings) -> SessionSettings) -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }

    // The crown only drives a component that holds focus.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .rotaryScrollable(
                RotaryScrollableDefaults.behavior(listState),
                focusRequester = focusRequester,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 14.dp,
            vertical = 26.dp,
        ),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        item {
            Stepper(
                label = stringResource(R.string.setting_focus),
                value = settings.focusMinutes,
                range = SessionSettings.FOCUS_RANGE,
                accent = Tomato,
                onChange = { v -> onUpdate { it.copy(focusMinutes = v) } },
            )
        }
        item {
            Stepper(
                label = stringResource(R.string.setting_short_break),
                value = settings.shortBreakMinutes,
                range = SessionSettings.SHORT_BREAK_RANGE,
                accent = Leaf,
                onChange = { v -> onUpdate { it.copy(shortBreakMinutes = v) } },
            )
        }
        item {
            Stepper(
                label = stringResource(R.string.setting_long_break),
                value = settings.longBreakMinutes,
                range = SessionSettings.LONG_BREAK_RANGE,
                accent = Leaf,
                onChange = { v -> onUpdate { it.copy(longBreakMinutes = v) } },
            )
        }
        item {
            Stepper(
                label = stringResource(R.string.setting_deep_work),
                value = settings.deepWorkMinutes,
                range = SessionSettings.DEEP_WORK_RANGE,
                accent = Amber,
                onChange = { v -> onUpdate { it.copy(deepWorkMinutes = v) } },
            )
        }
        item {
            Stepper(
                label = stringResource(R.string.setting_sessions),
                value = settings.sessionsBeforeLongBreak,
                range = SessionSettings.SESSIONS_RANGE,
                accent = Tomato,
                suffix = "",
                onChange = { v -> onUpdate { it.copy(sessionsBeforeLongBreak = v) } },
            )
        }
        item {
            Text(
                text = stringResource(R.string.settings_hint_swipe),
                fontSize = 10.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    accent: Color,
    onChange: (Int) -> Unit,
    suffix: String = "m",
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(R.drawable.ic_minus, R.string.action_decrease) {
                if (value > range.first) onChange(value - 1)
            }
            Text(
                text = "$value$suffix",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = accent,
            )
            StepButton(R.drawable.ic_plus, R.string.action_increase) {
                if (value < range.last) onChange(value + 1)
            }
        }
    }
}

@Composable
private fun StepButton(icon: Int, description: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = stringResource(description),
            colorFilter = ColorFilter.tint(TextPrimary),
            modifier = Modifier.size(14.dp),
        )
    }
}
