package com.example.oryn.reader.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.addPathNodes

object OrynIcons {
    val Menu: ImageVector by lazy {
        ImageVector.Builder(name = "Menu", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(3f, 9f); lineTo(21f, 9f); moveTo(3f, 15f); lineTo(21f, 15f)
            }
        }.build()
    }
    val Sliders: ImageVector by lazy {
        ImageVector.Builder(name = "Sliders", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 18f); lineTo(20f, 18f)
                moveTo(8f, 4f); lineTo(8f, 8f); moveTo(16f, 10f); lineTo(16f, 14f); moveTo(10f, 16f); lineTo(10f, 20f)
            }
        }.build()
    }
    val Plus: ImageVector by lazy {
        ImageVector.Builder(name = "Plus", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f)
            }
        }.build()
    }
    val Globe: ImageVector by lazy {
        ImageVector.Builder(name = "Globe", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
                moveTo(12f, 21f); curveTo(16.9706f, 21f, 21f, 16.9706f, 21f, 12f); curveTo(21f, 7.0294f, 16.9706f, 3f, 12f, 3f); curveTo(7.0294f, 3f, 3f, 7.0294f, 3f, 12f); curveTo(3f, 16.9706f, 7.0294f, 21f, 12f, 21f); close()
                moveTo(3.6f, 9f); lineTo(20.4f, 9f); moveTo(3.6f, 15f); lineTo(20.4f, 15f)
                moveTo(12f, 3f); curveTo(14.5f, 5f, 15.5f, 8f, 15.5f, 12f); curveTo(15.5f, 16f, 14.5f, 19f, 12f, 21f); curveTo(9.5f, 19f, 8.5f, 16f, 8.5f, 12f); curveTo(8.5f, 8f, 9.5f, 5f, 12f, 3f); close()
            }
        }.build()
    }
    val Bookmark: ImageVector by lazy {
        ImageVector.Builder(name = "Bookmark", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
                moveTo(5f, 7f); curveTo(5f, 5.1144f, 5f, 4.1716f, 5.5858f, 3.5858f); curveTo(6.1716f, 3f, 7.1144f, 3f, 9f, 3f); lineTo(15f, 3f); curveTo(16.8856f, 3f, 17.8284f, 3f, 18.4142f, 3.5858f); curveTo(19f, 4.1716f, 19f, 5.1144f, 19f, 7f); lineTo(19f, 21f); lineTo(12f, 16f); lineTo(5f, 21f); close()
            }
        }.build()
    }
    val BookmarkFilled: ImageVector by lazy {
        ImageVector.Builder(name = "BookmarkFilled", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 7f); curveTo(5f, 5.1144f, 5f, 4.1716f, 5.5858f, 3.5858f); curveTo(6.1716f, 3f, 7.1144f, 3f, 9f, 3f); lineTo(15f, 3f); curveTo(16.8856f, 3f, 17.8284f, 3f, 18.4142f, 3.5858f); curveTo(19f, 4.1716f, 19f, 5.1144f, 19f, 7f); lineTo(19f, 21f); lineTo(12f, 16f); lineTo(5f, 21f); close()
            }
        }.build()
    }
    val Check: ImageVector by lazy {
        ImageVector.Builder(name = "Check", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round) {
                moveTo(4f, 12f); lineTo(9f, 17f); lineTo(20f, 6f)
            }
        }.build()
    }
    val BookOpen: ImageVector by lazy {
        ImageVector.Builder(name = "BookOpen", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round) {
                moveTo(12f, 7f); curveTo(12f, 7f, 10f, 5f, 6f, 5f); curveTo(2f, 5f, 2f, 7f, 2f, 7f); lineTo(2f, 19f); curveTo(2f, 19f, 2f, 17f, 6f, 17f); curveTo(10f, 17f, 12f, 19f, 12f, 19f)
                moveTo(12f, 7f); curveTo(12f, 7f, 14f, 5f, 18f, 5f); curveTo(22f, 5f, 22f, 7f, 22f, 7f); lineTo(22f, 19f); curveTo(22f, 19f, 22f, 17f, 18f, 17f); curveTo(14f, 17f, 12f, 19f, 12f, 19f)
                moveTo(12f, 7f); lineTo(12f, 19f)
            }
        }.build()
    }
    val Chevron: ImageVector by lazy {
        ImageVector.Builder(name = "Chevron", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round) {
                moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
            }
        }.build()
    }
    val Play: ImageVector by lazy {
        ImageVector.Builder(name = "Play", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 4f); lineTo(19f, 12f); lineTo(7f, 20f); close()
            }
        }.build()
    }
    val Info: ImageVector by lazy {
        ImageVector.Builder(name = "Info", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
                moveTo(12f, 21f); curveTo(16.9706f, 21f, 21f, 16.9706f, 21f, 12f); curveTo(21f, 7.0294f, 16.9706f, 3f, 12f, 3f); curveTo(7.0294f, 3f, 3f, 7.0294f, 3f, 12f); curveTo(3f, 16.9706f, 7.0294f, 21f, 12f, 21f); close()
                moveTo(12f, 11f); lineTo(12f, 16f)
                moveTo(12f, 8f); lineTo(12.01f, 8f)
            }
        }.build()
    }
    val Trash: ImageVector by lazy {
        ImageVector.Builder(name = "Trash", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(19.5f, 5.5f); lineTo(18.88f, 15.525f); curveTo(18.722f, 18.086f, 18.643f, 19.367f, 18f, 20.288f); arcTo(4f, 4f, 0f, false, true, 16.8f, 21.416f); curveTo(15.843f, 22f, 14.56f, 22f, 11.994f, 22f); curveTo(9.424f, 22f, 8.139f, 22f, 7.18f, 21.415f); arcTo(4f, 4f, 0f, false, true, 5.98f, 20.285f); curveTo(5.338f, 19.363f, 5.26f, 18.08f, 5.106f, 15.515f); lineTo(4.5f, 5.5f)
                moveTo(3f, 5.5f); lineTo(21f, 5.5f)
                moveTo(16.056f, 5.5f); lineTo(15.373f, 4.092f); curveTo(14.92f, 3.156f, 14.693f, 2.689f, 14.302f, 2.397f); arcTo(2f, 2f, 0f, false, false, 14.027f, 2.225f); curveTo(13.594f, 2f, 13.074f, 2f, 12.035f, 2f); curveTo(10.969f, 2f, 10.436f, 2f, 9.995f, 2.234f); arcTo(2f, 2f, 0f, false, false, 9.717f, 2.414f); curveTo(9.322f, 2.717f, 9.101f, 3.202f, 8.659f, 4.171f); lineTo(8.053f, 5.5f)
                moveTo(9.5f, 16.5f); lineTo(9.5f, 10.5f); moveTo(14.5f, 16.5f); lineTo(14.5f, 10.5f)
            }
        }.build()
    }
    val Close: ImageVector by lazy {
        ImageVector.Builder(name = "Close", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(18f, 6f); lineTo(6f, 18f); moveTo(6f, 6f); lineTo(18f, 18f)
            }
        }.build()
    }
    val Edit: ImageVector by lazy {
        ImageVector.Builder(name = "Edit", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round) {
                moveTo(16.5f, 3.5f); curveTo(17.3284f, 2.6716f, 18.6716f, 2.6716f, 19.5f, 3.5f); lineTo(20.5f, 4.5f); curveTo(21.3284f, 5.3284f, 21.3284f, 6.6716f, 20.5f, 7.5f); lineTo(8f, 20f); lineTo(3f, 21f); lineTo(4f, 16f); close()
                moveTo(15f, 5f); lineTo(19f, 9f)
            }
        }.build()
    }
    val Star: ImageVector by lazy {
        ImageVector.Builder(name = "Star", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3.5f); lineTo(14.5f, 9f); lineTo(20.5f, 9.8f); lineTo(16f, 14f); lineTo(17.2f, 20f); lineTo(12f, 17f); lineTo(6.8f, 20f); lineTo(8f, 14f); lineTo(3.5f, 9.8f); lineTo(9.5f, 9f); close()
            }
        }.build()
    }
    val Image: ImageVector by lazy {
        ImageVector.Builder(name = "Image", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
                moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close()
                moveTo(4f, 15f); lineTo(9f, 10f); lineTo(14f, 15f); lineTo(17f, 12f); lineTo(20f, 15f)
                moveTo(9f, 8f); lineTo(9.01f, 8f)
            }
        }.build()
    }
    val Grid: ImageVector by lazy {
        ImageVector.Builder(name = "Grid", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
                moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
                moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
                moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
                moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
            }
        }.build()
    }
    val List: ImageVector by lazy {
        ImageVector.Builder(name = "List", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(8f, 6f); lineTo(21f, 6f); moveTo(8f, 12f); lineTo(21f, 12f); moveTo(8f, 18f); lineTo(21f, 18f)
                moveTo(3f, 6f); lineTo(3.01f, 6f); moveTo(3f, 12f); lineTo(3.01f, 12f); moveTo(3f, 18f); lineTo(3.01f, 18f)
            }
        }.build()
    }
    val Search: ImageVector by lazy {
        ImageVector.Builder(name = "Search", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(10.5f, 3f); curveTo(14.642f, 3f, 18f, 6.358f, 18f, 10.5f); curveTo(18f, 14.642f, 14.642f, 18f, 10.5f, 18f); curveTo(6.358f, 18f, 3f, 14.642f, 3f, 10.5f); curveTo(3f, 6.358f, 6.358f, 3f, 10.5f, 3f); close()
                moveTo(16f, 16f); lineTo(21f, 21f)
            }
        }.build()
    }
    val Sort: ImageVector by lazy {
        ImageVector.Builder(name = "Sort", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(4f, 6f); lineTo(16f, 6f); moveTo(4f, 12f); lineTo(12f, 12f); moveTo(4f, 18f); lineTo(8f, 18f)
                moveTo(15f, 15f); lineTo(18f, 18f); lineTo(21f, 15f); moveTo(18f, 18f); lineTo(18f, 10f)
            }
        }.build()
    }
    val AlignCenter: ImageVector by lazy {
        ImageVector.Builder(name = "AlignCenter", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(4f, 6f); lineTo(20f, 6f); moveTo(7f, 12f); lineTo(17f, 12f); moveTo(4f, 18f); lineTo(20f, 18f)
            }
        }.build()
    }
    val AlignJustify: ImageVector by lazy {
        ImageVector.Builder(name = "AlignJustify", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 18f); lineTo(20f, 18f)
            }
        }.build()
    }
    val AlignLeft: ImageVector by lazy {
        ImageVector.Builder(name = "AlignLeft", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 12f); lineTo(14f, 12f); moveTo(4f, 18f); lineTo(20f, 18f)
            }
        }.build()
    }
    val ArrowLeft: ImageVector by lazy {
        ImageVector.Builder(name = "ArrowLeft", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round) {
                moveTo(19f, 12f); lineTo(5f, 12f); moveTo(12f, 5f); lineTo(5f, 12f); lineTo(12f, 19f)
            }
        }.build()
    }
    val AutoTheme: ImageVector by lazy {
        ImageVector.Builder(name = "AutoTheme", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(12f, 3f); curveTo(16.9706f, 3f, 21f, 7.0294f, 21f, 12f); curveTo(21f, 16.9706f, 16.9706f, 21f, 12f, 21f); curveTo(7.0294f, 21f, 3f, 16.9706f, 3f, 12f); curveTo(3f, 7.0294f, 7.0294f, 3f, 12f, 3f); close()
                moveTo(12f, 3f); lineTo(12f, 21f)
            }
        }.build()
    }
    val Compass: ImageVector by lazy {
        ImageVector.Builder(name = "Compass", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
                moveTo(12f, 21f); curveTo(16.9706f, 21f, 21f, 16.9706f, 21f, 12f); curveTo(21f, 7.0294f, 16.9706f, 3f, 12f, 3f); curveTo(7.0294f, 3f, 3f, 7.0294f, 3f, 12f); curveTo(3f, 16.9706f, 7.0294f, 21f, 12f, 21f); close()
                moveTo(16.2f, 7.8f); lineTo(14f, 14f); lineTo(7.8f, 16.2f); lineTo(10f, 10f); close()
            }
        }.build()
    }
    val Download: ImageVector by lazy {
        ImageVector.Builder(name = "Download", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round) {
                moveTo(12f, 3f); lineTo(12f, 15f); moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
                moveTo(4f, 21f); lineTo(20f, 21f)
            }
        }.build()
    }
    val External: ImageVector by lazy {
        ImageVector.Builder(name = "External", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f)
                moveTo(20f, 4f); lineTo(10f, 14f)
                moveTo(18f, 14f); lineTo(18f, 20f); lineTo(4f, 20f); lineTo(4f, 6f); lineTo(10f, 6f)
            }
        }.build()
    }
    val EyeOff: ImageVector by lazy {
        ImageVector.Builder(name = "EyeOff", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(3f, 3f); lineTo(21f, 21f)
                moveTo(10.5f, 6f); curveTo(11f, 5.9f, 11.5f, 5.9f, 12f, 6f); curveTo(16.5f, 6f, 20f, 12f, 20f, 12f); curveTo(19.1f, 13.4f, 18f, 14.6f, 16.8f, 15.6f)
                moveTo(6.3f, 7.8f); curveTo(4.5f, 9.3f, 3.1f, 11.1f, 4f, 12f); curveTo(4f, 12f, 7.5f, 18f, 12f, 18f); curveTo(13.3f, 18f, 14.6f, 17.5f, 15.6f, 16.8f)
                moveTo(9.9f, 9.9f); curveTo(9.4f, 10.4f, 9f, 11.2f, 9f, 12f); curveTo(9f, 13.7f, 10.3f, 15f, 12f, 15f); curveTo(12.8f, 15f, 13.6f, 14.6f, 14.1f, 14.1f)
            }
        }.build()
    }
    val Font: ImageVector by lazy {
        ImageVector.Builder(name = "Font", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(6f, 20f); lineTo(12f, 4f); lineTo(18f, 20f); moveTo(8f, 14f); lineTo(16f, 14f)
            }
        }.build()
    }
    val Highlighter: ImageVector by lazy {
        ImageVector.Builder(name = "Highlighter", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round) {
                moveTo(14.5f, 4.5f); lineTo(19.5f, 9.5f); lineTo(10f, 19f); lineTo(4f, 20f); lineTo(5f, 14f); close()
                moveTo(4f, 20f); lineTo(20f, 20f)
            }
        }.build()
    }
    val Home: ImageVector by lazy {
        ImageVector.Builder(name = "Home", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round) {
                moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
                moveTo(5f, 8.5f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 8.5f)
                moveTo(9f, 21f); lineTo(9f, 14f); lineTo(15f, 14f); lineTo(15f, 21f)
            }
        }.build()
    }
    val HomeFilled: ImageVector by lazy {
        ImageVector.Builder(name = "HomeFilled", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f); lineTo(19f, 8.5f); lineTo(19f, 21f); lineTo(5f, 21f); lineTo(5f, 8.5f); close()
            }
        }.build()
    }
    val Library: ImageVector by lazy {
        ImageVector.Builder(name = "Library", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round) {
                moveTo(4f, 4f); lineTo(8f, 4f); lineTo(8f, 20f); lineTo(4f, 20f); close()
                moveTo(10f, 4f); lineTo(14f, 4f); lineTo(14f, 20f); lineTo(10f, 20f); close()
                moveTo(16f, 6f); lineTo(20f, 5f); lineTo(22f, 19f); lineTo(18f, 20f); close()
            }
        }.build()
    }
    val LibraryFilled: ImageVector by lazy {
        ImageVector.Builder(name = "LibraryFilled", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 4f); lineTo(8f, 4f); lineTo(8f, 20f); lineTo(4f, 20f); close()
                moveTo(10f, 4f); lineTo(14f, 4f); lineTo(14f, 20f); lineTo(10f, 20f); close()
                moveTo(16f, 6f); lineTo(20f, 5f); lineTo(22f, 19f); lineTo(18f, 20f); close()
            }
        }.build()
    }
    val LineSpacing: ImageVector by lazy {
        ImageVector.Builder(name = "LineSpacing", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(10f, 6f); lineTo(20f, 6f); moveTo(10f, 12f); lineTo(20f, 12f); moveTo(10f, 18f); lineTo(20f, 18f)
                moveTo(5f, 4f); lineTo(5f, 20f); moveTo(3f, 6f); lineTo(5f, 4f); lineTo(7f, 6f); moveTo(3f, 18f); lineTo(5f, 20f); lineTo(7f, 18f)
            }
        }.build()
    }
    val MarginNarrow: ImageVector by lazy {
        ImageVector.Builder(name = "MarginNarrow", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(8f, 6f); lineTo(20f, 6f); moveTo(12f, 12f); lineTo(20f, 12f); moveTo(8f, 18f); lineTo(20f, 18f)
                moveTo(4f, 6f); lineTo(4f, 18f); moveTo(2f, 8f); lineTo(4f, 6f); lineTo(6f, 8f)
            }
        }.build()
    }
    val MarginNormal: ImageVector by lazy {
        ImageVector.Builder(name = "MarginNormal", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(8f, 6f); lineTo(20f, 6f); moveTo(8f, 12f); lineTo(20f, 12f); moveTo(8f, 18f); lineTo(20f, 18f)
                moveTo(4f, 6f); lineTo(4f, 18f)
            }
        }.build()
    }
    val MarginWide: ImageVector by lazy {
        ImageVector.Builder(name = "MarginWide", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(12f, 6f); lineTo(20f, 6f); moveTo(8f, 12f); lineTo(20f, 12f); moveTo(12f, 18f); lineTo(20f, 18f)
                moveTo(4f, 6f); lineTo(4f, 18f); moveTo(2f, 8f); lineTo(4f, 6f); lineTo(6f, 8f); moveTo(2f, 16f); lineTo(4f, 18f); lineTo(6f, 16f)
            }
        }.build()
    }
    val Minus: ImageVector by lazy {
        ImageVector.Builder(name = "Minus", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(5f, 12f); lineTo(19f, 12f)
            }
        }.build()
    }
    val Moon: ImageVector by lazy {
        ImageVector.Builder(name = "Moon", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round) {
                moveTo(21f, 12.79f); curveTo(20.3f, 12.92f, 19.6f, 13f, 18.8f, 13f); curveTo(13.9f, 13f, 9.9f, 9f, 9.9f, 4.1f); curveTo(9.9f, 3.3f, 10f, 2.6f, 10.2f, 1.9f); curveTo(6.1f, 2.9f, 3f, 6.6f, 3f, 11f); curveTo(3f, 16.2f, 7.2f, 20.4f, 12.4f, 20.4f); curveTo(16.6f, 20.4f, 20.2f, 17.6f, 21f, 12.79f); close()
            }
        }.build()
    }
    val Pages: ImageVector by lazy {
        ImageVector.Builder(name = "Pages", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round) {
                moveTo(14f, 3f); lineTo(6f, 3f); curveTo(5f, 3f, 4f, 3f, 4f, 4f); lineTo(4f, 20f); curveTo(4f, 21f, 5f, 21f, 6f, 21f); lineTo(18f, 21f); curveTo(19f, 21f, 20f, 21f, 20f, 20f); lineTo(20f, 9f); lineTo(14f, 3f); close()
                moveTo(14f, 3f); lineTo(14f, 9f); lineTo(20f, 9f)
                moveTo(8f, 13f); lineTo(16f, 13f); moveTo(8f, 17f); lineTo(16f, 17f)
            }
        }.build()
    }
    val Palette: ImageVector by lazy {
        ImageVector.Builder(name = "Palette", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
                moveTo(12f, 21f); curveTo(16.9706f, 21f, 21f, 16.9706f, 21f, 12f); curveTo(21f, 7.0294f, 16.9706f, 3f, 12f, 3f); curveTo(7.0294f, 3f, 3f, 7.0294f, 3f, 12f); curveTo(3f, 16.9706f, 7.0294f, 21f, 12f, 21f); close()
                moveTo(8f, 9f); lineTo(8.01f, 9f); moveTo(12f, 6f); lineTo(12.01f, 6f); moveTo(16f, 9f); lineTo(16.01f, 9f); moveTo(18f, 14f); lineTo(18.01f, 14f); moveTo(6f, 14f); lineTo(6.01f, 14f)
            }
        }.build()
    }
    val Refresh: ImageVector by lazy {
        ImageVector.Builder(name = "Refresh", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round) {
                moveTo(20f, 12f); curveTo(20f, 7.58f, 16.42f, 4f, 12f, 4f); curveTo(8.5f, 4f, 5.6f, 6f, 4.4f, 8.8f)
                moveTo(4f, 4f); lineTo(4f, 9f); lineTo(9f, 9f)
                moveTo(4f, 12f); curveTo(4f, 16.42f, 7.58f, 20f, 12f, 20f); curveTo(15.5f, 20f, 18.4f, 18f, 19.6f, 15.2f)
                moveTo(20f, 20f); lineTo(20f, 15f); lineTo(15f, 15f)
            }
        }.build()
    }
    val ScrollVertical: ImageVector by lazy {
        ImageVector.Builder(name = "ScrollVertical", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(9f, 18f); lineTo(15f, 18f); moveTo(8f, 15f); lineTo(16f, 15f); moveTo(7f, 12f); lineTo(17f, 12f); moveTo(8f, 9f); lineTo(16f, 9f); moveTo(9f, 6f); lineTo(15f, 6f)
                moveTo(12f, 2f); lineTo(12f, 4f); moveTo(12f, 20f); lineTo(12f, 22f)
            }
        }.build()
    }
    val Sparkle: ImageVector by lazy {
        ImageVector.Builder(name = "Sparkle", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
                moveTo(12f, 3f); lineTo(14f, 10f); lineTo(21f, 12f); lineTo(14f, 14f); lineTo(12f, 21f); lineTo(10f, 14f); lineTo(3f, 12f); lineTo(10f, 10f); close()
            }
        }.build()
    }
    val Sun: ImageVector by lazy {
        ImageVector.Builder(name = "Sun", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(12f, 16f); curveTo(14.2091f, 16f, 16f, 14.2091f, 16f, 12f); curveTo(16f, 9.7909f, 14.2091f, 8f, 12f, 8f); curveTo(9.7909f, 8f, 8f, 9.7909f, 8f, 12f); curveTo(8f, 14.2091f, 9.7909f, 16f, 12f, 16f); close()
                moveTo(12f, 2f); lineTo(12f, 4f); moveTo(12f, 20f); lineTo(12f, 22f); moveTo(4.9f, 4.9f); lineTo(6.3f, 6.3f); moveTo(17.7f, 17.7f); lineTo(19.1f, 19.1f); moveTo(2f, 12f); lineTo(4f, 12f); moveTo(20f, 12f); lineTo(22f, 12f); moveTo(4.9f, 19.1f); lineTo(6.3f, 17.7f); moveTo(17.7f, 6.3f); lineTo(19.1f, 4.9f)
            }
        }.build()
    }
    val SunAuto: ImageVector by lazy {
        ImageVector.Builder(name = "SunAuto", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(12f, 3f); lineTo(12f, 5f); moveTo(12f, 19f); lineTo(12f, 21f); moveTo(5f, 5f); lineTo(6.5f, 6.5f); moveTo(17.5f, 17.5f); lineTo(19f, 19f); moveTo(3f, 12f); lineTo(5f, 12f); moveTo(19f, 12f); lineTo(21f, 12f); moveTo(5f, 19f); lineTo(6.5f, 17.5f); moveTo(17.5f, 6.5f); lineTo(19f, 5f)
                moveTo(12f, 8f); curveTo(14.2f, 8f, 16f, 9.8f, 16f, 12f); curveTo(16f, 14.2f, 14.2f, 16f, 12f, 16f); curveTo(9.8f, 16f, 8f, 14.2f, 8f, 12f); curveTo(8f, 9.8f, 9.8f, 8f, 12f, 8f); close()
            }
        }.build()
    }
    val TextSize: ImageVector by lazy {
        ImageVector.Builder(name = "TextSize", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(4f, 18f); lineTo(10f, 6f); lineTo(16f, 18f); moveTo(6f, 14f); lineTo(14f, 14f)
                moveTo(16f, 18f); lineTo(18f, 14f); lineTo(20f, 18f); moveTo(17f, 16.5f); lineTo(19f, 16.5f)
            }
        }.build()
    }
    val Toc: ImageVector by lazy {
        ImageVector.Builder(name = "Toc", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round) {
                moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 12f); lineTo(14f, 12f); moveTo(4f, 18f); lineTo(16f, 18f)
            }
        }.build()
    }
    val Select: ImageVector by lazy {
        ImageVector.Builder(name = "Select", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round) {
                moveTo(12.772f, 19.605f); lineTo(10.817f, 13.987f); curveTo(10.081f, 11.875f, 9.714f, 10.818f, 10.267f, 10.267f); curveTo(10.819f, 9.714f, 11.877f, 10.081f, 13.991f, 10.817f); lineTo(19.601f, 12.767f); curveTo(20.774f, 13.174f, 21.361f, 13.377f, 21.487f, 13.772f); arcTo(0.8f, 0.8f, 0f, false, true, 21.522f, 14.11f); curveTo(21.48f, 14.522f, 20.949f, 14.843f, 19.886f, 15.485f); curveTo(19.204f, 15.895f, 18.864f, 16.102f, 18.773f, 16.412f); arcTo(0.8f, 0.8f, 0f, false, false, 18.74f, 16.682f); curveTo(18.755f, 17.006f, 19.037f, 17.286f, 19.602f, 17.847f); lineTo(21.457f, 19.691f); curveTo(21.735f, 19.967f, 21.873f, 20.105f, 21.937f, 20.26f); arcTo(0.83f, 0.83f, 0f, false, true, 21.938f, 20.89f); curveTo(21.875f, 21.045f, 21.736f, 21.184f, 21.459f, 21.46f); curveTo(21.183f, 21.737f, 21.045f, 21.875f, 20.89f, 21.938f); arcTo(0.83f, 0.83f, 0f, false, true, 20.26f, 21.938f); curveTo(20.105f, 21.875f, 19.967f, 21.737f, 19.691f, 21.461f); lineTo(17.829f, 19.6f); curveTo(17.274f, 19.045f, 16.996f, 18.768f, 16.677f, 18.75f); arcTo(0.8f, 0.8f, 0f, false, false, 16.392f, 18.786f); curveTo(16.087f, 18.879f, 15.884f, 19.215f, 15.479f, 19.886f); curveTo(14.845f, 20.938f, 14.529f, 21.463f, 14.122f, 21.509f); arcTo(0.8f, 0.8f, 0f, false, true, 13.766f, 21.472f); curveTo(13.377f, 21.343f, 13.176f, 20.764f, 12.772f, 19.605f); close()
                moveTo(2f, 8.5f); lineTo(2f, 11.5f); moveTo(11.5f, 2f); lineTo(8.5f, 2f); moveTo(11.5f, 18f); lineTo(9f, 18f); moveTo(18f, 9f); lineTo(18f, 8.5f); moveTo(4.5f, 18f); arcTo(2.5f, 2.5f, 0f, false, true, 2f, 15.5f); moveTo(2f, 4.5f); arcTo(2.5f, 2.5f, 0f, false, true, 4.5f, 2f); moveTo(18f, 4.5f); arcTo(2.5f, 2.5f, 0f, false, false, 15.5f, 2f)
            }
        }.build()
    }
}
