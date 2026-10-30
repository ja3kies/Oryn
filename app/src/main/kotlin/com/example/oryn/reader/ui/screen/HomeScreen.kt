package com.example.oryn.reader.ui.screen

import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.requiredHeight
import com.example.oryn.reader.ui.theme.accent
import com.example.oryn.reader.ui.theme.onAccent
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.example.oryn.reader.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.oryn.reader.data.local.entity.BookEntity
import com.example.oryn.reader.ui.components.Book3D
import com.example.oryn.reader.ui.components.CoverStack
import com.example.oryn.reader.ui.components.BookActionsHost
import com.example.oryn.reader.ui.components.FavoriteButton
import com.example.oryn.reader.ui.components.LibraryEmptyState
import com.example.oryn.reader.ui.components.MascotMood
import com.example.oryn.reader.ui.components.OrynIcons
import com.example.oryn.reader.ui.components.OrynMascot
import com.example.oryn.reader.ui.components.OrynNav
import com.example.oryn.reader.ui.components.HomeSection
import com.example.oryn.reader.ui.components.glass
import com.example.oryn.reader.ui.components.inkPrimary
import com.example.oryn.reader.ui.components.inkSecondary
import com.example.oryn.reader.ui.components.navBarClearance
import com.example.oryn.reader.ui.components.relativeTime
import com.example.oryn.reader.ui.components.rememberCoverTint
import com.example.oryn.reader.ui.components.solidSurface
import java.util.Calendar

/**
 * Route « home » : l'accueil (tableau de bord) ou, quand l'onglet Bibliothèque de la barre est actif,
 * la bibliothèque complète. La signature reste celle d'avant pour ne pas toucher à la navigation.
 */
@Composable
fun HomeScreen(
    books: List<BookEntity>,
    isLoading: Boolean,
    onToggleFavorite: (BookEntity) -> Unit,
    onBookClick: (BookEntity) -> Unit,
    onImportClick: () -> Unit,
    onDeleteBooks: (List<BookEntity>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (OrynNav.section == HomeSection.LIBRARY) {
        BackHandler { OrynNav.section = HomeSection.HOME }
        LibraryContent(
                    books = books,
                    isLoading = isLoading,
                    onToggleFavorite = onToggleFavorite,
                    onBookClick = onBookClick,
                    onImportClick = onImportClick,
                    onDeleteBooks = onDeleteBooks,
                    modifier = modifier
                )
    } else {
        Dashboard(books, isLoading, onToggleFavorite, onBookClick, onImportClick, modifier)
    }
}

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 5 -> tr("Bonne nuit")
        h < 12 -> tr("Bonjour")
        h < 18 -> tr("Bon après-midi")
        else -> tr("Bonsoir")
    }
}

@Composable
private fun Dashboard(
    books: List<BookEntity>,
    isLoading: Boolean,
    onToggleFavorite: (BookEntity) -> Unit,
    onBookClick: (BookEntity) -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    val reading = remember(books) { recentReading(books) }
    val recent = remember(books) { books.sortedByDescending { it.id }.take(12) }
    val done = remember(books) { books.count { it.progress >= 0.98f } }
    val unread = remember(books) { books.count { it.progress < 0.01f } }
    val favorites = remember(books) { books.count { it.isFavorite } }

    var menuOpen by remember { mutableStateOf(false) }
    val justFinished = remember(books) {
        val now = System.currentTimeMillis()
        books.filter { it.progress >= 0.98f && now - it.lastReadTimestamp <= 7L * 24 * 3600 * 1000 }
            .maxByOrNull { it.lastReadTimestamp }
    }
    var tip by remember { mutableIntStateOf(0) }
    var bubble by remember { mutableStateOf(false) }
    // Pic d'émotion : la mascotte félicite à l'ouverture si un livre vient d'être terminé
    LaunchedEffect(justFinished?.id) { if (justFinished != null && tip == 0) tip = 3 }
    LaunchedEffect(tip) {
        if (tip > 0) {
            bubble = true
            kotlinx.coroutines.delay(4200)
            bubble = false
        }
    }
    val tipText = remember(tip, reading, books, justFinished) {
        val top = reading.firstOrNull()
        when {
            books.isEmpty() -> tr("Ajoute ton premier livre avec le bouton +")
            justFinished != null && tip % 3 == 0 -> trf("Bravo, tu as terminé « %s » !", justFinished.title)
            top != null && tip % 3 == 1 -> trf("Tu en étais à %s % de « %s »", (top.progress * 100).toInt(), top.title)
            tip % 3 == 2 -> trf(if (done > 1) "%s livres terminés, %s à découvrir" else "%s livre terminé, %s à découvrir", done, unread)
            else -> tr("Quelques pages avant de dormir ?")
        }
    }

    LazyColumn(
        modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = navBarClearance + 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ── Coin supérieur : mascotte à gauche, menu à droite, puis grande salutation à deux tons
        item(key = "top") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OrynMascot(
                    size = 52.dp,
                    mood = if (bubble) MascotMood.WOW else MascotMood.HAPPY,
                    onClick = { tip += 1 }
                )
                Spacer(Modifier.weight(1f))
                Box {
                    Box(
                        Modifier.size(52.dp).solidSurface(CircleShape).clickable { menuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(OrynIcons.Menu, tr("Menu"), tint = inkPrimary(), modifier = Modifier.size(26.dp))
                    }
                    RoundedMenu { DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MenuItem(OrynIcons.Settings, tr("Réglages")) { menuOpen = false; OrynNav.open("settings") }
                        MenuItem(OrynIcons.Globe, tr("Catalogues en ligne")) { menuOpen = false; OrynNav.open("online_search") }
                        MenuItem(OrynIcons.Plus, tr("Importer un livre")) { menuOpen = false; onImportClick() }
                    } }
                }
            }
            // Bulle de la mascotte
            AnimatedVisibility(
                visible = bubble,
                enter = fadeIn() + scaleIn(initialScale = 0.85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.1f, 0f)),
                exit = fadeOut() + scaleOut(targetScale = 0.9f)
            ) {
                Text(
                    tipText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = inkPrimary(),
                    modifier = Modifier
                        .padding(top = 8.dp, end = 40.dp)
                        .solidSurface(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            Column(Modifier.padding(start = 4.dp, top = 20.dp)) {
                Text(
                    greeting() + ",",
                    style = MaterialTheme.typography.headlineSmall,
                    color = inkSecondary().copy(alpha = 0.75f)
                )
                Text(
                    if (books.isEmpty()) tr("Ta bibliothèque t'attend") else tr("Qu'est-ce qu'on lit ?"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onBackground
                )
            }
        }

        if (books.isEmpty() && isLoading) {
            item(key = "skeleton") {
                val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "sk").animateFloat(
                    0.08f, 0.20f,
                    androidx.compose.animation.core.infiniteRepeatable(
                        androidx.compose.animation.core.tween(700),
                        androidx.compose.animation.core.RepeatMode.Reverse
                    ),
                    label = "skAlpha"
                )
                Box(
                    Modifier.fillMaxWidth().height(228.dp).clip(RoundedCornerShape(34.dp))
                        .background(colors.onSurface.copy(alpha = pulse))
                )
            }
            return@LazyColumn
        }
        if (books.isEmpty() && !isLoading) {
            item(key = "empty") {
                LibraryEmptyState(
                    title = tr("Aucun livre"),
                    subtitle = tr("Ajoute un EPUB ou un PDF, ou explore les catalogues libres."),
                    onImportClick = onImportClick
                )
            }
            return@LazyColumn
        }

        // ── Continuer la lecture (livre 3D)
        if (reading.isNotEmpty()) {
            item(key = "reading") { ReadingCarousel(reading, onBookClick, onToggleFavorite) }
        }

        // ── Ajoutés récemment
        if (recent.isNotEmpty()) {
            item(key = "recent") {
                Shelf(title = tr("Ajoutés récemment"), count = null, onHeader = { OrynNav.openLibrary() }) {
                    ShelfRow(recent, onBookClick)
                }
            }
        }

        // ── Collections : trois cartes avec une pile de couvertures, puis les catalogues
        item(key = "browse") {
            val favs = remember(books) { books.filter { it.isFavorite } }
            val finished = remember(books) { books.filter { it.progress >= 0.98f } }
            val toRead = remember(books) { books.filter { it.progress < 0.01f } }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    tr("Collections"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    modifier = Modifier.padding(start = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(start = 0.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.bleed(16.dp)
                ) {
                    item { CollectionCard(tr("Favoris"), favs) { OrynNav.open("bookmarks") } }
                    item { CollectionCard(tr("Terminés"), finished) { OrynNav.openLibrary("DONE") } }
                    item { CollectionCard(tr("Non lus"), toRead) { OrynNav.openLibrary("UNREAD") } }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .solidSurface(CircleShape)
                        .clickable(onClickLabel = tr("Catalogues en ligne")) { OrynNav.open("online_search") }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(OrynIcons.Globe, null, tint = accentInk(), modifier = Modifier.size(22.dp))
                    Text(
                        tr("Catalogues en ligne"),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f).padding(start = 14.dp)
                    )
                    Icon(OrynIcons.Chevron, null, tint = inkSecondary(), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(22.dp)) },
        onClick = onClick
    )
}

/** Carte de collection : titre et nombre en haut, pile de couvertures en bas (cartes sobres : l'accent reste à la carte « Continuer »). */
@Composable
private fun CollectionCard(title: String, books: List<BookEntity>, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .width(168.dp)
            .height(214.dp)
            .solidSurface(RoundedCornerShape(28.dp))
            .clickable(onClickLabel = title, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$title : ${books.size}" }
    ) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1)
            Text(books.size.toString(), style = MaterialTheme.typography.labelSmall, color = inkSecondary())
        }
        if (books.isEmpty()) {
            Icon(OrynIcons.BookOpen, null, tint = inkSecondary().copy(alpha = 0.5f), modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).size(28.dp))
        } else {
            CoverStack(books, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 14.dp), coverWidth = 64.dp)
        }
    }
}

/** Les 1 à 3 lectures les plus récentes, une grande carte à la fois (défilement qui s'aimante). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadingCarousel(
    reading: List<BookEntity>,
    onBookClick: (BookEntity) -> Unit,
    onToggleFavorite: (BookEntity) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val state = rememberLazyListState()
        val cardWidth = if (reading.size > 1) maxWidth - 28.dp else maxWidth
        // La rangée déborde de 16 dp de chaque côté (la marge de l'écran) : la carte suivante dépasse jusqu'au bord
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.layout { measurable, constraints ->
                val extra = 32.dp.roundToPx()
                val w = constraints.maxWidth + extra
                val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
            }
        ) {
            items(reading, key = { it.id to it.coverPath }) { book ->
                ContinueCard(
                    book = book,
                    onOpen = { onBookClick(book) },
                    onToggleFavorite = { onToggleFavorite(book) },
                    modifier = Modifier.width(cardWidth)
                )
            }
        }
    }
}

/**
 * « Continuer la lecture » : grande carte d'accent (chartreuse), le livre 3D déborde en haut à gauche,
 * légende en petites capitales, titre en sérif, progression fine et bouton « Reprendre » noir en pilule.
 * Texte toujours en encre foncée sur l'accent. Appui long = infos du livre.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(
    book: BookEntity,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var actions by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(34.dp)
    val ink = colors.onAccent
    val pct = (book.progress * 100).toInt()

    Box(modifier.fillMaxWidth().padding(top = 30.dp)) {
        // Fond d'accent + zone tactile : séparés du contenu pour que le livre puisse dépasser du haut
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .background(colors.accent)
                .combinedClickable(
                    onClickLabel = tr("Reprendre la lecture"),
                    onClick = onOpen,
                    onLongClickLabel = tr("Afficher les infos du livre"),
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions = true
                    }
                )
                .semantics { contentDescription = trf("Continuer la lecture : %s, %s, %s pour cent lu", book.title, book.author, pct) }
        )
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 188.dp)
                .padding(start = 8.dp, end = 18.dp, top = 18.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Le livre dépasse du haut de la carte
            Box(Modifier.width(116.dp).height(172.dp), contentAlignment = Alignment.Center) {
                Book3D(book, Modifier.requiredWidth(116.dp).requiredHeight(176.dp).offset(y = (-26).dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("CONTINUER · ") + "$pct %",
                            style = MaterialTheme.typography.labelSmall,
                            color = ink.copy(alpha = 0.72f),
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).clickable(
                                onClickLabel = if (book.isFavorite) tr("Retirer des favoris") else tr("Ajouter aux favoris"),
                                onClick = onToggleFavorite
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(if (book.isFavorite) ink else ink.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (book.isFavorite) OrynIcons.BookmarkFilled else OrynIcons.Bookmark,
                                    contentDescription = null,
                                    tint = if (book.isFavorite) colors.accent else ink,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        book.author + " · " + relativeTime(book.lastReadTimestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = ink.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(ink.copy(alpha = 0.16f))
                            .clearAndSetSemantics { contentDescription = trf("Progression : %s pour cent", pct) }
                    ) {
                        Box(Modifier.fillMaxWidth(book.progress.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(ink))
                    }
                    Row(
                        Modifier
                            .heightIn(min = 48.dp)
                            .clip(CircleShape)
                            .background(ink)
                            .clickable(onClick = onOpen)
                            .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(OrynIcons.Play, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Reprendre"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.accent, maxLines = 1)
                    }
                }
            }
        }
    }

    BookActionsHost(book, visible = actions, onDismiss = { actions = false })
}
