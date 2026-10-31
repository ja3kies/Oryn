package com.example.oryn.reader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ChromeColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val onAccent: Color,
    val outline: Color,
    val outlineVariant: Color,
)

@Immutable
data class EditorialColors(
    val teal: Color = Color(0xFF0F3A3A),
    val cherry: Color = Color(0xFFB5185F),
    val lime: Color = Color(0xFFD0E86E),
    val cyan: Color = Color(0xFF35C1C2),
    val parchment: Color = Color(0xFFFEFFDE),
    val darkSurface: Color = Color(0xFF081C1C),
    val darkCard: Color = Color(0xFF0D2727),
    val cherryOnDark: Color = Color(0xFFE65B91),
    val tealOnDark: Color = Color(0xFF4FBDBD),
    val cyanDeep: Color = Color(0xFF1A6B6B),
    val limeDeep: Color = Color(0xFF5A6B1E),
)

@Immutable
data class OrynColorScheme(
    val isDark: Boolean,
    val chrome: ChromeColors,
    val editorial: EditorialColors,
)

internal val LocalOrynColors = staticCompositionLocalOf<OrynColorScheme> {
    error("OrynTheme was not provided above this composable")
}

/** Access point for Oryn's interface and editorial brand colours. */
object OrynColors {
    val chrome: ChromeColors
        @Composable @ReadOnlyComposable
        get() = LocalOrynColors.current.chrome

    val editorial: EditorialColors
        @Composable @ReadOnlyComposable
        get() = LocalOrynColors.current.editorial

    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalOrynColors.current.isDark
}

@Composable
internal fun rememberOrynColorScheme(
    dark: Boolean,
    m3: ColorScheme,
): OrynColorScheme = remember(dark, m3.primaryContainer, m3.background) {
    OrynColorScheme(
        isDark = dark,
        chrome = ChromeColors(
            background = m3.background,
            surface = m3.surface,
            surfaceVariant = m3.surfaceVariant,
            onSurface = m3.onSurface,
            onSurfaceVariant = m3.onSurfaceVariant,
            primary = m3.primary,
            onPrimary = m3.onPrimary,
            accent = m3.primaryContainer,
            onAccent = m3.onPrimaryContainer,
            outline = m3.outline,
            outlineVariant = m3.outlineVariant,
        ),
        editorial = EditorialColors(),
    )
}
