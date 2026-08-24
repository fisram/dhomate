package io.github.fisram.dhomate.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import io.github.fisram.dhomate.engine.TimerEngine
import io.github.fisram.dhomate.timerEngine
import kotlinx.coroutines.launch

private const val ROUTE_TIMER = "timer"
private const val ROUTE_SETTINGS = "settings"
private const val REQUEST_NOTIFICATIONS = 1

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationsIfNeeded()

        val engine = timerEngine
        lifecycleScope.launch { engine.ensureLoaded() }

        setContent {
            val ambientModeManager = rememberAmbientModeManager()
            CompositionLocalProvider(LocalAmbientModeManager provides ambientModeManager) {
                DhomateTheme {
                    DhomateRoot(engine)
                }
            }
        }
    }

    /**
     * minSdk is 33, so POST_NOTIFICATIONS is always a runtime permission here -
     * no version branch needed. Without it both the end-of-phase alert and the
     * ongoing countdown chip are dropped silently.
     */
    private fun requestNotificationsIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }
}

/**
 * Settings is a pushed destination rather than a mode flag, so the platform's
 * swipe-right-to-go-back gesture works on it without a confirmation button.
 */
@Composable
private fun DhomateRoot(engine: TimerEngine) {
    val ready by engine.ready.collectAsStateWithLifecycle()
    if (!ready) {
        Box(Modifier.fillMaxSize().background(Background))
        return
    }

    val state by engine.state.collectAsStateWithLifecycle()
    val settings by engine.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = ROUTE_TIMER,
    ) {
        composable(ROUTE_TIMER) {
            TimerScreen(
                state = state,
                settings = settings,
                onToggle = { scope.launch { engine.toggle() } },
                onReset = { scope.launch { engine.reset() } },
                onSkip = { scope.launch { engine.skip() } },
                onSwitchMode = { mode -> scope.launch { engine.switchMode(mode) } },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
            )
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                settings = settings,
                onUpdate = { transform -> scope.launch { engine.updateSettings(transform) } },
            )
        }
    }
}
