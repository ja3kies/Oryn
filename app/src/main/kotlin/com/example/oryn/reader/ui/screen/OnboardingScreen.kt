package com.example.oryn.reader.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oryn.reader.i18n.Text
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.ui.theme.accent
import com.example.oryn.reader.ui.theme.onAccent
import kotlin.math.min

private data class IntroPage(val title: String, val body: String, val illustration: Int)

// Couleurs des couvertures dessinées : fixes (elles doivent ressortir en clair comme en sombre).
private val Cream = Color(0xFFF1EDE2)
private val Ink = Color(0xFF141414)
private val Coral = Color(0xFFFF7A59)
private val Forest = Color(0xFF1F3B2D)
private val Butter = Color(0xFFFFE04A)
private val Rust = Color(0xFFB5452B)

/**
 * Présentation en 4 pages : une grande illustration faite de couvertures posées, un titre en sérif,
 * une phrase, un bouton noir en pilule. On avance au bouton ou en glissant ; « Passer » est toujours là.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val pages = remember {
        listOf(
            IntroPage("Tes livres, à ton rythme", "Un espace calme pour retrouver le plaisir de lire, une page après l'autre.", 0),
            IntroPage("Toute ta bibliothèque avec toi", "Importe tes fichiers EPUB, PDF, TXT ou HTML, puis explore les catalogues.", 1),
            IntroPage("Une lecture qui te ressemble", "Choisis ton thème, ta police et le mode texte pour les PDF.", 2),
            IntroPage("Ton espace, tes choix", "Ta bibliothèque reste sur cet appareil. Ajuste l'apparence quand tu le souhaites.", 3)
        )
    }
    var page by remember { mutableIntStateOf(0) }
    val colors = MaterialTheme.colorScheme
    val last = pages.lastIndex

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(page) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = {
                        if (dx < -90f && page < last) page++ else if (dx > 90f && page > 0) page--
                    }
                ) { _, delta -> dx += delta }
            }
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Oryn", style = MaterialTheme.typography.headlineSmall, color = colors.onBackground)
            Box(Modifier.weight(1f))
            Text(
                "0${page + 1} / 0${pages.size}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp)
            )
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onFinish)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tr("Passer"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
            }
        }

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { w -> dir * w / 5 } + fadeIn()) togetherWith
                    (slideOutHorizontally { w -> -dir * w / 5 } + fadeOut())
            },
            label = "onboardingPage",
            modifier = Modifier.weight(1f)
        ) { index ->
            val item = pages[index]
            Column(Modifier.fillMaxSize()) {
                IntroIllustration(item.illustration, Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.onBackground,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    item.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(top = 10.dp, end = 24.dp)
                )
            }
        }

        Row(Modifier.padding(top = 22.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pages.indices.forEach { index ->
                Box(
                    Modifier
                        .size(if (index == page) 28.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (index == page) colors.onBackground else colors.outlineVariant)
                )
            }
        }

        val finalPage = page == last
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clip(CircleShape)
                .background(if (finalPage) colors.accent else colors.primary)
                .clickable(role = Role.Button) { if (finalPage) onFinish() else page++ },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (finalPage) tr("Commencer") else tr("Suivant"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (finalPage) colors.onAccent else colors.onPrimary
            )
        }
    }
}

// ───────────────────────── Illustrations : couvertures posées, dessinées au Canvas ─────────────────────────

@Composable
private fun IntroIllustration(kind: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val accent = colors.accent
    val ink = colors.onBackground
    val surface = colors.surfaceContainerLowest
    val paper = colors.surfaceContainerHigh
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val u = min(w, h)
        val c = Offset(w / 2f, h / 2f)
        when (kind) {
            // 1 · pile de trois couvertures sur un grand disque d'accent
            0 -> {
                drawCircle(accent, u * 0.46f, c)
                cover(Offset(c.x - u * 0.40f, c.y - u * 0.30f), Size(u * 0.50f, u * 0.74f), -12f, 0, accent)
                cover(Offset(c.x + u * 0.00f, c.y - u * 0.40f), Size(u * 0.50f, u * 0.74f), 9f, 2, accent)
                cover(Offset(c.x - u * 0.24f, c.y - u * 0.24f), Size(u * 0.52f, u * 0.77f), -1f, 1, accent)
            }
            // 2 · grille décalée de six couvertures (comme la bibliothèque)
            1 -> {
                val cw = u * 0.30f
                val ch = cw * 1.5f
                val gap = u * 0.05f
                val x0 = c.x - (cw * 3 + gap * 2) / 2f
                val styles = listOf(0, 1, 2, 3, 4, 5)
                for (i in 0 until 6) {
                    val col = i % 3
                    val row = i / 3
                    val dy = if (col == 1) ch * 0.22f else 0f
                    cover(
                        Offset(x0 + col * (cw + gap), c.y - ch - gap / 2f + row * (ch + gap) + dy - ch * 0.08f),
                        Size(cw, ch), listOf(-3f, 2f, -2f, 3f, -2f, 2f)[i], styles[i], accent
                    )
                }
            }
            // 3 · une page de texte et un panneau de réglages sombre à pastille d'accent
            2 -> {
                val pw = u * 0.62f
                val ph = u * 0.86f
                val pt = Offset(c.x - pw * 0.78f, c.y - ph / 2f)
                drawRoundRect(Color.Black.copy(alpha = 0.10f), pt + Offset(0f, u * 0.015f), Size(pw, ph), CornerRadius(u * 0.05f))
                drawRoundRect(surface, pt, Size(pw, ph), CornerRadius(u * 0.05f))
                for (i in 0 until 9) {
                    val lw = pw * (if (i % 4 == 3) 0.45f else 0.78f)
                    drawRoundRect(ink.copy(alpha = 0.16f), Offset(pt.x + pw * 0.11f, pt.y + ph * (0.10f + i * 0.085f)), Size(lw, u * 0.016f), CornerRadius(u))
                }
                // surlignage
                drawRoundRect(accent, Offset(pt.x + pw * 0.11f, pt.y + ph * (0.10f + 3 * 0.085f) - u * 0.008f), Size(pw * 0.78f, u * 0.034f), CornerRadius(u))
                // panneau
                val qw = u * 0.56f
                val qh = u * 0.44f
                val qt = Offset(c.x + u * 0.02f, c.y - qh * 0.1f)
                drawRoundRect(Color.Black.copy(alpha = 0.22f), qt + Offset(0f, u * 0.02f), Size(qw, qh), CornerRadius(u * 0.07f))
                drawRoundRect(Ink, qt, Size(qw, qh), CornerRadius(u * 0.07f))
                // bascule de thème
                drawRoundRect(Color(0xFF2E2E2E), Offset(qt.x + qw * 0.08f, qt.y + qh * 0.10f), Size(qw * 0.84f, qh * 0.22f), CornerRadius(u))
                drawRoundRect(accent, Offset(qt.x + qw * 0.34f, qt.y + qh * 0.10f), Size(qw * 0.36f, qh * 0.22f), CornerRadius(u))
                // curseurs
                for (i in 0..1) {
                    val y = qt.y + qh * (0.52f + i * 0.24f)
                    drawRoundRect(Color(0xFF3A3A3A), Offset(qt.x + qw * 0.08f, y), Size(qw * 0.84f, qh * 0.075f), CornerRadius(u))
                    drawRoundRect(accent, Offset(qt.x + qw * 0.08f, y), Size(qw * (0.52f - i * 0.18f), qh * 0.075f), CornerRadius(u))
                    drawCircle(accent, qh * 0.07f, Offset(qt.x + qw * (0.60f - i * 0.18f), y + qh * 0.0375f))
                }
            }
            // 4 · un disque moitié clair, moitié sombre, et l'étoile d'Oryn
            else -> {
                val r = u * 0.42f
                drawCircle(Cream, r, c)
                val half = Path().apply {
                    moveTo(c.x, c.y - r)
                    arcTo(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r), -90f, 180f, false)
                    close()
                }
                drawPath(half, Ink)
                drawStar(c, r * 0.46f, accent)
                drawCircle(ink.copy(alpha = 0.9f), r * 1.0f, c, style = Stroke(width = u * 0.008f))
                // deux petites couvertures posées
                cover(Offset(c.x - r * 1.18f, c.y + r * 0.35f), Size(u * 0.22f, u * 0.33f), -10f, 4, accent)
                cover(Offset(c.x + r * 0.78f, c.y + r * 0.40f), Size(u * 0.22f, u * 0.33f), 9f, 3, accent)
            }
        }
    }
}

/** Étoile à quatre branches (motif d'Oryn). */
private fun DrawScope.drawStar(center: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticBezierTo(center.x + r * 0.14f, center.y - r * 0.14f, center.x + r, center.y)
        quadraticBezierTo(center.x + r * 0.14f, center.y + r * 0.14f, center.x, center.y + r)
        quadraticBezierTo(center.x - r * 0.14f, center.y + r * 0.14f, center.x - r, center.y)
        quadraticBezierTo(center.x - r * 0.14f, center.y - r * 0.14f, center.x, center.y - r)
        close()
    }
    drawPath(p, color)
}

/** Une couverture imaginaire : liseré clair, ombre douce, motif géométrique selon [style]. */
private fun DrawScope.cover(topLeft: Offset, size: Size, degrees: Float, style: Int, accent: Color) {
    val corner = CornerRadius(size.width * 0.07f)
    rotate(degrees, pivot = Offset(topLeft.x + size.width / 2f, topLeft.y + size.height / 2f)) {
        drawRoundRect(Color.Black.copy(alpha = 0.16f), topLeft + Offset(0f, size.height * 0.03f), size, corner)
        drawRoundRect(Color.White, topLeft, size, corner)
        val inset = size.width * 0.035f
        val itl = topLeft + Offset(inset, inset)
        val isz = Size(size.width - inset * 2, size.height - inset * 2)
        val iw = isz.width
        val ih = isz.height
        val clip = Path().apply { addRoundRect(RoundRect(itl.x, itl.y, itl.x + iw, itl.y + ih, CornerRadius(corner.x * 0.8f))) }
        clipPath(clip) {
            when (style) {
                0 -> {
                    drawRect(Coral, itl, isz)
                    drawCircle(Cream, iw * 0.30f, Offset(itl.x + iw * 0.5f, itl.y + ih * 0.42f))
                    drawCircle(Ink, iw * 0.10f, Offset(itl.x + iw * 0.62f, itl.y + ih * 0.36f))
                }
                1 -> {
                    drawRect(Ink, itl, isz)
                    drawArc(accent, 180f, 180f, true, Offset(itl.x + iw * 0.12f, itl.y + ih * 0.50f), Size(iw * 0.76f, iw * 0.76f))
                    for (i in 0..2) drawRect(Cream.copy(alpha = 0.8f), Offset(itl.x + iw * 0.14f, itl.y + ih * (0.10f + i * 0.05f)), Size(iw * (0.5f - i * 0.1f), ih * 0.018f))
                }
                2 -> {
                    drawRect(Cream, itl, isz)
                    for (i in 0..2) drawRect(if (i == 1) Rust else Ink, Offset(itl.x + iw * (0.14f + i * 0.28f), itl.y + ih * 0.12f), Size(iw * 0.18f, ih * (0.55f - i * 0.1f)))
                    drawRect(Ink, Offset(itl.x + iw * 0.14f, itl.y + ih * 0.82f), Size(iw * 0.5f, ih * 0.02f))
                }
                3 -> {
                    drawRect(Forest, itl, isz)
                    drawCircle(Butter, iw * 0.20f, Offset(itl.x + iw * 0.5f, itl.y + ih * 0.32f))
                    for (i in 0..3) drawRoundRect(Cream.copy(alpha = 0.55f), Offset(itl.x + iw * 0.10f, itl.y + ih * (0.62f + i * 0.07f)), Size(iw * 0.80f, ih * 0.02f), CornerRadius(ih))
                }
                4 -> {
                    drawRect(accent, itl, isz)
                    drawArc(Ink, 90f, 90f, true, Offset(itl.x - iw * 0.55f, itl.y + ih * 0.30f), Size(iw * 1.10f, iw * 1.10f))
                    drawCircle(Ink, iw * 0.07f, Offset(itl.x + iw * 0.76f, itl.y + ih * 0.18f))
                }
                else -> {
                    drawRect(Butter, itl, isz)
                    drawCircle(Coral, iw * 0.30f, Offset(itl.x + iw * 0.5f, itl.y + ih * 0.45f), style = Stroke(iw * 0.10f))
                    drawRect(Ink, Offset(itl.x + iw * 0.14f, itl.y + ih * 0.84f), Size(iw * 0.44f, ih * 0.02f))
                }
            }
        }
    }
}
