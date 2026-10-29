package com.example.oryn.reader.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.oryn.reader.data.local.entity.BookEntity

/**
 * Vignette de grille : verre teinté par la couverture, couverture découpée par une encoche
 * qui loge le bouton favori, pastille de progression. Appui long = carte d'infos.
 * Toutes les vignettes ont la même hauteur (titre : 2 lignes réservées, auteur : 1).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookTile(
    book: BookEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val tint = rememberCoverTint(book)
    val haptics = LocalHapticFeedback.current
    var actions by remember { mutableStateOf(false) }
    val notchW = 34.dp
    val notchH = 32.dp

    Column(
        modifier
            .aspectRatio(0.70f)
            .glass(RoundedCornerShape(22.dp), tint)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions = true
                }
            )
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Couverture réduite, centrée dans la place restante : la carte garde un format large et régulier
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxHeight().aspectRatio(0.68f, matchHeightConstraintsFirst = true)) {
                BookCover(
                    book,
                    Modifier.fillMaxSize().clip(NotchShape(cornerRadius = 14.dp, notchWidth = notchW, notchHeight = notchH, notchRadius = 9.dp))
                )
                if (book.progress >= 0.01f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.60f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "${(book.progress * 100).toInt()} %",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Box(Modifier.align(Alignment.TopEnd).size(notchW, notchH), contentAlignment = Alignment.Center) {
                    FavoriteButton(book.isFavorite, onToggleFavorite, size = 26.dp)
                }
            }
        }
        Column(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp)) {
            Text(
                book.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                book.author,
                style = MaterialTheme.typography.labelSmall,
                color = inkSecondary(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    BookActionsHost(book, visible = actions, onDismiss = { actions = false })
}
