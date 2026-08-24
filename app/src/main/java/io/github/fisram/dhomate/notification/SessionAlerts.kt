package io.github.fisram.dhomate.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.fisram.dhomate.R
import io.github.fisram.dhomate.domain.TimerPhase
import io.github.fisram.dhomate.ui.MainActivity

/** Fires the end-of-phase alert on the [Channels.ALERTS] channel. */
class SessionAlerts(context: Context) {

    private val appContext = context.applicationContext

    fun notifyPhaseComplete(finished: TimerPhase, next: TimerPhase) {
        if (!canPost()) return
        Channels.ensure(appContext)

        val notification = NotificationCompat.Builder(appContext, Channels.ALERTS)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(appContext.getString(titleFor(finished)))
            .setContentText(appContext.getString(textFor(next)))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify(ALERT_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check above and the post.
        }
    }

    private fun titleFor(finished: TimerPhase) = when (finished) {
        TimerPhase.FOCUS -> R.string.alert_focus_complete
        TimerPhase.SHORT_BREAK, TimerPhase.LONG_BREAK -> R.string.alert_break_complete
    }

    private fun textFor(next: TimerPhase) = when (next) {
        TimerPhase.FOCUS -> R.string.alert_next_focus
        TimerPhase.SHORT_BREAK -> R.string.alert_next_short_break
        TimerPhase.LONG_BREAK -> R.string.alert_next_long_break
    }

    private fun canPost(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        appContext,
        REQUEST_OPEN,
        Intent(appContext, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val ALERT_ID = 1001
        const val REQUEST_OPEN = 101
    }
}
