package com.example.oryn.reader.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Fond de l'application : des taches de couleur floues (cercles à dégradé radial) sur une base blanche
 * (thème clair) ou brune (thème sombre) : halos discrets de sauge, terre cuite et ambre, issus du thème actif.
 * Plus de lignes : seulement des aplats doux. Dessiné une seule fois (aucun coût à l'écran).
 */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val base = scheme.background

    // (centre x, centre y, rayon relatif à la largeur, couleur clair, couleur sombre, opacité)
    val earth = scheme.primary
    val sage = scheme.secondary
    val amber = scheme.tertiary

    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            data class Blob(val x: Float, val y: Float, val r: Float, val c: Color, val a: Float)
            // Taches discrètes, petites et décalées vers les bords : jamais au milieu du contenu
            val blobs = listOf(
                Blob(0.06f, 0.03f, 0.46f, sage, 0.12f),
                Blob(0.98f, 0.06f, 0.42f, earth, 0.10f),
                Blob(0.62f, 0.20f, 0.26f, amber, 0.06f),
                Blob(0.00f, 0.55f, 0.30f, earth, 0.06f),
                Blob(1.00f, 0.74f, 0.32f, sage, 0.05f),
                Blob(0.10f, 1.00f, 0.34f, amber, 0.05f)
            )
            val brushes = blobs.map { b ->
                val center = Offset(b.x * w, b.y * h)
                val radius = b.r * w
                Brush.radialGradient(
                    0.0f to b.c.copy(alpha = b.a * (if (dark) 0.60f else 1f)),
                    0.50f to b.c.copy(alpha = b.a * 0.40f * (if (dark) 0.60f else 1f)),
                    1.0f to b.c.copy(alpha = 0f),
                    center = center,
                    radius = radius
                )
            }
            onDrawBehind {
                drawRect(base)
                brushes.forEach { drawRect(it) }
            }
        }
    )
}
