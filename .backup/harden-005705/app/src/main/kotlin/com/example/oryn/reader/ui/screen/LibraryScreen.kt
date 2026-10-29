package com.example.oryn.reader.ui.screen

import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.example.oryn.reader.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.SelectAll
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
import androidx.compose.ui.layout.layout
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
import com.example.oryn.reader.ui.components.HugeIcons
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

private enum class LibraryView(val label: String, val icon: ImageVector) {
    GRID("Grille", OrynIcons.Grid), LIST("Liste", OrynIcons.List)
}

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

@Composable
internal fun LibraryContent(
    books: List<BookEntity>,
    isLoading: Boolean,
    onToggleFavorite: (BookEntity) -> Unit,
    onBookClick: (BookEntity) -> Unit,
    onImportClick: () -> Unit,
    onDeleteBooks: (List<BookEntity>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(HomeFilter.ALL.name) }
    var sortName by rememberSaveable { mutableStateOf(HomeSort.RECENT.name) }
    var viewName by rememberSaveable { mutableStateOf(LibraryView.GRID.name) }
    var menuOpen by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedBooks by remember { mutableStateOf(setOf<BookEntity>()) }
    
    val colors = MaterialTheme.colorScheme

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
        if (isSelectionMode) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillIcon(Icons.Rounded.Close, tr("Annuler la sélection")) {
                    isSelectionMode = false
                    selectedBooks = emptySet()
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    trf(if (selectedBooks.size > 1) "%s sélectionnés" else "%s sélectionné", selectedBooks.size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f)
                )
                PillIcon(Icons.Rounded.SelectAll, tr("Tout sélectionner")) {
                    if (selectedBooks.size == shown.size) selectedBooks = emptySet()
                    else selectedBooks = shown.toSet()
                }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable {
                        if (selectedBooks.isNotEmpty()) {
                            onDeleteBooks(selectedBooks.toList())
                            isSelectionMode = false
                            selectedBooks = emptySet()
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = HugeIcons.Trash, contentDescription = tr("Supprimer la sélection"), tint = colors.error, modifier = Modifier.size(24.dp))
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Bibliothèque"),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.onBackground
                    )
                    if (books.isNotEmpty()) {
                        Text(
                            (trf(if (books.size > 1) "%s livres" else "%s livre", books.size) +
                                if (reading.isNotEmpty()) trf(" · %s en cours", reading.size) else "").uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = inkSecondary()
                        )
                    }
                }
                Row(
                    Modifier.solidSurface(CircleShape).padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillIcon(OrynIcons.Plus, tr("Importer un livre"), onImportClick)
                    Box {
                        PillIcon(OrynIcons.Filter, tr("Trier et filtrer")) { menuOpen = true }
                        RoundedMenu { DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            HomeSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    leadingIcon = { Icon(if (option == HomeSort.TITLE || option == HomeSort.AUTHOR) OrynIcons.SortAz else OrynIcons.Sort, null, modifier = Modifier.size(22.dp)) },
                                    trailingIcon = {
                                        if (option == sort) Icon(OrynIcons.Check, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                                    },
                                    onClick = { sortName = option.name; menuOpen = false }
                                )
                            }
                        } }
                    }
                    val next = if (view == LibraryView.GRID) LibraryView.LIST else LibraryView.GRID
                    PillIcon(next.icon, trf("Passer en affichage %s", tr(next.label).lowercase())) { viewName = next.name }
                }
            }
        }

        GlassSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = tr("Rechercher dans ta bibliothèque"),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (books.isEmpty() && !isLoading) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                LibraryEmptyState(
                    title = tr("Aucun livre"),
                    subtitle = tr("Ajoute un EPUB ou un PDF, ou explore les catalogues libres."),
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
                    label = if (f == HomeFilter.ALL) tr(f.label) else "${tr(f.label)} · $count",
                    selected = f == filter,
                    onClick = { filterName = f.name }
                )
            }
        }

        run {
            LazyVerticalGrid(
                columns = if (view == LibraryView.LIST) GridCells.Fixed(1) else GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = navBarClearance + 12.dp),
                horizontalArrangement = Arrangement.spacedBy(if (view == LibraryView.LIST) 12.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(if (view == LibraryView.LIST) 8.dp else 22.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (shown.isEmpty() && !isLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "none") {
                        Text(
                            tr("Aucun livre ne correspond."),
                            style = MaterialTheme.typography.bodyLarge,
                            color = inkSecondary(),
                            modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth()
                        )
                    }
                }
                itemsIndexed(shown, key = { _, book -> book.id }) { index, book ->
                    val isSelected = selectedBooks.contains(book)
                    val handleClick = {
                        if (isSelectionMode) {
                            selectedBooks = if (isSelected) selectedBooks - book else selectedBooks + book
                            if (selectedBooks.isEmpty()) isSelectionMode = false
                        } else {
                            onBookClick(book)
                        }
                    }
                    val handleSelect = {
                        if (!isSelectionMode) {
                            isSelectionMode = true
                            selectedBooks = selectedBooks + book
                        }
                    }

                    if (view == LibraryView.LIST) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            BookCard(
                                book,
                                onBookClick = handleClick,
                                onSelect = handleSelect,
                                onToggleFavorite = { onToggleFavorite(book) }
                            )
                            if (isSelectionMode) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) colors.primary else colors.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(28.dp)
                                )
                            }
                        }
                    } else {
                        GridBook(
                            book,
                            modifier = Modifier.padding(top = if (index % 2 == 1) 26.dp else 0.dp),
                            onClick = handleClick,
                            onSelect = handleSelect,
                            onToggleFavorite = { onToggleFavorite(book) },
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected
                        )
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

@Composable
internal fun RoundedMenu(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(22.dp)),
        content = content
    )
}

@Composable
internal fun Shelf(
    title: String,
    count: Int?,
    modifier: Modifier = Modifier,
    onHeader: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title + if (count != null) "  ·  $count" else "",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (onHeader != null) {
                Row(
                    Modifier
                        .heightIn(min = 40.dp)
                        .solidSurface(CircleShape)
                        .clickable(onClickLabel = tr("Tout voir"), onClick = onHeader)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tr("Tout voir"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    Icon(OrynIcons.Chevron, null, tint = colors.onSurface, modifier = Modifier.padding(start = 2.dp).size(18.dp))
                }
            }
        }
        Column(Modifier.padding(top = 8.dp)) { content() }
    }
}

@Composable
internal fun ShelfRow(books: List<BookEntity>, onBookClick: (BookEntity) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.bleed(16.dp)
    ) {
        items(books, key = { it.id }) { book -> ShelfBook(book, onClick = { onBookClick(book) }, width = 112.dp) }
    }
}

/** Laisse une rangée défiler jusqu'au bord de l'écran, malgré la marge du parent. */
internal fun Modifier.bleed(margin: androidx.compose.ui.unit.Dp): Modifier = this.layout { measurable, constraints ->
    val extra = margin.roundToPx()
    val w = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
    layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
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
                Modifier.fillMaxWidth().aspectRatio(2f / 3f).coverFrame()
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

@Composable
internal fun ProgressHairline(progress: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(if (progress >= 0.01f) colors.onSurface.copy(alpha = 0.12f) else Color.Transparent)
    ) {
        if (progress >= 0.01f) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(if (colors.background.luminance() < 0.5f) colors.primaryContainer else colors.primary))
        }
    }
}

@Composable
internal fun accentInk(): Color {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    // Texte / icône d'accent : l'accent lui-même en sombre, l'encre en clair (le chartreuse n'est pas lisible sur gris clair)
    return if (dark) colors.primaryContainer else colors.onSurface
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GridBook(
    book: BookEntity,
    onClick: () -> Unit,
    onSelect: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var actions by remember { mutableStateOf(false) }
    val state = when {
        book.progress >= 0.98f -> tr("Terminé")
        book.progress >= 0.01f -> "${(book.progress * 100).toInt()} %"
        else -> tr("Non lu")
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
        Box {
            BookCover(
                book,
                Modifier.fillMaxWidth().aspectRatio(2f / 3f).coverFrame()
            )
            if (isSelectionMode) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                )
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) colors.primary else Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp)
                )
            }
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
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                state,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = inkSecondary(),
                modifier = Modifier.weight(1f)
            )
            FavoriteButton(
                isFavorite = book.isFavorite,
                onClick = onToggleFavorite,
                size = 28.dp
            )
        }
    }
    if (!isSelectionMode) {
        BookActionsHost(book, visible = actions, onDismiss = { actions = false }, onSelect = onSelect)
    }
}

/** Couverture posée : coins arrondis, liseré clair et ombre douce, comme un objet sur la table. */
@Composable
internal fun Modifier.coverFrame(): Modifier {
    val shape = RoundedCornerShape(10.dp)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return this
        .shadow(if (dark) 0.dp else 10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.16f), spotColor = Color.Black.copy(alpha = 0.22f))
        .clip(shape)
        .border(if (dark) 0.dp else 2.dp, Color.White, shape)
}
