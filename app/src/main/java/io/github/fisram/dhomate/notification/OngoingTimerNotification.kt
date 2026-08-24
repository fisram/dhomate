package io.github.fisram.dhomate.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import io.github.fisram.dhomate.R
import io.github.fisram.dhomate.alarm.TimerActionReceiver
import io.github.fisram.dhomate.domain.TimerState
import io.github.fisram.dhomate.ui.MainActivity

/**
 * The persistent countdown chip on the watch face.
 *
 * This is what makes the timer behave like the system's own: the notification
 * lives in the platform's NotificationManager rather than in our process, and
 * [Status.TimerPart] is rendered *by the system* from the deadline. Both keep
 * working if our process is killed - nothing here has to stay running to make
 * the numbers advance, which is precisely why it cannot lag or jump.
 */
class OngoingTimerNotification(context: Context) {

    private val appContext = context.applicationContext

    fun show(state: TimerState, phaseLabel: String) {
        if (!canPost()) return
        Channels.ensure(appContext)

        val remaining = state.remainingMillis(System.currentTimeMillis())

        val status = Status.Builder()
            .addTemplate(TEMPLATE)
            .addPart("phase", Status.TextPart(phaseLabel))
            .addPart("timer", timerPart(state, remaining))
            .build()

        val builder = NotificationCompat.Builder(appContext, Channels.ONGOING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(phaseLabel)
            .setContentText(io.github.fisram.dhomate.domain.formatRemaining(remaining))
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .addAction(toggleAction(state))

        OngoingActivity.Builder(appContext, ONGOING_ID, builder)
            .setStaticIcon(R.drawable.ic_timer)
            .setTouchIntent(openApp())
            .setStatus(status)
            .build()
            .apply(appContext)

        try {
            NotificationManagerCompat.from(appContext).notify(ONGOING_ID, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check above and the post.
        }
    }

    fun clear() {
        NotificationManagerCompat.from(appContext).cancel(ONGOING_ID)
    }

    /**
     * [Status.TimerPart] counts against [SystemClock.elapsedRealtime], while the
     * timer's own deadline is wall-clock so it can survive a reboot. Convert at
     * the moment of posting rather than storing two clocks.
     */
    private fun timerPart(state: TimerState, remaining: Long): Status.Part =
        if (state.running) {
            Status.TimerPart(SystemClock.elapsedRealtime() + remaining)
        } else {
            Status.TextPart(io.github.fisram.dhomate.domain.formatRemaining(remaining))
        }

    private fun toggleAction(state: TimerState): NotificationCompat.Action {
        val label = appContext.getString(
            if (state.running) R.string.action_pause else R.string.action_resume,
        )
        val intent = Intent(appContext, TimerActionReceiver::class.java)
            .setAction(TimerActionReceiver.ACTION_TOGGLE)
        val pending = PendingIntent.getBroadcast(
            appContext,
            REQUEST_TOGGLE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            if (state.running) R.drawable.ic_pause else R.drawable.ic_play,
            label,
            pending,
        ).build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        appContext,
        REQUEST_OPEN,
        Intent(appContext, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun canPost(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val ONGOING_ID = 2001
        const val REQUEST_OPEN = 200
        const val REQUEST_TOGGLE = 201
        const val TEMPLATE = "#phase# #timer#"
    }
}
