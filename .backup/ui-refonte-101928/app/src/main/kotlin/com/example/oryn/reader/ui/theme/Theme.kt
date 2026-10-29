package com.example.oryn.reader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
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

enum class AppAccentTheme(val label: String, val primaryLight: Color, val primaryDark: Color) {
    DYNAMIC("Dynamique", Color(0xFF80513C), Color(0xFFE4B095)),
    ORANGE("Terre cuite", Color(0xFF80432B), Color(0xFFEAB193)),
    FUCHSIA("Ambre", Color(0xFF73500D), Color(0xFFE7BD68)),
    GREEN("Sauge", Color(0xFF456344), Color(0xFFA8C99B)),
    BLUE("Sauge", Color(0xFF456344), Color(0xFFA8C99B));

    companion object { val choices: List<AppAccentTheme> get() = listOf(ORANGE, FUCHSIA, GREEN) }
}

enum class AppThemeMode(val label: String) { SYSTEM("Système"), LIGHT("Clair"), DARK("Sombre") }

/** Palette éditoriale Oryn: papier et encre en clair, surfaces brunes superposées en sombre. */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF80432B), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFF4D8C9), onPrimaryContainer = Color(0xFF35170C),
    secondary = Color(0xFF456344), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFD9E8D2), onSecondaryContainer = Color(0xFF142612),
    tertiary = Color(0xFF73500D), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFF4E2B9), onTertiaryContainer = Color(0xFF291B00),
    background = Color(0xFFFAF7F1), onBackground = Color(0xFF241D19),
    surface = Color(0xFFFAF7F1), onSurface = Color(0xFF241D19), surfaceVariant = Color(0xFFECE5DC), onSurfaceVariant = Color(0xFF50463F),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF5F0E9), surfaceContainer = Color(0xFFF0EAE2), surfaceContainerHigh = Color(0xFFE9E1D8), surfaceContainerHighest = Color(0xFFE2D9CF),
    outline = Color(0xFF81766E), outlineVariant = Color(0xFFD2C8BE), inverseSurface = Color(0xFF352C26), inverseOnSurface = Color(0xFFF7F0E9), inversePrimary = Color(0xFFEAB193), error = Color(0xFFBA1A1A), onError = Color.White
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFEAB193), onPrimary = Color(0xFF35170C), primaryContainer = Color(0xFF60301D), onPrimaryContainer = Color(0xFFFFDBCA),
    secondary = Color(0xFFA8C99B), onSecondary = Color(0xFF1C351D), secondaryContainer = Color(0xFF344D34), onSecondaryContainer = Color(0xFFD3EBC9),
    tertiary = Color(0xFFE7BD68), onTertiary = Color(0xFF392900), tertiaryContainer = Color(0xFF56400C), onTertiaryContainer = Color(0xFFF8E2AF),
    background = Color(0xFF191512), onBackground = Color(0xFFEFE7DE),
    surface = Color(0xFF191512), onSurface = Color(0xFFEFE7DE), surfaceVariant = Color(0xFF38302A), onSurfaceVariant = Color(0xFFD0C3B8),
    surfaceContainerLowest = Color(0xFF120F0D), surfaceContainerLow = Color(0xFF211B17), surfaceContainer = Color(0xFF28211C), surfaceContainerHigh = Color(0xFF302821), surfaceContainerHighest = Color(0xFF3A3028),
    outline = Color(0xFF9A8C80), outlineVariant = Color(0xFF51463D), inverseSurface = Color(0xFFEFE7DE), inverseOnSurface = Color(0xFF352C26), inversePrimary = Color(0xFF80432B), error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
)

/** Titres sérif, interface sans-serif: les tailles restent en sp et suivent l'échelle système. */
private val OrynTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp)
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
    // L'accent utilisateur se limite aux trois teintes naturelles du système. Les surfaces gardent
    // la même base chaude pour assurer la cohérence et les ratios AA dans tous les choix.
    val scheme = when (accentTheme) {
        AppAccentTheme.GREEN -> if (darkTheme) DarkScheme.copy(primary = Color(0xFFA8C99B), onPrimary = Color(0xFF1C351D)) else LightScheme.copy(primary = Color(0xFF456344), onPrimary = Color.White)
        AppAccentTheme.FUCHSIA -> if (darkTheme) DarkScheme.copy(primary = Color(0xFFE7BD68), onPrimary = Color(0xFF392900)) else LightScheme.copy(primary = Color(0xFF73500D), onPrimary = Color.White)
        else -> if (darkTheme) DarkScheme else LightScheme
    }
    MaterialTheme(colorScheme = scheme, typography = OrynTypography, shapes = OrynShapes, content = content)
}

@Composable
fun AppAccentTheme.previewColor(dark: Boolean): Color = when (this) {
    AppAccentTheme.GREEN, AppAccentTheme.BLUE -> if (dark) Color(0xFFA8C99B) else Color(0xFF456344)
    AppAccentTheme.FUCHSIA -> if (dark) Color(0xFFE7BD68) else Color(0xFF73500D)
    else -> if (dark) Color(0xFFEAB193) else Color(0xFF80432B)
}
