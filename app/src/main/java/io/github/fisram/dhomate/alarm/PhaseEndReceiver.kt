package io.github.fisram.dhomate.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.fisram.dhomate.engine.AlarmScheduler
import io.github.fisram.dhomate.timerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fired by [android.app.AlarmManager] at the instant a phase ends. */
class PhaseEndReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PHASE_END) return

        val pending = goAsync()
        val engine = context.timerEngine
        CoroutineScope(Dispatchers.Default).launch {
            try {
                engine.onPhaseEnd(
                    intent.getLongExtra(AlarmScheduler.EXTRA_DEADLINE, 0L),
                )
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PHASE_END = "io.github.fisram.dhomate.action.PHASE_END"
    }
}
