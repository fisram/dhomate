package io.github.fisram.dhomate.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.fisram.dhomate.domain.SessionSettings
import io.github.fisram.dhomate.domain.TimerMode
import io.github.fisram.dhomate.domain.TimerPhase
import io.github.fisram.dhomate.domain.TimerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dhomate")

/**
 * Persists both the settings and the live timer state.
 *
 * The timer state is written on every transition, not on every tick, because a
 * deadline does not change while it runs. That keeps writes rare and means the
 * timer survives process death exactly as it was.
 */
class DhomateStore(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<SessionSettings> = store.data.map { it.toSettings() }

    val timerState: Flow<TimerState> = store.data.map { it.toTimerState() }

    suspend fun currentSnapshot(): Snapshot = store.data.first().let { preferences ->
        Snapshot(preferences.toSettings(), preferences.toTimerState())
    }

    suspend fun saveSettings(settings: SessionSettings) {
        store.edit { it.putSettings(settings) }
    }

    suspend fun saveTimerState(state: TimerState) {
        store.edit { it.putTimerState(state) }
    }

    suspend fun saveSnapshot(settings: SessionSettings, state: TimerState) {
        store.edit { prefs ->
            prefs.putSettings(settings)
            prefs.putTimerState(state)
        }
    }

    private fun MutablePreferences.putSettings(settings: SessionSettings) {
        this[KEY_FOCUS] = settings.focusMinutes
        this[KEY_SHORT_BREAK] = settings.shortBreakMinutes
        this[KEY_LONG_BREAK] = settings.longBreakMinutes
        this[KEY_DEEP_WORK] = settings.deepWorkMinutes
        this[KEY_SESSIONS] = settings.sessionsBeforeLongBreak
    }

    private fun MutablePreferences.putTimerState(state: TimerState) {
        this[KEY_MODE] = state.mode.ordinal
        this[KEY_PHASE] = state.phase.ordinal
        this[KEY_RUNNING] = state.running
        this[KEY_DEADLINE] = state.deadlineEpochMillis
        this[KEY_PAUSED_REMAINING] = state.pausedRemainingMillis
        this[KEY_PHASE_TOTAL] = state.phaseTotalMillis
        this[KEY_COMPLETED] = state.completedFocusSessions
    }

    private fun Preferences.toSettings() = SessionSettings(
        focusMinutes = this[KEY_FOCUS] ?: 25,
        shortBreakMinutes = this[KEY_SHORT_BREAK] ?: 5,
        longBreakMinutes = this[KEY_LONG_BREAK] ?: 15,
        deepWorkMinutes = this[KEY_DEEP_WORK] ?: 60,
        sessionsBeforeLongBreak = this[KEY_SESSIONS] ?: 4,
    )

    private fun Preferences.toTimerState() = TimerState(
        mode = TimerMode.entries.getOrElse(this[KEY_MODE] ?: 0) { TimerMode.POMODORO },
        phase = TimerPhase.entries.getOrElse(this[KEY_PHASE] ?: 0) { TimerPhase.FOCUS },
        running = this[KEY_RUNNING] ?: false,
        deadlineEpochMillis = this[KEY_DEADLINE] ?: 0L,
        pausedRemainingMillis = this[KEY_PAUSED_REMAINING] ?: 0L,
        phaseTotalMillis = this[KEY_PHASE_TOTAL] ?: 0L,
        completedFocusSessions = this[KEY_COMPLETED] ?: 0,
    )

    private companion object {
        val KEY_FOCUS = intPreferencesKey("focus_minutes")
        val KEY_SHORT_BREAK = intPreferencesKey("short_break_minutes")
        val KEY_LONG_BREAK = intPreferencesKey("long_break_minutes")
        val KEY_DEEP_WORK = intPreferencesKey("deep_work_minutes")
        val KEY_SESSIONS = intPreferencesKey("sessions_before_long_break")

        val KEY_MODE = intPreferencesKey("mode")
        val KEY_PHASE = intPreferencesKey("phase")
        val KEY_RUNNING = booleanPreferencesKey("running")
        val KEY_DEADLINE = longPreferencesKey("deadline_epoch_millis")
        val KEY_PAUSED_REMAINING = longPreferencesKey("paused_remaining_millis")
        val KEY_PHASE_TOTAL = longPreferencesKey("phase_total_millis")
        val KEY_COMPLETED = intPreferencesKey("completed_focus_sessions")
    }

    data class Snapshot(
        val settings: SessionSettings,
        val timerState: TimerState,
    )
}
