package com.example.oryn.reader.ui.screen

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.material3.Text
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
        h < 5 -> "Bonne nuit"
        h < 12 -> "Bonjour"
        h < 18 -> "Bon après-midi"
        else -> "Bonsoir"
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
            books.isEmpty() -> "Ajoute ton premier livre avec le bouton +"
            justFinished != null && tip % 3 == 0 -> "Bravo, tu as terminé « ${justFinished.title} » !"
            top != null && tip % 3 == 1 -> "Tu en étais à ${(top.progress * 100).toInt()} % de « ${top.title} »"
            tip % 3 == 2 -> "$done livre${if (done > 1) "s" else ""} terminé${if (done > 1) "s" else ""}, $unread à découvrir"
            else -> "Quelques pages avant de dormir ?"
        }
    }

    LazyColumn(
        modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = navBarClearance + 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ── Coin supérieur : mascotte à gauche, menu « hamburger » à droite
        item(key = "top") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OrynMascot(
                    size = 60.dp,
                    mood = if (bubble) MascotMood.WOW else MascotMood.HAPPY,
                    onClick = { tip += 1 }
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        greeting(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.onBackground
                    )
                    Text(
                        if (books.isEmpty()) "Ta bibliothèque t'attend" else "${books.size} livre${if (books.size > 1) "s" else ""} dans ta bibliothèque",
                        style = MaterialTheme.typography.bodyMedium,
                        color = inkSecondary()
                    )
                }
                Box {
                    Box(
                        Modifier.size(52.dp).solidSurface(CircleShape).clickable { menuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(OrynIcons.Menu, "Menu", tint = inkPrimary(), modifier = Modifier.size(26.dp))
                    }
                    RoundedMenu { DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MenuItem(OrynIcons.Settings, "Réglages") { menuOpen = false; OrynNav.open("settings") }
                        MenuItem(OrynIcons.Globe, "Catalogues en ligne") { menuOpen = false; OrynNav.open("explore") }
                        MenuItem(OrynIcons.Plus, "Importer un livre") { menuOpen = false; onImportClick() }
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
                    title = "Aucun livre",
                    subtitle = "Ajoute un EPUB ou un PDF, ou explore les catalogues libres.",
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
                Shelf(title = "Ajoutés récemment", count = null, onHeader = { OrynNav.openLibrary() }) {
                    ShelfRow(recent, onBookClick)
                }
            }
        }

        // ── Parcourir
        item(key = "browse") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "PARCOURIR",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = inkSecondary(),
                    modifier = Modifier.padding(start = 6.dp)
                )
                val dark = colors.background.luminance() < 0.5f
                val cardShape = RoundedCornerShape(28.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(
                            Brush.verticalGradient(
                                if (dark) listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.05f))
                                else listOf(Color.White.copy(alpha = 0.92f), Color.White.copy(alpha = 0.70f))
                            )
                        )
                        .border(1.dp, if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f), cardShape)
                        .padding(vertical = 4.dp)
                ) {
                    BrowseRow(OrynIcons.Bookmark, "Favoris", favorites.toString(), false) { OrynNav.open("bookmarks") }
                    BrowseRow(OrynIcons.Check, "Terminés", done.toString(), false) { OrynNav.openLibrary("DONE") }
                    BrowseRow(OrynIcons.BookOpen, "Non lus", unread.toString(), false) { OrynNav.openLibrary("UNREAD") }
                    BrowseRow(OrynIcons.Globe, "Catalogues en ligne", null, true) { OrynNav.open("explore") }
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

@Composable
private fun BrowseRow(icon: ImageVector, label: String, count: String?, last: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = accentInk(), modifier = Modifier.size(22.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f).padding(start = 16.dp)
            )
            if (count != null) Text(count, style = MaterialTheme.typography.bodyMedium, color = inkSecondary())
            Icon(OrynIcons.Chevron, null, tint = inkSecondary().copy(alpha = 0.6f), modifier = Modifier.padding(start = 6.dp).size(18.dp))
        }
        if (!last) {
            Box(Modifier.padding(start = 56.dp, end = 18.dp).fillMaxWidth().height(1.dp).background(colors.onSurface.copy(alpha = 0.07f)))
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
            items(reading, key = { it.id }) { book ->
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
 * « Continuer la lecture » : rectangle arrondi (≈ 1,7 : 1), fond bicolore en diagonale (teinte de la couverture),
 * livre 3D incliné qui flotte, titre en empattement (2 lignes max), barre de progression avec le pourcentage
 * à l'intérieur, bouton « Reprendre » large et signet compact (zone tactile de 48 dp). Appui long = infos.
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
    val tint = rememberCoverTint(book) ?: colors.primary
    val haptics = LocalHapticFeedback.current
    var actions by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.dp)
    val base = lerp(colors.surface, tint, 0.16f)
    val diagonal = lerp(colors.surface, tint, 0.34f)
    val pct = (book.progress * 100).toInt()

    // Box racine pour le bouton flottant
    Box(
        modifier.fillMaxWidth().padding(bottom = 20.dp)
    ) {
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 156.dp) // Hauteur minimale, mais s'adapte au contenu
                .clip(shape)
                .background(base)
                .drawBehind {
                    val p = Path().apply {
                        moveTo(size.width * 0.38f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        lineTo(size.width * 0.06f, size.height)
                        close()
                    }
                    drawPath(p, Brush.linearGradient(listOf(diagonal, base), Offset(size.width, 0f), Offset(size.width * 0.2f, size.height)))
                }
                .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
                .combinedClickable(
                    onClickLabel = "Reprendre la lecture",
                    onClick = onOpen,
                    onLongClickLabel = "Afficher les infos du livre",
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions = true
                    }
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "Continuer la lecture : ${book.title}, ${book.author}, $pct pour cent lu"
                }
                // Padding bas augmenté pour laisser de l'espace au bouton
                .padding(start = 6.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Livre 3D : Taille rééquilibrée
            Book3D(book, Modifier.width(100.dp).height(156.dp).offset(y = (-4).dp))
            
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Top
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 34.dp)) {
                        Text(
                            "CONTINUER LA LECTURE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onToggleFavorite),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(if (book.isFavorite) colors.primary.copy(alpha = 0.18f) else colors.onSurface.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (book.isFavorite) OrynIcons.BookmarkFilled else OrynIcons.Bookmark,
                                    contentDescription = if (book.isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                                    tint = if (book.isFavorite) colors.primary else colors.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Text(
                        book.title,
                        fontFamily = FontFamily.Default, 
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        book.author + " · " + relativeTime(book.lastReadTimestamp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Normal,
                        color = inkSecondary(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                // Barre de progression : remontée
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(colors.onSurface.copy(alpha = 0.18f))
                            .clearAndSetSemantics { contentDescription = "Progression : $pct pour cent" }
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(book.progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(colors.primary)
                        )
                    }
                    Text(
                        "$pct %",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                        maxLines = 1
                    )
                }
            }
        }

        // Bouton flottant : centré et ajusté
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(y = 12.dp, x = (-16).dp) // Moins d'offset vertical pour le coller à la carte
                .width(190.dp) // Un peu plus large pour l'équilibre
                .height(44.dp) // 44dp est le sweet spot tactile
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(lerp(colors.primary, Color.White, 0.1f), colors.primary)))
                .border(3.dp, colors.surface, CircleShape) // Bordure plus épaisse pour bien le détacher
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(OrynIcons.Play, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Reprendre",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, 
                    color = Color.White, 
                    maxLines = 1
                )
            }
        }
    }

    BookActionsHost(book, visible = actions, onDismiss = { actions = false })
}
