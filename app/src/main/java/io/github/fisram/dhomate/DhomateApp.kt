package io.github.fisram.dhomate

import android.app.Application
import android.content.Context
import io.github.fisram.dhomate.engine.TimerEngine

class DhomateApp : Application() {

    lateinit var timerEngine: TimerEngine
        private set

    override fun onCreate() {
        super.onCreate()
        timerEngine = TimerEngine(this)
    }
}

/**
 * The engine is process-wide: the activity, the tile, the complication and the
 * alarm receiver all reach it through here rather than building their own.
 */
val Context.timerEngine: TimerEngine
    get() = (applicationContext as DhomateApp).timerEngine
