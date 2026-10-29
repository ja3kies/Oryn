package com.example.oryn.reader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Accent d'Oryn : une seule couleur vive, utilisée en grand (cartes héroïques, sélection, progression).
 * Les noms ORANGE / FUCHSIA / GREEN / BLUE sont conservés car ils sont enregistrés dans les préférences.
 */
enum class AppAccentTheme(val label: String, val accentLight: Color, val accentDark: Color) {
    DYNAMIC("Chartreuse", Color(0xFFC8F135), Color(0xFFCBF53C)),
    ORANGE("Chartreuse", Color(0xFFC8F135), Color(0xFFCBF53C)),
    FUCHSIA("Citron", Color(0xFFFFE04A), Color(0xFFFFE45C)),
    GREEN("Corail", Color(0xFFFF7A59), Color(0xFFFF8F72)),
    BLUE("Chartreuse", Color(0xFFC8F135), Color(0xFFCBF53C));

    companion object { val choices: List<AppAccentTheme> get() = listOf(ORANGE, FUCHSIA, GREEN) }
}

enum class AppThemeMode(val label: String) { SYSTEM("Système"), LIGHT("Clair"), DARK("Sombre") }

/**
 * Palette Oryn : neutres presque monochromes (gris perle / noir encre) et un accent unique.
 * `primary` est l'encre (boutons noirs en clair, blancs en sombre) ; l'accent vit dans `primaryContainer`
 * (voir [accent]). Texte sur accent : toujours l'encre foncée (ratio > 10:1).
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF111111), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFC8F135), onPrimaryContainer = Color(0xFF141A00),
    secondary = Color(0xFF3B3B38), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFE4E4E0), onSecondaryContainer = Color(0xFF1A1A18),
    tertiary = Color(0xFF3F5A00), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFDDF58A), onTertiaryContainer = Color(0xFF141A00),
    background = Color(0xFFEFEFEC), onBackground = Color(0xFF111111),
    surface = Color(0xFFEFEFEC), onSurface = Color(0xFF111111), surfaceVariant = Color(0xFFE4E4E0), onSurfaceVariant = Color(0xFF55554F),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF6F6F3), surfaceContainer = Color(0xFFEAEAE6), surfaceContainerHigh = Color(0xFFE2E2DD), surfaceContainerHighest = Color(0xFFD9D9D4),
    outline = Color(0xFF787873), outlineVariant = Color(0xFFD3D3CD), inverseSurface = Color(0xFF1B1B1A), inverseOnSurface = Color(0xFFF2F2EF), inversePrimary = Color(0xFFF2F2EF), error = Color(0xFFBA1A1A), onError = Color.White
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF2F2EF), onPrimary = Color(0xFF111111), primaryContainer = Color(0xFFCBF53C), onPrimaryContainer = Color(0xFF141A00),
    secondary = Color(0xFFC9C9C3), onSecondary = Color(0xFF1A1A18), secondaryContainer = Color(0xFF2E2E2B), onSecondaryContainer = Color(0xFFE4E4E0),
    tertiary = Color(0xFFCBF53C), onTertiary = Color(0xFF141A00), tertiaryContainer = Color(0xFF3A4A00), onTertiaryContainer = Color(0xFFDDF58A),
    background = Color(0xFF121212), onBackground = Color(0xFFF2F2EF),
    surface = Color(0xFF121212), onSurface = Color(0xFFF2F2EF), surfaceVariant = Color(0xFF2A2A28), onSurfaceVariant = Color(0xFFB6B6B0),
    surfaceContainerLowest = Color(0xFF0B0B0B), surfaceContainerLow = Color(0xFF181817), surfaceContainer = Color(0xFF1E1E1D), surfaceContainerHigh = Color(0xFF262625), surfaceContainerHighest = Color(0xFF30302E),
    outline = Color(0xFF8C8C86), outlineVariant = Color(0xFF3A3A37), inverseSurface = Color(0xFFF2F2EF), inverseOnSurface = Color(0xFF1B1B1A), inversePrimary = Color(0xFF111111), error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
)

/** Schéma complet (neutres + accent choisi), utilisable pour une surface à part, par exemple le panneau de lecture. */
fun orynColorScheme(dark: Boolean, accentTheme: AppAccentTheme): ColorScheme {
    val accent = if (dark) accentTheme.accentDark else accentTheme.accentLight
    val base = if (dark) DarkScheme else LightScheme
    return base.copy(primaryContainer = accent, onPrimaryContainer = Color(0xFF141414), tertiary = if (dark) accent else base.tertiary)
}

/** Couleur d'accent courante (chartreuse par défaut) : fonds, sélection, progression. Texte dessus : [onAccent]. */
val ColorScheme.accent: Color get() = primaryContainer
val ColorScheme.onAccent: Color get() = onPrimaryContainer

/**
 * Échelle sobre : sérif réservée aux titres de page et de section (petits, serrés), sans-serif partout ailleurs.
 * Les métadonnées sont en petites capitales sans-serif espacées. Les tailles restent en sp (échelle système).
 */
private val OrynTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.8).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.6).sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp)
)

private val OrynShapes = Shapes(
    extraSmall = RoundedCornerShape(OrynSpace.xs), small = RoundedCornerShape(OrynShape.control),
    medium = RoundedCornerShape(OrynShape.medium), large = RoundedCornerShape(OrynShape.large), extraLarge = RoundedCornerShape(OrynShape.sheet)
)

@Composable
fun OrynTheme(darkTheme: Boolean = isSystemInDarkTheme(), accentTheme: AppAccentTheme = AppAccentTheme.ORANGE, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    // Seule la couleur d'accent change selon le choix de l'utilisateur ; les neutres restent identiques
    // pour garder les contrastes AA dans tous les cas.
    val accent = if (darkTheme) accentTheme.accentDark else accentTheme.accentLight
    val base = if (darkTheme) DarkScheme else LightScheme
    val scheme = base.copy(
        primaryContainer = accent,
        onPrimaryContainer = Color(0xFF141414),
        tertiary = if (darkTheme) accent else base.tertiary
    )
    MaterialTheme(colorScheme = scheme, typography = OrynTypography, shapes = OrynShapes, content = content)
}

@Composable
fun AppAccentTheme.previewColor(dark: Boolean): Color = if (dark) accentDark else accentLight
