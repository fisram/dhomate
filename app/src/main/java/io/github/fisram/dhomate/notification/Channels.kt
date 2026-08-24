package io.github.fisram.dhomate.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import io.github.fisram.dhomate.R

/**
 * The app's two notification channels.
 *
 * A channel's sound and vibration are frozen at creation, so each id carries a
 * version suffix - bump it whenever the settings below change, otherwise
 * existing installs silently keep the old configuration.
 */
object Channels {

    const val ALERTS = "session_alerts_v2"

    /** Superseded ids, deleted so they do not linger in system settings. */
    private val RETIRED = listOf("session_alerts_v1")
    const val ONGOING = "timer_status_v1"

    private val ALERT_VIBRATION = longArrayOf(0, 350, 200, 350, 200, 350)

    @Volatile
    private var initialized = false

    fun ensure(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            RETIRED.forEach { manager.deleteNotificationChannel(it) }
            manager.createNotificationChannel(alertChannel(context))
            manager.createNotificationChannel(ongoingChannel(context))
            initialized = true
        }
    }

    /**
     * Deliberately an **alarm**, not a notification.
     *
     * On USAGE_NOTIFICATION this played on STREAM_NOTIFICATION, which sits at
     * 1/7 on this watch while STREAM_ALARM sits at 5/7 - the tone fired
     * correctly and was simply inaudible. Alarm usage is what the system timer
     * uses, and it inherits the "alarms are allowed" exception under Do Not
     * Disturb rather than being swallowed by it.
     *
     * It still carries a vibration pattern, so a silenced watch buzzes instead.
     * That choice stays with the platform.
     */
    private fun alertChannel(context: Context) = NotificationChannel(
        ALERTS,
        context.getString(R.string.channel_alerts_name),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = context.getString(R.string.channel_alerts_description)
        enableVibration(true)
        vibrationPattern = ALERT_VIBRATION
        setSound(
            alertSoundUri(),
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build(),
        )
        setShowBadge(false)
    }

    /** Silent: this one only carries the persistent countdown chip. */
    private fun ongoingChannel(context: Context) = NotificationChannel(
        ONGOING,
        context.getString(R.string.channel_ongoing_name),
        NotificationManager.IMPORTANCE_LOW,
    ).apply {
        description = context.getString(R.string.channel_ongoing_description)
        enableVibration(false)
        setSound(null, null)
        setShowBadge(false)
    }

    /**
     * Watches do not always have a default notification tone configured, and a
     * null uri would leave the channel silent. Fall back through the other
     * system tones before giving up on sound altogether.
     */
    private fun alertSoundUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
}
