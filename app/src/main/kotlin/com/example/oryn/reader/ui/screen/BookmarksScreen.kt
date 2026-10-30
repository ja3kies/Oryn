package com.example.oryn.reader.ui.screen

import com.example.oryn.reader.i18n.trf
import com.example.oryn.reader.i18n.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import com.example.oryn.reader.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.oryn.reader.data.local.entity.BookEntity
import com.example.oryn.reader.ui.components.GlassChip
import com.example.oryn.reader.ui.components.LibraryEmptyState
import com.example.oryn.reader.ui.components.MascotMood
import com.example.oryn.reader.ui.components.OrynMascot
import com.example.oryn.reader.ui.components.inkSecondary
import com.example.oryn.reader.ui.components.navBarClearance

private enum class FavSort(val label: String) { RECENT("Récents"), TITLE("Titre"), AUTHOR("Auteur"), PROGRESS("Progression") }

@Composable
fun BookmarksScreen(
    favorites: List<BookEntity>,
    isLoading: Boolean,
    onToggleFavorite: (BookEntity) -> Unit,
    onBookClick: (BookEntity) -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var sortName by rememberSaveable { mutableStateOf(FavSort.RECENT.name) }
    val sort = FavSort.valueOf(sortName)
    val sorted = remember(favorites, sort) {
        when (sort) {
            FavSort.RECENT -> favorites.sortedByDescending { it.lastReadTimestamp }
            FavSort.TITLE -> favorites.sortedBy { it.title.plain() }
            FavSort.AUTHOR -> favorites.sortedBy { it.author.plain() }
            FavSort.PROGRESS -> favorites.sortedByDescending { it.progress }
        }
    }
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("Favoris"), style = MaterialTheme.typography.headlineLarge, color = colors.onBackground)
                if (favorites.isNotEmpty()) {
                    Text(
                        trf(if (favorites.size > 1) "%s livres mis de côté" else "%s livre mis de côté", favorites.size).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = inkSecondary()
                    )
                }
            }
            OrynMascot(size = 48.dp, mood = if (favorites.isEmpty()) MascotMood.SLEEPY else MascotMood.HAPPY)
        }
        if (favorites.isEmpty() && !isLoading) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                LibraryEmptyState(
                    title = tr("Aucun favori"),
                    subtitle = tr("Touche le marque-page d'un livre pour le retrouver ici."),
                    onImportClick = onImportClick
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FavSort.entries) { s -> GlassChip(s.label, s == sort, { sortName = s.name }) }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = navBarClearance + 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(sorted, key = { _, book -> book.id to book.coverPath }) { index, book ->
                    GridBook(
                        book,
                        modifier = Modifier.padding(top = if (index % 2 == 1) 26.dp else 0.dp),
                        onClick = { onBookClick(book) },
                        onToggleFavorite = { onToggleFavorite(book) }
                    )
                }
            }
        }
    }
}
