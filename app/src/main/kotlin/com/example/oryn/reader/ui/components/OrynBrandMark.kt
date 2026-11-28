// From Uiverse.io by moin_7638 - https://uiverse.io/moin_7638/stupid-grasshopper-46
// MIT License, Copyright (c) moin_7638 (Jami)
package com.example.oryn.reader.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.oryn.reader.i18n.tr
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

@Composable
fun OrynBrandMark(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val angle = if (systemAnimationsEnabled(LocalContext.current)) {
        rememberInfiniteTransition(label = "Home pattern").animateFloat(
            0f, 360f, infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "Hue"
        )
    } else null
    val label = tr("Conseil de lecture")
    Spacer(modifier.size(56.dp).semantics { contentDescription = label }
        .clickable(role = Role.Button, onClick = onClick)
        .drawWithCache {
            val unit = 4.dp.toPx()
            val ink = Path()
            // CSS background-position: 0 centers both tile layers vertically.
            val top = size.height / 2f
            val firstRow = floor(-top / unit).toInt()
            var row = firstRow
            while (top + row * unit < size.height) {
                var col = 0
                while (col * unit < size.width) {
                    val right = (col / 8) % 2 == 1
                    val lower = Math.floorMod(row, 16) < 8
                    val fineInk = (col + row) % 2 == 0
                    if ((right && lower) || (right != lower && fineInk)) {
                        ink.addRect(Rect(col * unit, top + row * unit,
                            (col + 1) * unit, top + (row + 1) * unit))
                    }
                    col++
                }
                row++
            }
            onDrawBehind {
                drawRect(Color.Black)
                drawPath(ink, hueRotatedPurple(angle?.value ?: 0f))
            }
        })
}

// CSS Filter Effects hue-rotate matrix applied to hsl(270, 60%, 65%).
private fun hueRotatedPurple(degrees: Float): Color {
    val radians = Math.toRadians(degrees.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    val r = .65f
    val g = .44f
    val b = .86f
    val y = .213f * r + .715f * g + .072f * b
    return Color(
        (y + c * (r - y) + s * (b - y)).coerceIn(0f, 1f),
        (y + c * (g - y) + s * (.143f * r + .140f * g - .283f * b)).coerceIn(0f, 1f),
        (y + c * (b - y) + s * (y - r)).coerceIn(0f, 1f)
    )
}
