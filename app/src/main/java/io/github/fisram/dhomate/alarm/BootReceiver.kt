package io.github.fisram.dhomate.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.fisram.dhomate.timerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A reboot clears every scheduled alarm, but the deadline is stored as wall
 * clock so it is still valid - re-arm it rather than losing the session.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        val engine = context.timerEngine
        CoroutineScope(Dispatchers.Default).launch {
            try {
                engine.restoreAfterBoot()
            } finally {
                pending.finish()
            }
        }
    }
}
