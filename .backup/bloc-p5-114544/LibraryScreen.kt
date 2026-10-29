package com.example.oryn.reader.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.oryn.reader.data.local.entity.BookEntity
import com.example.oryn.reader.ui.components.BookActionsHost
import com.example.oryn.reader.ui.components.BookCard
import com.example.oryn.reader.ui.components.BookCover
import com.example.oryn.reader.ui.components.FavoriteButton
import com.example.oryn.reader.ui.components.GlassChip
import com.example.oryn.reader.ui.components.GlassSearchField
import com.example.oryn.reader.ui.components.LibraryEmptyState
import com.example.oryn.reader.ui.components.OrynIcons
import com.example.oryn.reader.ui.components.OrynNav
import com.example.oryn.reader.ui.components.inkPrimary
import com.example.oryn.reader.ui.components.inkSecondary
import com.example.oryn.reader.ui.components.navBarClearance
import com.example.oryn.reader.ui.components.solidSurface
import java.text.Normalizer

internal enum class HomeFilter(val label: String) {
    ALL("Tous"), READING("En cours"), UNREAD("Non lus"), DONE("Terminés"), FAVORITES("Favoris")
}

internal enum class HomeSort(val label: String) {
    RECENT("Lus récemment"), TITLE("Titre (A → Z)"), AUTHOR("Auteur (A → Z)"), PROGRESS("Progression")
}

/** Deux modes seulement : grille de couvertures (défaut) ou liste détaillée. Les filtres restent toujours utiles. */
private enum class LibraryView(val label: String, val icon: ImageVector) {
    GRID("Grille", OrynIcons.Grid), LIST("Liste", OrynIcons.List)
}

/** « En cours » = les plus récents seulement : au plus 3 livres commencés et lus ces 30 derniers jours. */
internal const val MAX_READING = 3
internal const val RECENT_WINDOW_MS = 30L * 24 * 3600 * 1000

internal fun String.plain(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

internal fun BookEntity.matches(filter: HomeFilter, readingIds: Set<Long>): Boolean = when (filter) {
    HomeFilter.ALL -> true
    HomeFilter.READING -> id in readingIds
    HomeFilter.UNREAD -> progress < 0.01f
    HomeFilter.DONE -> progress >= 0.98f
    HomeFilter.FAVORITES -> isFavorite
}

internal fun recentReading(books: List<BookEntity>): List<BookEntity> {
    val now = System.currentTimeMillis()
    return books
        .filter { it.progress >= 0.01f && it.progress < 0.98f && now - it.lastReadTimestamp <= RECENT_WINDOW_MS }
        .sortedByDescending { it.lastReadTimestamp }
        .take(MAX_READING)
}

/**
 * Onglet « Bibliothèque » : en-tête avec pastille d'actions (importer · tri/filtre · affichage),
 * recherche, filtres, puis étagères arrondies (défaut), grille ou liste.
 */
@Composable
internal fun LibraryContent(
    books: List<BookEntity>,
    isLoading: Boolean,
    onToggleFavorite: (BookEntity) -> Unit,
    onBookClick: (BookEntity) -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(HomeFilter.ALL.name) }
    var sortName by rememberSaveable { mutableStateOf(HomeSort.RECENT.name) }
    var viewName by rememberSaveable { mutableStateOf(LibraryView.GRID.name) }
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    // Filtre demandé depuis l'accueil (« Terminés », « Non lus »…), consommé une seule fois
    val requested = OrynNav.libraryFilter
    LaunchedEffect(requested) {
        if (requested != null) {
            filterName = requested
            OrynNav.libraryFilter = null
        }
    }

    val filter = HomeFilter.valueOf(filterName)
    val sort = HomeSort.valueOf(sortName)
    val view = LibraryView.valueOf(viewName)

    val reading = remember(books) { recentReading(books) }
    val readingIds = remember(reading) { reading.map { it.id }.toSet() }

    val shown = remember(books, query, filter, sort, readingIds) {
        val q = query.trim().plain()
        books
            .filter { it.matches(filter, readingIds) }
            .filter { q.isEmpty() || it.title.plain().contains(q) || it.author.plain().contains(q) }
            .let { list ->
                when (sort) {
                    HomeSort.RECENT -> list.sortedByDescending { it.lastReadTimestamp }
                    HomeSort.TITLE -> list.sortedBy { it.title.plain() }
                    HomeSort.AUTHOR -> list.sortedBy { it.author.plain() }
                    HomeSort.PROGRESS -> list.sortedByDescending { it.progress }
                }
            }
    }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        // ── En-tête + pastille d'actions
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Bibliothèque",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.onBackground
                )
                if (books.isNotEmpty()) {
                    Text(
                        "${books.size} livre${if (books.size > 1) "s" else ""}" +
                            if (reading.isNotEmpty()) " · ${reading.size} en cours" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = inkSecondary()
                    )
                }
            }
            Row(
                Modifier.solidSurface(CircleShape).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillIcon(OrynIcons.Plus, "Importer un livre", onImportClick)
                Box {
                    PillIcon(OrynIcons.Sliders, "Trier et filtrer") { menuOpen = true }
                    RoundedMenu { DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        HomeSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                leadingIcon = { Icon(OrynIcons.Sort, null, modifier = Modifier.size(22.dp)) },
                                trailingIcon = {
                                    if (option == sort) Icon(OrynIcons.Check, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                                },
                                onClick = { sortName = option.name; menuOpen = false }
                            )
                        }
                    } }
                }
                // L'icône montre le mode vers lequel on bascule (convention des autres applications)
                val next = if (view == LibraryView.GRID) LibraryView.LIST else LibraryView.GRID
                PillIcon(next.icon, "Passer en affichage ${next.label.lowercase()}") { viewName = next.name }
            }
        }

        GlassSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Rechercher dans ta bibliothèque",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (books.isEmpty() && !isLoading) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                LibraryEmptyState(
                    title = "Aucun livre",
                    subtitle = "Ajoute un EPUB ou un PDF, ou explore les catalogues libres.",
                    onImportClick = onImportClick
                )
            }
            return@Column
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(HomeFilter.entries) { f ->
                val count = books.count { it.matches(f, readingIds) }
                GlassChip(
                    label = if (f == HomeFilter.ALL) f.label else "${f.label} · $count",
                    selected = f == filter,
                    onClick = { filterName = f.name }
                )
            }
        }

        run {
            LazyVerticalGrid(
                columns = if (view == LibraryView.LIST) GridCells.Fixed(1) else GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = navBarClearance + 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(if (view == LibraryView.LIST) 8.dp else 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (shown.isEmpty() && !isLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "none") {
                        Text(
                            "Aucun livre ne correspond.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = inkSecondary(),
                            modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth()
                        )
                    }
                }
                items(shown, key = { it.id }) { book ->
                    if (view == LibraryView.LIST) {
                        BookCard(book, onBookClick = { onBookClick(book) }, onToggleFavorite = { onToggleFavorite(book) })
                    } else {
                        GridBook(book, onClick = { onBookClick(book) }, onToggleFavorite = { onToggleFavorite(book) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PillIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = inkPrimary(), modifier = Modifier.size(24.dp))
    }
}

/** Menus déroulants aux coins bien arrondis (la forme « extra small » de Material 3 sert aux menus). */
@Composable
internal fun RoundedMenu(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(22.dp)),
        content = content
    )
}

/**
 * Étagère (réf. 4) : conteneur clair et doux, en-tête « ÉTIQUETTE en petites capitales » à gauche
 * et lien « Tout voir › » à droite quand l'étagère est cliquable.
 */
@Composable
internal fun Shelf(
    title: String,
    count: Int?,
    modifier: Modifier = Modifier,
    onHeader: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.05f))
                    else listOf(Color.White.copy(alpha = 0.92f), Color.White.copy(alpha = 0.70f))
                )
            )
            .border(1.dp, if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f), shape)
            .padding(top = 6.dp, bottom = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title.uppercase() + if (count != null) "  ·  $count" else "",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = inkSecondary(),
                modifier = Modifier.weight(1f)
            )
            if (onHeader != null) {
                Row(
                    Modifier.clip(CircleShape).clickable(onClick = onHeader).padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tout voir", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.primary)
                    Icon(OrynIcons.Chevron, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Column(Modifier.padding(top = 4.dp)) { content() }
    }
}

/** Rangée de couvertures posées sur une tablette. */
@Composable
internal fun ShelfRow(books: List<BookEntity>, onBookClick: (BookEntity) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(books, key = { it.id }) { book -> ShelfBook(book, onClick = { onBookClick(book) }) }
        }
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .padding(top = 2.dp)
                .fillMaxWidth()
                .height(7.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(colors.onSurface.copy(alpha = 0.04f), colors.onSurface.copy(alpha = 0.16f), colors.onSurface.copy(alpha = 0.04f))))
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShelfBook(book: BookEntity, onClick: () -> Unit, width: Dp = 92.dp) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var actions by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(width)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions = true
                }
            )
    ) {
        Box {
            BookCover(
                book,
                Modifier.fillMaxWidth().aspectRatio(2f / 3f).shadow(8.dp, RoundedCornerShape(12.dp))
            )
        }
        ProgressHairline(book.progress, Modifier.padding(top = 6.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            book.author,
            style = MaterialTheme.typography.labelSmall,
            color = inkSecondary(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    BookActionsHost(book, visible = actions, onDismiss = { actions = false })
}

/** Fine barre de progression (invisible pour un livre non commencé), à poser sous une couverture. */
@Composable
internal fun ProgressHairline(progress: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    if (progress < 0.01f) return
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(colors.onSurface.copy(alpha = 0.14f))
    ) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(colors.primary))
    }
}

/**
 * Carte de la grille : couverture entière (2:3, coins arrondis, ombre douce), barre de progression dessous,
 * titre sur 2 lignes, auteur, puis une ligne d'état (« 19 % », « Terminé » ou « Non lu ») avec le favori à droite.
 * Un petit repère de signet sur la couverture indique les favoris. Appui long = infos du livre.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GridBook(
    book: BookEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var actions by remember { mutableStateOf(false) }
    val state = when {
        book.progress >= 0.98f -> "Terminé"
        book.progress >= 0.01f -> "${(book.progress * 100).toInt()} %"
        else -> "Non lu"
    }
    Column(
        modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions = true
                }
            )
    ) {
        BookCover(
            book,
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).shadow(8.dp, RoundedCornerShape(12.dp))
        )
        ProgressHairline(book.progress, Modifier.padding(top = 8.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            book.author.ifBlank { " " },
            style = MaterialTheme.typography.labelSmall,
            color = inkSecondary(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (book.progress >= 0.01f) colors.primary else inkSecondary(),
                modifier = Modifier.weight(1f)
            )
            FavoriteButton(book.isFavorite, onToggleFavorite, size = 40.dp)
        }
    }
    BookActionsHost(book, visible = actions, onDismiss = { actions = false })
}
