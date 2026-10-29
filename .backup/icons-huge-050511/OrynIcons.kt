package com.example.oryn.reader.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icônes Lucide (https://lucide.dev, licence ISC) : trait de 2, extrémités et angles arrondis, grille 24.
 * Elles se teintent comme n'importe quelle icône Material (paramètre `tint`).
 * Les variantes « Filled » (accueil, favori, lecture) remplissent la forme en plus du trait.
 */
object OrynIcons {
    private fun lucide(name: String, filled: Boolean, vararg d: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        d.forEach { p ->
            b.addPath(
                pathData = PathParser().parsePathString(p).toNodes(),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return b.build()
    }

    val Search: ImageVector by lazy {
        lucide("Search", false,
            "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0",
            "m21 21-4.3-4.3"
        )
    }

    val Close: ImageVector by lazy {
        lucide("Close", false,
            "M18 6 6 18",
            "m6 6 12 12"
        )
    }

    val Check: ImageVector by lazy {
        lucide("Check", false,
            "M20 6 9 17l-5-5"
        )
    }

    val Edit: ImageVector by lazy {
        lucide("Edit", false,
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
            "m15 5 4 4"
        )
    }

    val Highlighter: ImageVector by lazy {
        lucide("Highlighter", false,
            "m9 11-6 6v3h9l3-3",
            "m22 12-4.6 4.6a2 2 0 0 1-2.8 0l-5.2-5.2a2 2 0 0 1 0-2.8L14 4"
        )
    }

    val Toc: ImageVector by lazy {
        lucide("Toc", false,
            "M21 12h-8",
            "M21 6H8",
            "M21 18h-8",
            "M3 6v4c0 1.1.9 2 2 2h3",
            "M3 10v6c0 1.1.9 2 2 2h3"
        )
    }

    val Grid: ImageVector by lazy {
        lucide("Grid", false,
            "M4 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1z",
            "M15 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1z",
            "M15 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1z",
            "M4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1z"
        )
    }

    val List: ImageVector by lazy {
        lucide("List", false,
            "M3 12h.01",
            "M3 18h.01",
            "M3 6h.01",
            "M8 12h13",
            "M8 18h13",
            "M8 6h13"
        )
    }

    val Sort: ImageVector by lazy {
        lucide("Sort", false,
            "m21 16-4 4-4-4",
            "M17 20V4",
            "m3 8 4-4 4 4",
            "M7 4v16"
        )
    }

    val Sliders: ImageVector by lazy {
        lucide("Sliders", false,
            "M21 4L14 4",
            "M10 4L3 4",
            "M21 12L12 12",
            "M8 12L3 12",
            "M21 20L16 20",
            "M12 20L3 20",
            "M14 2L14 6",
            "M8 10L8 14",
            "M16 18L16 22"
        )
    }

    val Play: ImageVector by lazy {
        lucide("Play", true,
            "M6 3L20 12L6 21L6 3z"
        )
    }

    val Info: ImageVector by lazy {
        lucide("Info", false,
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
            "M12 16v-4",
            "M12 8h.01"
        )
    }

    val Trash: ImageVector by lazy {
        lucide("Trash", false,
            "M3 6h18",
            "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6",
            "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2",
            "M10 11L10 17",
            "M14 11L14 17"
        )
    }

    val Image: ImageVector by lazy {
        lucide("Image", false,
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
            "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"
        )
    }

    val Clock: ImageVector by lazy {
        lucide("Clock", false,
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
            "M12 6L12 12L16 14"
        )
    }

    val Star: ImageVector by lazy {
        lucide("Star", false,
            "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z"
        )
    }

    val Compass: ImageVector by lazy {
        lucide("Compass", false,
            "m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0"
        )
    }

    val CompassFilled: ImageVector by lazy {
        lucide("CompassFilled", false,
            "m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0"
        )
    }

    val Plus: ImageVector by lazy {
        lucide("Plus", false,
            "M5 12h14",
            "M12 5v14"
        )
    }

    val Sun: ImageVector by lazy {
        lucide("Sun", false,
            "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
            "M12 2v2",
            "M12 20v2",
            "m4.93 4.93 1.41 1.41",
            "m17.66 17.66 1.41 1.41",
            "M2 12h2",
            "M20 12h2",
            "m6.34 17.66-1.41 1.41",
            "m19.07 4.93-1.41 1.41"
        )
    }

    val Moon: ImageVector by lazy {
        lucide("Moon", false,
            "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"
        )
    }

    val AutoTheme: ImageVector by lazy {
        lucide("AutoTheme", false,
            "M12 8a2.83 2.83 0 0 0 4 4 4 4 0 1 1-4-4",
            "M12 2v2",
            "M12 20v2",
            "m4.9 4.9 1.4 1.4",
            "m17.7 17.7 1.4 1.4",
            "M2 12h2",
            "M20 12h2",
            "m6.3 17.7-1.4 1.4",
            "m19.1 4.9-1.4 1.4"
        )
    }

    val Palette: ImageVector by lazy {
        lucide("Palette", false,
            "M13 6.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0",
            "M17 10.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0",
            "M8 7.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0",
            "M6 12.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0",
            "M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.926 0 1.648-.746 1.648-1.688 0-.437-.18-.835-.437-1.125-.29-.289-.438-.652-.438-1.125a1.64 1.64 0 0 1 1.668-1.668h1.996c3.051 0 5.555-2.503 5.555-5.554C21.965 6.012 17.461 2 12 2z"
        )
    }

    val Sparkle: ImageVector by lazy {
        lucide("Sparkle", false,
            "M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z",
            "M20 3v4",
            "M22 5h-4",
            "M4 17v2",
            "M5 18H3"
        )
    }

    val BookOpen: ImageVector by lazy {
        lucide("BookOpen", false,
            "M12 7v14",
            "M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z"
        )
    }

    val EyeOff: ImageVector by lazy {
        lucide("EyeOff", false,
            "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49",
            "M14.084 14.158a3 3 0 0 1-4.242-4.242",
            "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143",
            "m2 2 20 20"
        )
    }

    val Download: ImageVector by lazy {
        lucide("Download", false,
            "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
            "M7 10L12 15L17 10",
            "M12 15L12 3"
        )
    }

    val ArrowLeft: ImageVector by lazy {
        lucide("ArrowLeft", false,
            "m12 19-7-7 7-7",
            "M19 12H5"
        )
    }

    val Refresh: ImageVector by lazy {
        lucide("Refresh", false,
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
            "M8 16H3v5"
        )
    }

    val External: ImageVector by lazy {
        lucide("External", false,
            "M15 3h6v6",
            "M10 14 21 3",
            "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"
        )
    }

    val Minus: ImageVector by lazy {
        lucide("Minus", false,
            "M5 12h14"
        )
    }

    val Font: ImageVector by lazy {
        lucide("Font", false,
            "M4 7L4 4L20 4L20 7",
            "M9 20L15 20",
            "M12 4L12 20"
        )
    }

    val TextSize: ImageVector by lazy {
        lucide("TextSize", false,
            "M21 14h-5",
            "M16 16v-3.5a2.5 2.5 0 0 1 5 0V16",
            "M4.5 13h6",
            "m3 16 4.5-9 4.5 9"
        )
    }

    val LineSpacing: ImageVector by lazy {
        lucide("LineSpacing", false,
            "M7 15h10a2 2 0 0 1 2 2v2a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-2a2 2 0 0 1 2 -2z",
            "M9 3h6a2 2 0 0 1 2 2v2a2 2 0 0 1 -2 2h-6a2 2 0 0 1 -2 -2v-2a2 2 0 0 1 2 -2z",
            "M2 21h20",
            "M2 3h20"
        )
    }

    val AlignLeft: ImageVector by lazy {
        lucide("AlignLeft", false,
            "M15 12H3",
            "M17 18H3",
            "M21 6H3"
        )
    }

    val AlignCenter: ImageVector by lazy {
        lucide("AlignCenter", false,
            "M17 12H7",
            "M19 18H5",
            "M21 6H3"
        )
    }

    val AlignJustify: ImageVector by lazy {
        lucide("AlignJustify", false,
            "M3 12h18",
            "M3 18h18",
            "M3 6h18"
        )
    }

    val ScrollVertical: ImageVector by lazy {
        lucide("ScrollVertical", false,
            "m7 15 5 5 5-5",
            "m7 9 5-5 5 5"
        )
    }

    val Pages: ImageVector by lazy {
        lucide("Pages", false,
            "M12 7v14",
            "M16 12h2",
            "M16 8h2",
            "M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z",
            "M6 12h2",
            "M6 8h2"
        )
    }

    val SunAuto: ImageVector by lazy {
        lucide("SunAuto", false,
            "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
            "M12 4h.01",
            "M20 12h.01",
            "M12 20h.01",
            "M4 12h.01",
            "M17.657 6.343h.01",
            "M17.657 17.657h.01",
            "M6.343 17.657h.01",
            "M6.343 6.343h.01"
        )
    }

    val Menu: ImageVector by lazy {
        lucide("Menu", false,
            "M4 12L20 12",
            "M4 6L20 6",
            "M4 18L20 18"
        )
    }

    val Chevron: ImageVector by lazy {
        lucide("Chevron", false,
            "m9 18 6-6-6-6"
        )
    }

    val Globe: ImageVector by lazy {
        lucide("Globe", false,
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
            "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
            "M2 12h20"
        )
    }

    val MarginNarrow: ImageVector by lazy {
        lucide("MarginNarrow", false,
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M6 8v8",
            "M18 8v8"
        )
    }

    val MarginNormal: ImageVector by lazy {
        lucide("MarginNormal", false,
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M8 8v8",
            "M16 8v8"
        )
    }

    val MarginWide: ImageVector by lazy {
        lucide("MarginWide", false,
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M10 8v8",
            "M14 8v8"
        )
    }


    /* ── Navigation : jeu Solar « Linear », deux états. Contour = inactif, plein = actif. ── */
    private fun solarLine(name: String, vararg d: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        d.forEach { p ->
            b.addPath(
                pathData = PathParser().parsePathString(p).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return b.build()
    }

    private fun solarFilled(name: String, d: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
                fill = SolidColor(Color.Black)
            )
        }.build()

    val NavHome: ImageVector by lazy {
        solarLine("NavHome",
            "M3 11.99v2.51c0 3.3 0 4.95 1.025 5.975S6.7 21.5 10 21.5h4c3.3 0 4.95 0 5.975-1.025S21 17.8 21 14.5v-2.51c0-1.682 0-2.522-.356-3.25s-1.02-1.244-2.346-2.276l-2-1.555C14.233 3.303 13.2 2.5 12 2.5s-2.233.803-4.298 2.409l-2 1.555C4.375 7.496 3.712 8.012 3.356 8.74S3 10.308 3 11.99M16 17H8"
        )
    }

    val NavHomeFilled: ImageVector by lazy {
        solarFilled("NavHomeFilled",
            "M2.26,10.70L2.25,15.67L2.28,17.02L2.31,17.63L2.35,18.19L2.43,18.71L2.52,19.17L2.65,19.62L2.82,20.05L3.05,20.45L3.21,20.68L3.51,21.02L3.82,21.29L4.20,21.55L4.62,21.75L5.10,21.92L5.54,22.03L5.82,22.08L6.60,22.17L7.80,22.23L9.21,22.25L15.52,22.25L16.82,22.21L17.69,22.15L18.43,22.03L18.93,21.91L19.34,21.77L19.55,21.68L19.95,21.45L20.32,21.18L20.68,20.82L20.95,20.45L21.07,20.25L21.25,19.88L21.35,19.62L21.47,19.20L21.53,18.93L21.62,18.43L21.67,17.91L21.71,17.33L21.75,16.03L21.75,11.40L21.73,10.38L21.69,9.81L21.63,9.39L21.51,8.90L21.38,8.54L21.13,8.08L20.81,7.65L20.35,7.17L19.78,6.67L16.26,3.93L15.17,3.10L14.59,2.70L14.22,2.46L13.84,2.25L13.48,2.07L12.96,1.89L12.59,1.80L12.21,1.76L11.83,1.75L11.45,1.80L11.04,1.89L10.68,2.01L10.16,2.25L9.80,2.45L9.41,2.70L8.42,3.41L4.77,6.24L3.87,6.98L3.37,7.45L3.01,7.87L2.81,8.17L2.69,8.40L2.49,8.88L2.37,9.39L2.30,9.95ZM16.00,16.25L16.15,16.26L16.29,16.31L16.42,16.38L16.53,16.47L16.67,16.67L16.72,16.81L16.75,16.95L16.74,17.10L16.71,17.24L16.65,17.38L16.56,17.49L16.46,17.60L16.33,17.67L16.19,17.72L16.05,17.75L7.95,17.75L7.71,17.69L7.58,17.62L7.47,17.53L7.38,17.42L7.31,17.29L7.25,17.05L7.26,16.90L7.29,16.76L7.35,16.62L7.44,16.51L7.54,16.40L7.67,16.33L7.81,16.28L7.95,16.25Z"
        )
    }

    val NavLibrary: ImageVector by lazy {
        solarLine("NavLibrary",
            "M2 7c0-1.4 0-2.1.272-2.635a2.5 2.5 0 0 1 1.093-1.093C3.9 3 4.6 3 6 3s2.1 0 2.635.272a2.5 2.5 0 0 1 1.093 1.093C10 4.9 10 5.6 10 7v10c0 1.4 0 2.1-.272 2.635a2.5 2.5 0 0 1-1.093 1.092C8.1 21 7.4 21 6 21s-2.1 0-2.635-.273a2.5 2.5 0 0 1-1.093-1.092C2 19.1 2 18.4 2 17z",
            "M6.125 17H6m.25 0a.25.25 0 1 1-.5 0a.25.25 0 0 1 .5 0m11.656-.307h-.125m.25 0a.25.25 0 1 1-.5 0a.25.25 0 0 1 .5 0M2 7h8",
            "M11.449 8.268c-.355-1.33-.533-1.995-.41-2.572a2.46 2.46 0 0 1 .756-1.316c.437-.395 1.1-.573 2.424-.93c1.324-.356 1.987-.534 2.561-.411a2.44 2.44 0 0 1 1.31.76c.394.438.572 1.103.927 2.433l2.534 9.5c.355 1.33.533 1.995.41 2.572a2.46 2.46 0 0 1-.756 1.316c-.437.395-1.1.573-2.424.93c-1.324.356-1.986.534-2.561.411a2.45 2.45 0 0 1-1.31-.76c-.394-.438-.572-1.103-.927-2.433zM12 8l6.5-2"
        )
    }

    val NavLibraryFilled: ImageVector by lazy {
        solarFilled("NavLibraryFilled",
            "M1.25,7.00L1.26,18.21L1.31,18.92L1.41,19.46L1.55,19.86L1.68,20.12L1.83,20.35L2.02,20.60L2.20,20.79L2.40,20.98L2.65,21.17L2.88,21.32L3.14,21.45L3.44,21.56L3.75,21.64L4.08,21.69L4.53,21.73L5.21,21.75L6.65,21.75L7.71,21.71L8.27,21.64L8.56,21.56L8.79,21.48L9.04,21.36L9.26,21.23L9.47,21.08L9.68,20.91L9.91,20.68L10.12,20.42L10.31,20.13L10.44,19.89L10.53,19.66L10.62,19.35L10.67,19.05L10.71,18.71L10.74,18.21L10.75,17.50L10.75,8.56L13.57,19.11L13.76,19.67L13.93,20.07L14.15,20.44L14.36,20.71L14.64,20.99L14.91,21.19L15.19,21.37L15.49,21.52L15.83,21.63L16.15,21.71L16.47,21.75L16.79,21.74L17.25,21.69L17.83,21.57L18.98,21.27L20.12,20.95L20.59,20.80L20.98,20.64L21.29,20.48L21.56,20.30L21.88,20.01L22.15,19.68L22.37,19.33L22.56,18.93L22.68,18.53L22.74,18.13L22.74,17.73L22.69,17.27L22.57,16.69L22.40,16.01L19.50,5.15L19.31,4.54L19.16,4.12L18.97,3.74L18.77,3.45L18.48,3.13L18.16,2.86L17.81,2.63L17.43,2.45L17.03,2.33L16.64,2.26L16.29,2.25L15.86,2.29L15.04,2.46L13.41,2.89L12.54,3.15L12.02,3.36L11.62,3.57L11.44,3.70L11.24,3.87L10.90,4.25L10.76,4.45L10.62,4.67L10.49,4.24L10.32,3.88L10.07,3.51L9.79,3.19L9.42,2.88L9.06,2.65L8.69,2.48L8.27,2.36L7.71,2.29L6.94,2.25L5.06,2.25L4.19,2.30L3.73,2.36L3.31,2.48L3.11,2.56L2.88,2.68L2.51,2.93L2.26,3.15L2.02,3.40L1.82,3.67L1.61,4.02L1.48,4.31L1.39,4.62L1.33,4.95L1.29,5.29L1.26,5.79L1.25,6.50ZM1.25,7.00L1.28,6.81L1.33,6.67L1.47,6.47L1.67,6.33L1.81,6.28L1.95,6.25L10.00,6.25L10.15,6.26L10.29,6.31L10.42,6.38L10.53,6.47L10.62,6.58L10.69,6.71L10.74,6.85L10.75,7.00L10.74,7.15L10.69,7.29L10.62,7.42L10.53,7.53L10.33,7.67L10.19,7.72L10.05,7.75L2.00,7.75L1.81,7.72L1.67,7.67L1.54,7.60L1.44,7.49L1.35,7.38L1.29,7.24L1.26,7.10ZM18.81,6.68L12.17,8.73L11.98,8.75L11.79,8.72L11.65,8.66L11.53,8.58L11.42,8.48L11.34,8.36L11.28,8.22L11.25,8.08L11.25,7.93L11.28,7.79L11.36,7.61L11.45,7.49L11.56,7.39L11.73,7.30L18.33,5.27L18.57,5.25L18.81,5.32L19.01,5.45L19.11,5.56L19.18,5.69L19.23,5.83L19.25,5.97L19.24,6.12L19.20,6.26L19.14,6.39L19.05,6.51L18.94,6.61ZM18.77,16.56L18.78,16.74L18.75,16.95L18.68,17.12L18.57,17.30L18.45,17.43L18.28,17.56L18.12,17.63L17.91,17.68L17.73,17.69L17.52,17.66L17.35,17.60L17.17,17.49L17.04,17.36L16.91,17.19L16.84,17.03L16.79,16.82L16.78,16.64L16.82,16.43L16.88,16.27L16.99,16.08L17.11,15.95L17.28,15.83L17.44,15.75L17.65,15.70L17.83,15.69L18.04,15.73L18.21,15.79L18.39,15.90L18.52,16.02L18.65,16.19L18.72,16.36ZM6.99,16.87L7.00,17.08L6.97,17.26L6.89,17.46L6.79,17.61L6.67,17.74L6.50,17.87L6.34,17.94L6.13,17.99L5.95,18.00L5.74,17.97L5.57,17.90L5.39,17.79L5.26,17.67L5.13,17.50L5.05,17.31L5.01,17.13L5.00,16.92L5.03,16.74L5.11,16.54L5.21,16.39L5.33,16.26L5.50,16.13L5.66,16.06L5.87,16.01L6.05,16.00L6.26,16.03L6.43,16.10L6.61,16.21L6.74,16.33L6.87,16.50L6.95,16.69Z"
        )
    }

    val NavBookmark: ImageVector by lazy {
        solarLine("NavBookmark",
            "M4 17.98V9.709c0-3.634 0-5.45 1.172-6.58S8.229 2 12 2s5.657 0 6.828 1.129C20 4.257 20 6.074 20 9.708v8.273c0 2.306 0 3.459-.773 3.871c-1.497.8-4.304-1.867-5.637-2.67c-.773-.465-1.16-.698-1.59-.698s-.817.233-1.59.698c-1.333.803-4.14 3.47-5.637 2.67C4 21.44 4 20.287 4 17.981"
        )
    }

    val NavBookmarkFilled: ImageVector by lazy {
        solarFilled("NavBookmarkFilled",
            "M3.25,8.42L3.26,19.29L3.29,20.17L3.33,20.57L3.38,20.91L3.52,21.41L3.60,21.62L3.74,21.88L3.84,22.02L3.98,22.19L4.15,22.33L4.39,22.50L4.79,22.66L5.06,22.72L5.38,22.75L5.70,22.73L5.98,22.69L6.29,22.61L6.59,22.51L7.18,22.25L7.76,21.93L8.34,21.57L9.13,21.02L10.28,20.17L10.80,19.82L11.60,19.36L11.81,19.27L11.98,19.23L12.13,19.25L12.35,19.34L12.63,19.49L13.20,19.82L13.72,20.17L15.13,21.21L15.94,21.75L16.50,22.08L16.79,22.23L17.41,22.51L17.96,22.67L18.26,22.73L18.62,22.75L18.89,22.73L19.25,22.65L19.57,22.52L19.77,22.40L19.99,22.21L20.16,22.02L20.32,21.78L20.42,21.57L20.52,21.29L20.65,20.73L20.72,19.95L20.74,19.29L20.75,18.26L20.75,8.84L20.73,7.29L20.69,6.28L20.63,5.67L20.55,5.08L20.44,4.55L20.28,4.05L20.08,3.58L19.82,3.14L19.51,2.75L19.14,2.40L18.73,2.11L18.51,1.98L18.09,1.79L17.56,1.61L17.04,1.49L16.18,1.36L15.22,1.29L13.74,1.26L11.09,1.25L9.49,1.27L8.13,1.33L7.24,1.44L6.70,1.54L6.40,1.62L5.95,1.77L5.71,1.87L5.23,2.14L4.86,2.40L4.67,2.57L4.33,2.94L4.15,3.19L3.92,3.58L3.72,4.05L3.62,4.33L3.50,4.84L3.41,5.36L3.34,5.96L3.28,6.94Z"
        )
    }

    /* ── Icônes Lucide (trait 2) pour tout le reste de l'appli : les formes de la navbar sont les Nav* ci-dessus. ── */
    val Home: ImageVector by lazy {
        lucide("Home", false,
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
        )
    }

    val HomeFilled: ImageVector by lazy { Home }

    val Library: ImageVector by lazy {
        lucide("Library", false,
            "m16 6 4 14",
            "M12 6v14",
            "M8 8v12",
            "M4 4v16"
        )
    }

    val LibraryFilled: ImageVector by lazy { Library }

    val Bookmark: ImageVector by lazy {
        lucide("Bookmark", false, "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z")
    }

    val BookmarkFilled: ImageVector by lazy {
        lucide("BookmarkFilled", true, "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z")
    }
}
