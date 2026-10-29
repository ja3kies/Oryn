package com.example.oryn.reader.ui.components

import com.example.oryn.reader.i18n.tr
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.example.oryn.reader.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.oryn.reader.data.local.entity.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Verre dépoli / « liquid glass », lisible en clair comme en sombre :
 *  - fond translucide en dégradé (blanc en clair, voile sombre + blanc en sombre) pour garantir le contraste du texte ;
 *  - teinte de la couverture ;
 *  - reflet spéculaire en haut à gauche ;
 *  - liseré lumineux aux deux coins opposés + filet fin qui détache la carte du fond.
 * [frosted] rend la carte presque opaque (fenêtres, menus) pour les surfaces posées sur un fond assombri.
 */
@Composable
fun Modifier.glass(shape: Shape, tint: Color? = null, frosted: Boolean = false): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f

    val veil = if (dark) scheme.background.copy(alpha = 0.32f) else Color.Transparent
    val fill = Brush.verticalGradient(
        if (dark) listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.06f))
        else listOf(Color.White.copy(alpha = 0.80f), Color.White.copy(alpha = 0.58f))
    )
    val rim = Brush.linearGradient(
        0.00f to Color.White.copy(alpha = if (dark) 0.55f else 1.00f),
        0.35f to Color.White.copy(alpha = if (dark) 0.10f else 0.25f),
        0.65f to Color.White.copy(alpha = if (dark) 0.08f else 0.20f),
        1.00f to Color.White.copy(alpha = if (dark) 0.40f else 0.90f)
    )
    val hairline = scheme.outlineVariant.copy(alpha = if (dark) 0.55f else 0.75f)
    val shine = Color.White.copy(alpha = if (dark) 0.14f else 0.60f)
    val tintAlpha = if (dark) 0.20f else 0.10f

    return this
        .clip(shape)
        .then(if (frosted) Modifier.background(scheme.surface.copy(alpha = if (dark) 0.90f else 0.94f)) else Modifier)
        .background(veil)
        .background(fill)
        .then(if (tint != null) Modifier.background(tint.copy(alpha = tintAlpha)) else Modifier)
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(shine, Color.Transparent),
                    center = Offset(size.width * 0.10f, 0f),
                    radius = size.width * 0.95f
                )
            )
        }
        .border(0.6.dp, hairline, shape)
        .border(1.2.dp, rim, shape)
}

/** Texte principal / secondaire : couleurs pleines (pas de transparence) pour un contraste AA sur le verre et le fond. */
@Composable
fun inkPrimary(): Color =
    MaterialTheme.colorScheme.onSurface

@Composable
fun inkSecondary(): Color =
    MaterialTheme.colorScheme.onSurfaceVariant

/** Fond plein (boutons, pastilles, champs) : le verre est réservé aux cartes et à la barre de navigation. */
@Composable
fun Modifier.solidSurface(shape: Shape): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return this
        .clip(shape)
        .background(if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f), shape)
}

/** Bouton rond plein. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 48.dp
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .solidSurface(CircleShape)
            .then(if (active) Modifier.background(colors.primary) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, description,
            tint = if (active) colors.onPrimary else inkPrimary(),
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** Pastille de filtre : pleine, la sélection prend la couleur d'accent. */
@Composable
fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .solidSurface(CircleShape)
            .then(if (selected) Modifier.background(colors.primary) else Modifier)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.onPrimary else inkPrimary()
        )
    }
}

/** Petite étiquette (format, état de lecture). */
@Composable
fun GlassTag(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.onSurface.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = inkPrimary()
        )
    }
}

/** Champ de recherche en pastille de verre. */
@Composable
fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .height(52.dp)
            .solidSurface(CircleShape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
    ) {
        Icon(OrynIcons.Search, null, tint = inkSecondary(), modifier = Modifier.size(22.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = inkSecondary(),
                    maxLines = 1
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = inkPrimary(),
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                ),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                OrynIcons.Close, tr("Effacer"),
                tint = inkSecondary(),
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable { onValueChange("") }
                    .padding(3.dp)
            )
        }
    }
}

/** Couleur moyenne de la couverture (teinte la carte de verre). null tant qu'elle n'est pas connue. */
@Composable
fun rememberCoverTint(book: BookEntity): Color? {
    val path = book.coverPath?.takeIf { it.isNotBlank() }
    val tint by produceState<Color?>(initialValue = null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            val bitmap = CoverCache.get(path)
                ?: CoverCache.decodeFile(path)?.also { CoverCache.put(path, it) }
            bitmap?.let {
                val px = Bitmap.createScaledBitmap(it, 1, 1, true).getPixel(0, 0)
                Color(px)
            }
        }
    }
    return tint
}

/**
 * Rectangle arrondi dont le coin haut-droit est creusé d'une encoche arrondie
 * (le contenu de l'encoche vit sur la carte, pas sur l'image).
 */
class NotchShape(
    private val cornerRadius: Dp = 18.dp,
    private val notchWidth: Dp = 46.dp,
    private val notchHeight: Dp = 42.dp,
    private val notchRadius: Dp = 12.dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = with(density) { cornerRadius.toPx() }
        val nw = with(density) { notchWidth.toPx() }
        val nh = with(density) { notchHeight.toPx() }
        val nr = with(density) { notchRadius.toPx() }
        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(w - nw - nr, 0f)
            quadraticBezierTo(w - nw, 0f, w - nw, nr)
            lineTo(w - nw, nh - nr)
            quadraticBezierTo(w - nw, nh, w - nw + nr, nh)
            lineTo(w - nr, nh)
            quadraticBezierTo(w, nh, w, nh + nr)
            lineTo(w, h - r)
            quadraticBezierTo(w, h, w - r, h)
            lineTo(r, h)
            quadraticBezierTo(0f, h, 0f, h - r)
            lineTo(0f, r)
            quadraticBezierTo(0f, 0f, r, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}
