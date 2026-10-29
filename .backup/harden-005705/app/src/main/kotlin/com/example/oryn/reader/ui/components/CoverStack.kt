package com.example.oryn.reader.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.oryn.reader.data.local.entity.BookEntity
import com.example.oryn.reader.ui.screen.coverFrame

private class Placement(val book: BookEntity, val rotation: Float, val dx: Dp, val dy: Dp)

/**
 * Pile de couvertures posées en éventail (jusqu'à trois), comme les listes « À lire » des applications de lecture.
 * Purement décorative : le libellé de la carte qui la contient porte le sens. Le premier livre est au premier plan.
 */
@Composable
fun CoverStack(books: List<BookEntity>, modifier: Modifier = Modifier, coverWidth: Dp = 76.dp) {
    val shown = books.take(3)
    if (shown.isEmpty()) return
    val coverHeight = coverWidth * 1.5f
    // Ordre de dessin : de l'arrière vers l'avant
    val placements = when (shown.size) {
        1 -> listOf(Placement(shown[0], 0f, 0.dp, 0.dp))
        2 -> listOf(
            Placement(shown[1], 7f, coverWidth * 0.42f, 4.dp),
            Placement(shown[0], -7f, 0.dp, 0.dp)
        )
        else -> listOf(
            Placement(shown[2], 9f, coverWidth * 0.84f, 6.dp),
            Placement(shown[1], -10f, 0.dp, 6.dp),
            Placement(shown[0], -1f, coverWidth * 0.42f, 0.dp)
        )
    }
    val spread = placements.maxOf { it.dx }
    Box(modifier.width(coverWidth + spread).height(coverHeight + 8.dp)) {
        placements.forEach { p ->
            BookCover(
                p.book,
                Modifier
                    .offset(x = p.dx, y = p.dy)
                    .size(coverWidth, coverHeight)
                    .graphicsLayer { rotationZ = p.rotation }
                    .coverFrame()
            )
        }
    }
}
