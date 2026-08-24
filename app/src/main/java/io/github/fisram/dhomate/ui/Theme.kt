package io.github.fisram.dhomate.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme

/**
 * Pitch black. Those pixels are simply off on an OLED watch, which is why a
 * separate power-saving mode had nothing left to switch off.
 */
val Background = Color(0xFF000000)

/** Neutral fill for secondary controls, matching the system timer's dark pills. */
val Surface = Color(0xFF1E1B1A)

/** Tomato: the focus accent and the app's identity colour. */
val Tomato = Color(0xFFFF5241)

/** Muted tomato for the large play control, so it is not glaring at night. */
val TomatoSoft = Color(0xFFFFB4A6)

/** Leaf green - a break should read as growing rather than working. */
val Leaf = Color(0xFF6FCF7F)
val LeafSoft = Color(0xFFB9EFC1)

/** Flow runs long, so it gets a warmer, calmer amber. */
val Amber = Color(0xFFFFAA5C)
val AmberSoft = Color(0xFFFFD9B0)

val TextPrimary = Color(0xFFF4EFEE)
val TextSecondary = Color(0xFF9E9491)
val Track = Color(0xFF2A2422)

/**
 * Wear Material3 already defaults to a dark scheme, which is what a watch wants.
 * The colours above are applied at the call site rather than threaded through a
 * bespoke ColorScheme.
 */
@Composable
fun DhomateTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
