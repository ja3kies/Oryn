package com.example.oryn.reader.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.dp

/** Shared layout and motion values; spacing follows a 4 dp base grid. */
object OrynSpace {
    val xxs = 4.dp; val xs = 8.dp; val sm = 12.dp; val md = 16.dp
    val lg = 24.dp; val xl = 32.dp; val xxl = 40.dp; val section = 48.dp
}

object OrynShape {
    val control = 12.dp; val medium = 20.dp; val card = 24.dp; val large = 28.dp; val sheet = 32.dp; val pill = 100.dp
}

object OrynElevation {
    val resting = 1.dp; val raised = 3.dp; val floating = 8.dp; val modal = 16.dp
}

object OrynMotion {
    const val quick = 140; const val standard = 220; const val enter = 320
    val editorial = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    // Default to critically damped motion; reserve bounce for interactions with real gesture momentum.
    val spring = Spring.DampingRatioNoBouncy to Spring.StiffnessMediumLow
}
