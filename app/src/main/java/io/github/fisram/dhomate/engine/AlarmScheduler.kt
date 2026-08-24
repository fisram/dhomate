package io.github.fisram.dhomate.engine

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.fisram.dhomate.alarm.PhaseEndReceiver
import io.github.fisram.dhomate.ui.MainActivity

/**
 * Schedules the moment a phase ends.
 *
 * The countdown on screen is only a rendering of the deadline - this is what
 * actually fires. [AlarmManager.setAlarmClock] is used because it is the one
 * scheduling call the platform will not defer for doze or battery saver, which
 * is what makes the alert behave like the system alarm rather than like a
 * best-effort background job.
 */
class AlarmScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(AlarmManager::class.java)

    @SuppressLint("MissingPermission") // USE_EXACT_ALARM is declared; fallback handles policy changes.
    fun schedule(deadlineEpochMillis: Long) {
        val manager = manager ?: return
        val operation = fireIntent(deadlineEpochMillis)

        if (manager.canScheduleExactAlarms()) {
            manager.setAlarmClock(
                AlarmManager.AlarmClockInfo(deadlineEpochMillis, showIntent()),
                operation,
            )
        } else {
            // Should not happen with USE_EXACT_ALARM declared, but never silently
            // downgrade to an inexact alarm without still waking the device.
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                deadlineEpochMillis,
                operation,
            )
        }
    }

    fun cancel() {
        manager?.cancel(fireIntent(0L))
    }

    private fun fireIntent(deadlineEpochMillis: Long): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        REQUEST_FIRE,
        Intent(appContext, PhaseEndReceiver::class.java)
            .setAction(PhaseEndReceiver.ACTION_PHASE_END)
            .putExtra(EXTRA_DEADLINE, deadlineEpochMillis),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        REQUEST_SHOW,
        Intent(appContext, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val EXTRA_DEADLINE = "io.github.fisram.dhomate.extra.DEADLINE"

        private const val REQUEST_FIRE = 100
        private const val REQUEST_SHOW = 101
    }
}
