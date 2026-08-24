package io.github.fisram.dhomate.engine

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import io.github.fisram.dhomate.complication.RemainingComplicationService
import io.github.fisram.dhomate.tile.PomodoroTileService

/** Nudges the tile and the complication to re-read state after a transition. */
object Surfaces {

    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            TileService.getUpdater(appContext).requestUpdate(PomodoroTileService::class.java)
        }
        runCatching {
            ComplicationDataSourceUpdateRequester.create(
                appContext,
                ComponentName(appContext, RemainingComplicationService::class.java),
            ).requestUpdateAll()
        }
    }
}
