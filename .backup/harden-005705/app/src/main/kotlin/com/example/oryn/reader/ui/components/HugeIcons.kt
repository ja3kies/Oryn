package com.example.oryn.reader.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

object HugeIcons {
    // --- SIGNE T (NAVBAR) ---
    val Bookmark: ImageVector by lazy {
        ImageVector.Builder(name = "Bookmark", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
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

    // --- SÉLECTIONNER (CURSEUR) ---
    val Select: ImageVector by lazy {
        ImageVector.Builder(name = "Select", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12.772f, 19.605f); lineTo(10.817f, 13.987f); curveTo(10.081f, 11.875f, 9.714f, 10.818f, 10.267f, 10.267f); curveTo(10.819f, 9.714f, 11.877f, 10.081f, 13.991f, 10.817f); lineTo(19.601f, 12.767f); curveTo(20.774f, 13.174f, 21.361f, 13.377f, 21.487f, 13.772f); arcTo(0.8f, 0.8f, 0f, false, true, 21.522f, 14.11f); curveTo(21.48f, 14.522f, 20.949f, 14.843f, 19.886f, 15.485f); curveTo(19.204f, 15.895f, 18.864f, 16.102f, 18.773f, 16.412f); arcTo(0.8f, 0.8f, 0f, false, false, 18.74f, 16.682f); curveTo(18.755f, 17.006f, 19.037f, 17.286f, 19.602f, 17.847f); lineTo(21.457f, 19.691f); curveTo(21.735f, 19.967f, 21.873f, 20.105f, 21.937f, 20.26f); arcTo(0.83f, 0.83f, 0f, false, true, 21.938f, 20.89f); curveTo(21.875f, 21.045f, 21.736f, 21.184f, 21.459f, 21.46f); curveTo(21.183f, 21.737f, 21.045f, 21.875f, 20.89f, 21.938f); arcTo(0.83f, 0.83f, 0f, false, true, 20.26f, 21.938f); curveTo(20.105f, 21.875f, 19.967f, 21.737f, 19.691f, 21.461f); lineTo(17.829f, 19.6f); curveTo(17.274f, 19.045f, 16.996f, 18.768f, 16.677f, 18.75f); arcTo(0.8f, 0.8f, 0f, false, false, 16.392f, 18.786f); curveTo(16.087f, 18.879f, 15.884f, 19.215f, 15.479f, 19.886f); curveTo(14.845f, 20.938f, 14.529f, 21.463f, 14.122f, 21.509f); arcTo(0.8f, 0.8f, 0f, false, true, 13.766f, 21.472f); curveTo(13.377f, 21.343f, 13.176f, 20.764f, 12.772f, 19.605f); close()
                moveTo(2f, 8.5f); lineTo(2f, 11.5f); moveTo(11.5f, 2f); lineTo(8.5f, 2f); moveTo(11.5f, 18f); lineTo(9f, 18f); moveTo(18f, 9f); lineTo(18f, 8.5f); moveTo(4.5f, 18f); arcTo(2.5f, 2.5f, 0f, false, true, 2f, 15.5f); moveTo(2f, 4.5f); arcTo(2.5f, 2.5f, 0f, false, true, 4.5f, 2f); moveTo(18f, 4.5f); arcTo(2.5f, 2.5f, 0f, false, false, 15.5f, 2f)
            }
        }.build()
    }

    // --- SUPPRIMER (POUBELLE) ---
    val Trash: ImageVector by lazy {
        ImageVector.Builder(name = "Trash", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round) {
                moveTo(19.5f, 5.5f); lineTo(18.88f, 15.525f); curveTo(18.722f, 18.086f, 18.643f, 19.367f, 18f, 20.288f); arcTo(4f, 4f, 0f, false, true, 16.8f, 21.416f); curveTo(15.843f, 22f, 14.56f, 22f, 11.994f, 22f); curveTo(9.424f, 22f, 8.139f, 22f, 7.18f, 21.415f); arcTo(4f, 4f, 0f, false, true, 5.98f, 20.285f); curveTo(5.338f, 19.363f, 5.26f, 18.08f, 5.106f, 15.515f); lineTo(4.5f, 5.5f)
                moveTo(3f, 5.5f); lineTo(21f, 5.5f)
                moveTo(16.056f, 5.5f); lineTo(15.373f, 4.092f); curveTo(14.92f, 3.156f, 14.693f, 2.689f, 14.302f, 2.397f); arcTo(2f, 2f, 0f, false, false, 14.027f, 2.225f); curveTo(13.594f, 2f, 13.074f, 2f, 12.035f, 2f); curveTo(10.969f, 2f, 10.436f, 2f, 9.995f, 2.234f); arcTo(2f, 2f, 0f, false, false, 9.717f, 2.414f); curveTo(9.322f, 2.717f, 9.101f, 3.202f, 8.659f, 4.171f); lineTo(8.053f, 5.5f)
                moveTo(9.5f, 16.5f); lineTo(9.5f, 10.5f); moveTo(14.5f, 16.5f); lineTo(14.5f, 10.5f)
            }
        }.build()
    }
}
