package com.example.oryn.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.example.oryn.reader.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oryn.reader.ui.theme.AppAccentTheme
import com.example.oryn.reader.ui.theme.previewColor

/** Trèfle à quatre feuilles en cœur, dessiné au Canvas, en quatre nuances de [color]. */
@Composable
fun CloverGlyph(color: Color, modifier: Modifier = Modifier) {
    val tones = listOf(
        lerp(color, Color.White, 0.18f),
        color,
        lerp(color, Color.Black, 0.14f),
        lerp(color, Color.White, 0.34f)
    )
    Canvas(modifier) {
        val r = size.minDimension / 2f
        // Une feuille pointe vers le haut, sa pointe est au centre ; (0,0) = centre, unité = rayon
        val leaf = Path().apply {
            moveTo(0f, 0f)
            cubicTo(-0.50f * r, -0.10f * r, -0.72f * r, -0.62f * r, -0.40f * r, -0.88f * r)
            cubicTo(-0.20f * r, -1.02f * r, -0.02f * r, -0.92f * r, 0f, -0.75f * r)
            cubicTo(0.02f * r, -0.92f * r, 0.20f * r, -1.02f * r, 0.40f * r, -0.88f * r)
            cubicTo(0.72f * r, -0.62f * r, 0.50f * r, -0.10f * r, 0f, 0f)
            close()
        }
        tones.forEachIndexed { index, tone ->
            rotate(degrees = index * 90f, pivot = center) {
                translate(left = center.x, top = center.y) {
                    drawPath(leaf, tone)
                }
            }
        }
    }
}

/** Pastille d'accent : trèfle + libellé, coche au centre quand elle est choisie. */
@Composable
fun CloverAccentOption(
    accent: AppAccentTheme,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val cloverColor = accent.previewColor(dark)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) colors.onSurface.copy(alpha = 0.07f) else Color.Transparent)
            .then(if (selected) Modifier.border(1.5.dp, colors.onSurface, RoundedCornerShape(18.dp)) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            CloverGlyph(cloverColor, Modifier.fillMaxSize())
            if (selected) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(colors.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Check, null, tint = colors.onSurface, modifier = Modifier.size(11.dp))
                }
            }
        }
        Text(
            text = accent.label,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = colors.onSurface
        )
    }
}
