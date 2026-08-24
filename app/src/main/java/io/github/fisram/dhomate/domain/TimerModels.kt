package io.github.fisram.dhomate.domain

enum class TimerPhase { FOCUS, SHORT_BREAK, LONG_BREAK }

enum class TimerMode { POMODORO, DEEP_WORK }

/**
 * User-configurable session lengths. Every duration in the app comes from here;
 * nothing is hard-coded.
 */
data class SessionSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val deepWorkMinutes: Int = 60,
    val sessionsBeforeLongBreak: Int = 4,
) {
    fun durationMillis(mode: TimerMode, phase: TimerPhase): Long {
        val minutes = when (phase) {
            TimerPhase.FOCUS ->
                if (mode == TimerMode.DEEP_WORK) deepWorkMinutes else focusMinutes
            TimerPhase.SHORT_BREAK -> shortBreakMinutes
            TimerPhase.LONG_BREAK -> longBreakMinutes
        }
        return minutes * 60_000L
    }

    companion object {
        val FOCUS_RANGE = 5..120
        val SHORT_BREAK_RANGE = 1..30
        val LONG_BREAK_RANGE = 5..60
        val DEEP_WORK_RANGE = 15..240
        val SESSIONS_RANGE = 2..8
    }
}

/**
 * The timer as a **deadline**, never as a running total.
 *
 * The previous implementation subtracted 1000ms per coroutine tick, so any tick
 * the system deferred (ambient, doze, process death) was lost time that could
 * never be recovered - and a burst of catch-up ticks made the clock jump. Here
 * the remaining time is always derived from the wall clock, so a late or missed
 * update is cosmetic: the next read is correct again, and it can never drift.
 */
data class TimerState(
    val mode: TimerMode = TimerMode.POMODORO,
    val phase: TimerPhase = TimerPhase.FOCUS,
    val running: Boolean = false,
    /** Instant the current phase ends. Only meaningful while [running]. */
    val deadlineEpochMillis: Long = 0L,
    /** Time left when the user paused. Only meaningful while not [running]. */
    val pausedRemainingMillis: Long = 0L,
    val phaseTotalMillis: Long = 0L,
    val completedFocusSessions: Int = 0,
) {
    fun remainingMillis(nowEpochMillis: Long): Long =
        if (running) (deadlineEpochMillis - nowEpochMillis).coerceAtLeast(0L)
        else pausedRemainingMillis.coerceAtLeast(0L)

    fun progress(nowEpochMillis: Long): Float {
        if (phaseTotalMillis <= 0L) return 0f
        val remaining = remainingMillis(nowEpochMillis)
        return (1f - remaining.toFloat() / phaseTotalMillis).coerceIn(0f, 1f)
    }

    fun hasExpired(nowEpochMillis: Long): Boolean =
        running && nowEpochMillis >= deadlineEpochMillis
}

/**
 * mm:ss, rounded **up**, so a phase reads 00:01 until it genuinely hits zero
 * rather than sitting on 00:00 for a second before the alert.
 */
fun formatRemaining(millis: Long): String {
    val totalSeconds = (millis + 999L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return buildString(if (minutes >= 100L) 6 else 5) {
        if (minutes < 10L) append('0')
        append(minutes)
        append(':')
        if (seconds < 10L) append('0')
        append(seconds)
    }
}

/** The phase that follows [phase], and the focus-session count that goes with it. */
fun TimerState.advance(settings: SessionSettings): TimerState {
    val nextPhase: TimerPhase
    val nextCompleted: Int
    when (phase) {
        TimerPhase.FOCUS -> {
            val completed = completedFocusSessions + 1
            if (completed >= settings.sessionsBeforeLongBreak) {
                nextPhase = TimerPhase.LONG_BREAK
                nextCompleted = 0
            } else {
                nextPhase = TimerPhase.SHORT_BREAK
                nextCompleted = completed
            }
        }
        TimerPhase.SHORT_BREAK, TimerPhase.LONG_BREAK -> {
            nextPhase = TimerPhase.FOCUS
            nextCompleted = completedFocusSessions
        }
    }
    val total = settings.durationMillis(mode, nextPhase)
    return copy(
        phase = nextPhase,
        running = false,
        deadlineEpochMillis = 0L,
        pausedRemainingMillis = total,
        phaseTotalMillis = total,
        completedFocusSessions = nextCompleted,
    )
}
