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

    val Bookmark: ImageVector by lazy {
        lucide("Bookmark", false,
            "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z"
        )
    }

    val BookmarkFilled: ImageVector by lazy {
        lucide("BookmarkFilled", true,
            "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z"
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

    val Home: ImageVector by lazy {
        lucide("Home", false,
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
        )
    }

    /** Maison pleine : la porte reste un vide (remplissage pair/impair), sinon le pictogramme devient un bloc. */
    val HomeFilled: ImageVector by lazy {
        ImageVector.Builder("HomeFilled", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" +
                        "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8z"
                ).toNodes(),
                pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }.build()
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

    val Library: ImageVector by lazy {
        lucide("Library", false,
            "m16 6 4 14",
            "M12 6v14",
            "M8 8v12",
            "M4 4v16"
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

}
