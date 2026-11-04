package com.example.oryn.reader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class OrynContrastTest {
    private fun ratio(a: Color, b: Color): Float {
        val light = maxOf(a.luminance(), b.luminance())
        val dark = minOf(a.luminance(), b.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test fun semanticTextAndSelectionColorsMeetWcagAa() {
        for (dark in listOf(false, true)) {
            for (accent in AppAccentTheme.entries) {
                val scheme: ColorScheme = orynColorScheme(dark, accent)
                val pairs = listOf(
                    "background" to (scheme.onBackground to scheme.background),
                    "surface" to (scheme.onSurface to scheme.surface),
                    "surface variant" to (scheme.onSurfaceVariant to scheme.surfaceVariant),
                    "primary action" to (scheme.onPrimary to scheme.primary),
                    "selected accent" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                    "secondary" to (scheme.onSecondary to scheme.secondary),
                    "tertiary" to (scheme.onTertiary to scheme.tertiary)
                )
                pairs.forEach { (name, colors) ->
                    assertTrue("$name contrast too low (dark=$dark accent=$accent): ${ratio(colors.first, colors.second)}",
                        ratio(colors.first, colors.second) >= 4.5f)
                }
            }
        }
    }
}
