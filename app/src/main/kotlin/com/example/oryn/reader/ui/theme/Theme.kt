package com.example.oryn.reader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val DeepTeal = Color(0xFF0F3A3A)
private val LightYellow = Color(0xFFFEFFDE)
private val CherryRose = Color(0xFFB5185F)
private val LimeCream = Color(0xFFD0E86E)
private val StrongCyan = Color(0xFF35C1C2)
private val DarkSurface = Color(0xFF081C1C)
private val DarkCard = Color(0xFF0D2727)

/**
 * Les noms d'enum sont persistés dans DataStore : ne pas les renommer.
 * Les choix historiques pointent désormais vers les accents de la palette Oryn.
 */
enum class AppAccentTheme(val label: String, val accentLight: Color, val accentDark: Color) {
    DYNAMIC("Lime Cream", LimeCream, LimeCream),
    ORANGE("Lime Cream", LimeCream, LimeCream),
    FUCHSIA("Cherry Rose", CherryRose, CherryRose),
    GREEN("Strong Cyan", StrongCyan, StrongCyan),
    BLUE("Strong Cyan", StrongCyan, StrongCyan);

    companion object { val choices: List<AppAccentTheme> get() = listOf(ORANGE, FUCHSIA, GREEN) }
}

enum class AppThemeMode(val label: String) { SYSTEM("Système"), LIGHT("Clair"), DARK("Sombre") }

private fun onAccentFor(accent: Color): Color =
    if (accent == CherryRose) LightYellow else DeepTeal

private val LightScheme = lightColorScheme(
    primary = DeepTeal,
    onPrimary = LightYellow,
    primaryContainer = LimeCream,
    onPrimaryContainer = DeepTeal,
    secondary = StrongCyan,
    onSecondary = DeepTeal,
    secondaryContainer = Color(0xFFD9F4EF),
    onSecondaryContainer = DeepTeal,
    tertiary = CherryRose,
    onTertiary = LightYellow,
    tertiaryContainer = Color(0xFFF6DCE7),
    onTertiaryContainer = DeepTeal,
    background = LightYellow,
    onBackground = DeepTeal,
    surface = Color(0xFFFFFFEE),
    onSurface = DeepTeal,
    surfaceVariant = Color(0xFFF0F2DD),
    onSurfaceVariant = Color(0xFF405E59),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFEE),
    surfaceContainer = Color(0xFFF3F5E4),
    surfaceContainerHigh = Color(0xFFE9EDD5),
    surfaceContainerHighest = Color(0xFFDEE5C8),
    outline = Color(0xFF607A72),
    outlineVariant = Color(0xFFC7D2B9),
    inverseSurface = DeepTeal,
    inverseOnSurface = LightYellow,
    inversePrimary = LimeCream,
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkScheme = darkColorScheme(
    primary = LightYellow,
    onPrimary = DeepTeal,
    primaryContainer = LimeCream,
    onPrimaryContainer = DeepTeal,
    secondary = StrongCyan,
    onSecondary = DeepTeal,
    secondaryContainer = Color(0xFF164846),
    onSecondaryContainer = LightYellow,
    tertiary = Color(0xFFE65B91),
    onTertiary = DarkSurface,
    tertiaryContainer = Color(0xFF51213A),
    onTertiaryContainer = Color(0xFFFFEAF2),
    background = DarkSurface,
    onBackground = LightYellow,
    surface = DarkCard,
    onSurface = LightYellow,
    surfaceVariant = Color(0xFF173634),
    onSurfaceVariant = Color(0xFFC2D4C8),
    surfaceContainerLowest = Color(0xFF061615),
    surfaceContainerLow = Color(0xFF0A2221),
    surfaceContainer = Color(0xFF102D2B),
    surfaceContainerHigh = Color(0xFF173634),
    surfaceContainerHighest = Color(0xFF21423F),
    outline = Color(0xFF6C8983),
    outlineVariant = Color(0xFF355650),
    inverseSurface = LightYellow,
    inverseOnSurface = DeepTeal,
    inversePrimary = DeepTeal,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

/** Schéma complet pour les surfaces qui utilisent le thème Oryn, dont le panneau de lecture. */
fun orynColorScheme(dark: Boolean, accentTheme: AppAccentTheme): ColorScheme {
    val accent = if (dark) accentTheme.accentDark else accentTheme.accentLight
    val base = if (dark) DarkScheme else LightScheme
    return base.copy(
        primaryContainer = accent,
        onPrimaryContainer = onAccentFor(accent),
        tertiary = if (dark) Color(0xFFE65B91) else CherryRose
    )
}

/** Accent sélectionné pour les états de progression et de sélection. */
val ColorScheme.accent: Color get() = primaryContainer
val ColorScheme.onAccent: Color get() = onPrimaryContainer

/**
 * Typographie système : lisible, native et compatible avec les réglages de taille Android.
 * Les polices de lecture restent indépendantes de l'interface.
 */
private val OrynTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-1.1).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.35).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.35.sp)
)

private val OrynShapes = Shapes(
    extraSmall = RoundedCornerShape(OrynSpace.xs),
    small = RoundedCornerShape(OrynShape.control),
    medium = RoundedCornerShape(OrynShape.medium),
    large = RoundedCornerShape(OrynShape.large),
    extraLarge = RoundedCornerShape(OrynShape.sheet)
)

@Composable
fun OrynTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentTheme: AppAccentTheme = AppAccentTheme.ORANGE,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val accent = if (darkTheme) accentTheme.accentDark else accentTheme.accentLight
    val base = if (darkTheme) DarkScheme else LightScheme
    val scheme = base.copy(
        primaryContainer = accent,
        onPrimaryContainer = onAccentFor(accent)
    )
    val orynColors = rememberOrynColorScheme(darkTheme, scheme)
    CompositionLocalProvider(LocalOrynColors provides orynColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = OrynTypography,
            shapes = OrynShapes,
            content = content
        )
    }
}

@Composable
fun AppAccentTheme.previewColor(dark: Boolean): Color =
    if (dark) accentDark else accentLight
