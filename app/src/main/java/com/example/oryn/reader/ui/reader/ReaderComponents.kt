package com.example.oryn.reader.ui.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/* ───────────────────────── Accents de couleur (thème de l'app) ───────────────────────── */

internal data class Accent(val base: Color, val container: Color, val onContainer: Color)

@Composable
internal fun primaryAccent() = MaterialTheme.colorScheme.let { Accent(it.primary, it.primaryContainer, it.onPrimaryContainer) }

@Composable
internal fun secondaryAccent() = MaterialTheme.colorScheme.let { Accent(it.secondary, it.secondaryContainer, it.onSecondaryContainer) }

@Composable
internal fun tertiaryAccent() = MaterialTheme.colorScheme.let { Accent(it.tertiary, it.tertiaryContainer, it.onTertiaryContainer) }

/* ───────────────────────── Cards visuelles ───────────────────────── */

@Composable
internal fun ThemeCard(theme: ReaderTheme, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (selected) 1f else 0.94f, label = "themeScale")
    val shape = RoundedCornerShape(22.dp)

    Column(
        Modifier.width(92.dp).scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(92.dp, 104.dp)
                .clip(shape)
                .border(
                    if (selected) 2.5.dp else 1.dp,
                    if (selected) cs.primary else cs.outlineVariant.copy(alpha = 0.6f),
                    shape
                )
                .clickable(onClick = onClick)
        ) {
            if (theme == ReaderTheme.Auto) {
                val l = ReaderTheme.Light.palette(false)
                val d = ReaderTheme.Dark.palette(true)
                Row(Modifier.matchParentSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(l.bg), contentAlignment = Alignment.CenterEnd) {
                        Text("A", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = l.fg)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().background(d.bg), contentAlignment = Alignment.CenterStart) {
                        Text("a", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = d.fg)
                    }
                }
            } else {
                val p = theme.palette(false)
                Box(Modifier.matchParentSize().background(p.bg))
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Aa", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = p.fg)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(46.dp).height(5.dp).clip(CircleShape).background(p.accent))
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.width(38.dp).height(5.dp).clip(CircleShape).background(p.fg.copy(alpha = 0.3f)))
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.width(28.dp).height(5.dp).clip(CircleShape).background(p.fg.copy(alpha = 0.3f)))
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(cs.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = cs.onPrimary)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            theme.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) cs.primary else cs.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
internal fun FontCard(font: ReaderFont, accent: Color, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (selected) 1f else 0.96f, label = "fontScale")
    val shape = RoundedCornerShape(20.dp)

    Box(
        Modifier
            .width(96.dp)
            .scale(scale)
            .clip(shape)
            .background(accent.copy(alpha = if (selected) 0.24f else 0.12f))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) accent else accent.copy(alpha = 0.25f),
                shape
            )
            .clickable(onClick = onClick)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Aa", fontFamily = font.family(), fontSize = 32.sp, color = accent)
            Spacer(Modifier.height(6.dp))
            Text(
                font.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = cs.onSurface,
                maxLines = 1
            )
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = cs.surface)
            }
        }
    }
}

/* ───────────────────────── Briques communes ───────────────────────── */

@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val bg: Brush = if (tint != null) {
        Brush.horizontalGradient(listOf(tint.copy(alpha = 0.22f), cs.surfaceContainerHigh.copy(alpha = 0.55f)))
    } else {
        SolidColor(cs.surfaceContainerHigh.copy(alpha = 0.6f))
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, tint?.copy(alpha = 0.30f) ?: cs.outlineVariant.copy(alpha = 0.35f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content
    )
}

@Composable
internal fun IconBadge(
    icon: ImageVector,
    size: Dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    brush: Brush? = null
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(container.copy(alpha = 0.85f))),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = content)
    }
}

@Composable
internal fun GlassIconButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    container: Color? = null,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(container ?: cs.surfaceContainerHigh.copy(alpha = 0.65f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(size * 0.5f), tint = tint ?: cs.onSurface)
    }
}

@Composable
internal fun StepperCard(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    accent: Accent,
    onChange: (Float) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    GlassCard(tint = accent.base) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, 44.dp, container = accent.container, content = accent.onContainer)
                Text(
                    title,
                    Modifier.weight(1f).padding(horizontal = 14.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(accent.container)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(valueLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = accent.onContainer)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.Remove, "Diminuer", 36.dp, container = accent.container.copy(alpha = 0.7f), tint = accent.onContainer) {
                    onChange((value - step).coerceIn(range))
                }
                Slider(
                    value = value,
                    onValueChange = { onChange(((it / step).roundToInt() * step).coerceIn(range)) },
                    valueRange = range,
                    colors = SliderDefaults.colors(
                        thumbColor = accent.base,
                        activeTrackColor = accent.base,
                        inactiveTrackColor = accent.container.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                )
                GlassIconButton(Icons.Rounded.Add, "Augmenter", 36.dp, container = accent.container.copy(alpha = 0.7f), tint = accent.onContainer) {
                    onChange((value + step).coerceIn(range))
                }
            }
        }
    }
}

@Composable
internal fun <T> GlassSegmented(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    icon: (T) -> ImageVector,
    filled: Boolean,
    onSelect: (T) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh.copy(alpha = 0.6f))
            .padding(4.dp)
    ) {
        items.forEach { item ->
            val sel = item == selected
            val fg = when {
                sel && filled -> cs.onPrimary
                sel -> cs.onPrimaryContainer
                else -> cs.onSurfaceVariant
            }
            var m = Modifier.weight(1f).clip(CircleShape)
            if (sel) {
                m = if (filled) {
                    m.background(Brush.horizontalGradient(listOf(cs.primary, cs.tertiary)))
                } else {
                    m.background(cs.primaryContainer).border(1.dp, cs.primary.copy(alpha = 0.35f), CircleShape)
                }
            }
            Row(
                m.clickable { onSelect(item) }.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon(item), null, Modifier.size(18.dp), tint = fg)
                Spacer(Modifier.width(8.dp))
                Text(
                    label(item),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                    color = fg,
                    maxLines = 1
                )
            }
        }
    }
}
