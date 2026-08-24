package io.github.fisram.dhomate.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import io.github.fisram.dhomate.R
import io.github.fisram.dhomate.domain.TimerMode
import io.github.fisram.dhomate.domain.TimerPhase
import io.github.fisram.dhomate.domain.TimerState
import io.github.fisram.dhomate.domain.formatRemaining
import io.github.fisram.dhomate.timerEngine
import io.github.fisram.dhomate.ui.MainActivity
import java.time.Instant

/**
 * Puts the remaining time into a watch face's own data slots.
 *
 * While the timer runs the text is a [TimeDifferenceComplicationText] anchored to
 * the deadline, which the watch face renders and updates itself. We are not asked
 * again every second and nothing of ours needs to be alive for the number to stay
 * right - the same reason the ongoing chip cannot drift.
 */
class RemainingComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val sample = PlainComplicationText.Builder("12:30").build()
        val label = PlainComplicationText.Builder(getString(R.string.phase_focus)).build()
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(sample, label).setTitle(label).build()

            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(0.4f, 0f, 1f, label)
                    .setText(sample)
                    .build()

            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val engine = timerEngine
        engine.ensureLoaded()

        val state = engine.state.value
        val now = System.currentTimeMillis()
        val remaining = state.remainingMillis(now)
        val phaseName = getString(phaseLabelRes(state))
        val description = PlainComplicationText.Builder(phaseName).build()

        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(countdownText(state, remaining), description)
                    .setTitle(PlainComplicationText.Builder(phaseName).build())
                    .setTapAction(openApp())
                    .build()

            ComplicationType.RANGED_VALUE ->
                rangedValue(state, now, description)
                    .setText(countdownText(state, remaining))
                    .setTapAction(openApp())
                    .build()

            else -> null
        }
    }

    /**
     * Give the watch face a platform-time expression for the ring as well as
     * for the text. The face can then animate the range without polling this
     * service or keeping Dhomate's process alive.
     */
    private fun rangedValue(
        state: TimerState,
        now: Long,
        description: ComplicationText,
    ): RangedValueComplicationData.Builder {
        val fallback = state.progress(now)
        if (!state.running || state.phaseTotalMillis <= 0L) {
            return RangedValueComplicationData.Builder(fallback, 0f, 1f, description)
        }

        val startedAt = state.deadlineEpochMillis - state.phaseTotalMillis
        val elapsed = DynamicInstant.withSecondsPrecision(Instant.ofEpochMilli(startedAt))
            .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
        val dynamicProgress = elapsed.toIntSeconds()
            .asFloat()
            .div((state.phaseTotalMillis / 1_000f).coerceAtLeast(1f))

        return RangedValueComplicationData.Builder(
            dynamicProgress,
            fallback,
            0f,
            1f,
            description,
        )
    }

    /**
     * A running phase gets a self-updating countdown; a paused one gets a static
     * string, because there is nothing for the face to count towards.
     */
    private fun countdownText(state: TimerState, remaining: Long): ComplicationText =
        if (state.running) {
            TimeDifferenceComplicationText.Builder(
                TimeDifferenceStyle.STOPWATCH,
                CountDownTimeReference(Instant.ofEpochMilli(state.deadlineEpochMillis)),
            ).build()
        } else {
            PlainComplicationText.Builder(formatRemaining(remaining)).build()
        }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun phaseLabelRes(state: TimerState): Int = when {
        state.mode == TimerMode.DEEP_WORK && state.phase == TimerPhase.FOCUS ->
            R.string.phase_deep_work
        state.phase == TimerPhase.FOCUS -> R.string.phase_focus
        state.phase == TimerPhase.SHORT_BREAK -> R.string.phase_short_break
        else -> R.string.phase_long_break
    }
}
