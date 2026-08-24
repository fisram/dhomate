package io.github.fisram.dhomate.engine

import android.content.Context
import io.github.fisram.dhomate.R
import io.github.fisram.dhomate.data.DhomateStore
import io.github.fisram.dhomate.domain.SessionSettings
import io.github.fisram.dhomate.domain.TimerMode
import io.github.fisram.dhomate.domain.TimerPhase
import io.github.fisram.dhomate.domain.TimerState
import io.github.fisram.dhomate.domain.advance
import io.github.fisram.dhomate.notification.OngoingTimerNotification
import io.github.fisram.dhomate.notification.SessionAlerts
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The single source of truth for the timer, shared by the app UI, the tile, the
 * complication and the alarm receiver - all of which live in this one process.
 *
 * Every mutation is serialised behind [mutex] and persisted immediately, so
 * whichever surface wakes the process next reads back the same state.
 */
class TimerEngine(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val appContext = context.applicationContext
    private val store = DhomateStore(appContext)
    private val alarms = AlarmScheduler(appContext)
    private val alerts = SessionAlerts(appContext)
    private val ongoing = OngoingTimerNotification(appContext)

    private val mutex = Mutex()
    private var loaded = false

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(SessionSettings())
    val settings: StateFlow<SessionSettings> = _settings.asStateFlow()

    suspend fun ensureLoaded() = serialized { loadLocked() }

    suspend fun toggle() = serialized {
        loadLocked()
        if (_state.value.running) pauseLocked() else startLocked()
    }

    suspend fun start() = serialized {
        loadLocked()
        startLocked()
    }

    suspend fun pause() = serialized {
        loadLocked()
        pauseLocked()
    }

    /** Restart the phase that is currently showing. */
    suspend fun reset() = serialized {
        loadLocked()
        alarms.cancel()
        val current = _state.value
        val total = _settings.value.durationMillis(current.mode, current.phase)
        applyLocked(
            current.copy(
                running = false,
                deadlineEpochMillis = 0L,
                pausedRemainingMillis = total,
                phaseTotalMillis = total,
            )
        )
    }

    /** Move to the next phase without firing the end-of-phase alert. */
    suspend fun skip() = serialized {
        loadLocked()
        alarms.cancel()
        applyLocked(_state.value.advance(_settings.value))
    }

    suspend fun switchMode(mode: TimerMode) = serialized {
        loadLocked()
        alarms.cancel()
        val total = _settings.value.durationMillis(mode, TimerPhase.FOCUS)
        applyLocked(
            TimerState(
                mode = mode,
                phase = TimerPhase.FOCUS,
                running = false,
                deadlineEpochMillis = 0L,
                pausedRemainingMillis = total,
                phaseTotalMillis = total,
                completedFocusSessions = 0,
            )
        )
    }

    suspend fun updateSettings(transform: (SessionSettings) -> SessionSettings) = serialized {
        loadLocked()
        val currentSettings = _settings.value
        val updated = transform(currentSettings)
        if (updated == currentSettings) return@serialized

        _settings.value = updated

        // A length change lands on the phase already showing only when that phase
        // has not started yet; rescaling a session under way would be surprising.
        val current = _state.value
        val next = if (!current.running && current.pausedRemainingMillis >= current.phaseTotalMillis) {
            val total = updated.durationMillis(current.mode, current.phase)
            current.copy(pausedRemainingMillis = total, phaseTotalMillis = total)
        } else {
            current
        }

        if (next == current) {
            store.saveSettings(updated)
        } else {
            _state.value = next
            store.saveSnapshot(updated, next)
            syncExternalState(next)
        }
    }

    /** Called when the scheduled alarm fires. */
    suspend fun onPhaseEnd(expectedDeadlineEpochMillis: Long) = serialized {
        val alreadyLoaded = loaded
        loadLocked()
        // On a cold start loadLocked() has already settled the expired phase.
        // Completing again here would skip a whole phase and double-alert.
        if (!alreadyLoaded) return@serialized

        val current = _state.value
        val deadlineMatches = expectedDeadlineEpochMillis == 0L ||
            current.deadlineEpochMillis == expectedDeadlineEpochMillis
        if (!current.running || !deadlineMatches) return@serialized

        if (current.hasExpired(System.currentTimeMillis())) {
            completePhaseLocked()
        } else {
            // A wall-clock correction can move the deadline after an RTC alarm
            // was queued. Re-arm rather than completing the phase early.
            alarms.schedule(current.deadlineEpochMillis)
        }
    }

    /** Re-arm the alarm after a reboot cleared it. */
    suspend fun restoreAfterBoot() = serialized {
        loadLocked()
    }

    private suspend fun <T> serialized(block: suspend () -> T): T =
        withContext(dispatcher) { mutex.withLock { block() } }

    // --- internals; every one of these runs with the mutex already held -----

    private suspend fun loadLocked() {
        if (loaded) return

        val snapshot = store.currentSnapshot()
        val restoredSettings = snapshot.settings
        var restored = snapshot.timerState

        if (restored.phaseTotalMillis <= 0L) {
            val total = restoredSettings.durationMillis(restored.mode, restored.phase)
            restored = restored.copy(phaseTotalMillis = total, pausedRemainingMillis = total)
        }

        _settings.value = restoredSettings
        _state.value = restored
        loaded = true

        // The deadline may have passed while the process was dead. The phase is
        // genuinely over, so settle it instead of showing a stale countdown.
        if (restored.hasExpired(System.currentTimeMillis())) {
            completePhaseLocked()
        } else {
            if (restored.running) {
                // AlarmManager normally outlives our process, but alarms are
                // cleared by reboot and can be disrupted by package replacement.
                // Reusing the same PendingIntent makes this idempotent.
                alarms.schedule(restored.deadlineEpochMillis)
            }

            // Notifications normally outlive our process too, but an app update
            // clears them. Restore only a real in-progress session on cold load;
            // the system-owned countdown then continues without our process.
            if (restored.isMidSession()) syncOngoing(restored)
        }
        _ready.value = true
    }

    private suspend fun startLocked() {
        val current = _state.value
        if (current.running) return

        val remaining = current.pausedRemainingMillis.takeIf { it > 0L }
            ?: current.phaseTotalMillis
        val deadline = System.currentTimeMillis() + remaining

        applyLocked(
            current.copy(
                running = true,
                deadlineEpochMillis = deadline,
                pausedRemainingMillis = 0L,
            )
        )
        alarms.schedule(deadline)
    }

    private suspend fun pauseLocked() {
        val current = _state.value
        if (!current.running) return

        alarms.cancel()
        applyLocked(
            current.copy(
                running = false,
                deadlineEpochMillis = 0L,
                pausedRemainingMillis = current.remainingMillis(System.currentTimeMillis()),
            )
        )
    }

    private suspend fun completePhaseLocked() {
        val finished = _state.value
        val next = finished.advance(_settings.value)
        alarms.cancel()
        applyLocked(next)
        alerts.notifyPhaseComplete(finished.phase, next.phase)
    }

    private suspend fun applyLocked(next: TimerState) {
        if (next == _state.value) return

        _state.value = next
        store.saveTimerState(next)
        syncExternalState(next)
    }

    private fun syncExternalState(next: TimerState) {
        syncOngoing(next)
        Surfaces.requestUpdate(appContext)
    }

    private fun syncOngoing(state: TimerState) {
        if (state.isMidSession()) ongoing.show(state, phaseLabel(state)) else ongoing.clear()
    }

    private fun TimerState.isMidSession(): Boolean = running ||
        (pausedRemainingMillis > 0L && pausedRemainingMillis < phaseTotalMillis)

    private fun phaseLabel(state: TimerState): String = appContext.getString(
        when {
            state.mode == TimerMode.DEEP_WORK && state.phase == TimerPhase.FOCUS ->
                R.string.phase_deep_work
            state.phase == TimerPhase.FOCUS -> R.string.phase_focus
            state.phase == TimerPhase.SHORT_BREAK -> R.string.phase_short_break
            else -> R.string.phase_long_break
        }
    )
}
