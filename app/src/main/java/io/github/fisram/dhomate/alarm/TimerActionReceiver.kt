package io.github.fisram.dhomate.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.fisram.dhomate.timerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the controls exposed outside the app: the ongoing notification action
 * and the buttons on the tile.
 */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        val engine = context.timerEngine

        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (action) {
                    ACTION_TOGGLE -> engine.toggle()
                    ACTION_RESET -> engine.reset()
                    ACTION_SKIP -> engine.skip()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "io.github.fisram.dhomate.action.TOGGLE"
        const val ACTION_RESET = "io.github.fisram.dhomate.action.RESET"
        const val ACTION_SKIP = "io.github.fisram.dhomate.action.SKIP"
    }
}
