package com.example.oryn.reader.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.oryn.reader.ui.theme.AppAccentTheme
import com.example.oryn.reader.ui.theme.orynColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression: Sheet and Pill share the same app chrome, never a forced dark sheet. */
class ReaderChromeThemeTest {
    @Test fun lightAndDarkAppChromeProduceMatchingReaderOverlaySchemes() {
        for (dark in listOf(false, true)) {
            for (accent in AppAccentTheme.choices) {
                val app = orynColorScheme(dark, accent)
                val reader = readerChromeSchemeFor(app.background, accent)
                assertEquals("surface dark=$dark accent=$accent", app.surface, reader.surface)
                assertEquals("onSurface dark=$dark accent=$accent", app.onSurface, reader.onSurface)
                assertEquals("selection dark=$dark accent=$accent", app.primaryContainer, reader.primaryContainer)
                assertEquals(dark, isDarkReaderChrome(app.background))
            }
        }
    }

    @Test fun neutralTextAndSelectedLabelsPassWcagAaForAllAccents() {
        fun contrast(foreground: Color, background: Color): Float {
            val a = foreground.luminance()
            val b = background.luminance()
            return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
        }
        for (dark in listOf(false, true)) {
            for (accent in AppAccentTheme.choices) {
                val scheme = readerChromeSchemeFor(orynColorScheme(dark, accent).background, accent)
                assertTrue("Text contrast dark=$dark accent=$accent",
                    contrast(scheme.onSurface, scheme.surface) >= 4.5f)
                assertTrue("Accent label contrast dark=$dark accent=$accent",
                    contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
            }
        }
    }
}
