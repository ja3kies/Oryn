package com.example.oryn.reader.ui.components

import com.example.oryn.reader.i18n.Lang
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.luminance
import com.example.oryn.reader.ui.screen.coverFrame
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.example.oryn.reader.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.oryn.reader.data.local.entity.BookEntity
import com.example.oryn.reader.data.remote.BookInfo
import com.example.oryn.reader.data.remote.BookInfoOverrides
import com.example.oryn.reader.data.remote.BookInfoClient
import com.example.oryn.reader.rememberMainViewModel
import java.util.Locale

private enum class Stage { MENU, INFO, CONFIRM }

@Composable
fun BookActionsHost(book: BookEntity, visible: Boolean, onDismiss: () -> Unit, onSelect: () -> Unit = {}) {
    if (!visible) return
    var stage by remember { mutableStateOf(Stage.MENU) }
    when (stage) {
        Stage.MENU -> ContextCard(
            book = book,
            onDismiss = onDismiss,
            onInfo = { stage = Stage.INFO },
            onDelete = { stage = Stage.CONFIRM },
            onSelect = { onDismiss(); onSelect() }
        )
        Stage.INFO -> InfoDialog(book = book, onDismiss = onDismiss)
        Stage.CONFIRM -> {
            val viewModel = rememberMainViewModel()
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(tr("Supprimer ce livre ?")) },
                text = { Text(trf("« %s » sera retiré de ta bibliothèque. Les fichiers externes restent inchangés ; les copies locales inutilisées peuvent être supprimées.", book.title)) },
                confirmButton = {
                    TextButton(onClick = { onDismiss(); viewModel.deleteBook(book) }) { Text(tr("Supprimer")) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annuler")) } }
            )
        }
    }
}

@Composable
private fun noRipple() = remember { MutableInteractionSource() }

@Composable
private fun ContextCard(book: BookEntity, onDismiss: () -> Unit, onInfo: () -> Unit, onDelete: () -> Unit, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = rememberCoverTint(book)
    val outside = noRipple()
    val inside = noRipple()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = outside, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .width(270.dp)
                    .clickable(interactionSource = inside, indication = null, onClick = {}),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BookCover(
                    book,
                    Modifier
                        .width(150.dp)
                        .height(225.dp)
                        .graphicsLayer { rotationZ = -2f }
                        .shadow(22.dp, RoundedCornerShape(14.dp))
                )
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), tint, frosted = true)) {
                    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp)) {
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            book.author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = inkSecondary(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.onSurface.copy(alpha = 0.10f)))
                    ActionRow(label = tr("Infos"), onClick = onInfo) {
                        Icon(OrynIcons.Info, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.onSurface.copy(alpha = 0.10f)))
                    ActionRow(label = tr("Sélectionner"), onClick = onSelect) {
                        Icon(OrynIcons.Select, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.onSurface.copy(alpha = 0.10f)))
                    ActionRow(label = tr("Supprimer de la bibliothèque"), onClick = onDelete, danger = true) {
                        Icon(OrynIcons.Trash, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    icon: @Composable () -> Unit
) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon()
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun InfoDialog(book: BookEntity, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val viewModel = rememberMainViewModel()
    val outside = noRipple()
    val inside = noRipple()
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.setCover(book, uri)
    }
    var metadataRefresh by remember(book.id) { androidx.compose.runtime.mutableIntStateOf(0) }
    val info by produceState<BookInfo?>(initialValue = null, book.id, metadataRefresh) {
        value = runCatching { BookInfoClient.load(context, book) }.getOrDefault(BookInfo())
    }
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmCoverRemoval by remember { mutableStateOf(false) }
    val hasCover = !book.coverPath.isNullOrBlank()

    if (editing) {
        EditInfoDialog(book, info, onDismiss = { editing = false }) { t, a, description, year, publisher, pages ->
            BookInfoOverrides.save(context, book.id, description, year, publisher, pages)
            viewModel.updateBookInfo(book, t, a)
            metadataRefresh++
            editing = false
        }
        return // Never stack the edit dialog underneath the book details dialog.
    }

    if (confirmCoverRemoval) {
        AlertDialog(
            onDismissRequest = { confirmCoverRemoval = false },
            title = { Text(tr("Supprimer la couverture ?")) },
            text = { Text(tr("Tu pourras choisir une autre couverture depuis la fiche du livre.")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeCover(book)
                    confirmCoverRemoval = false
                }) { Text(tr("Supprimer")) }
            },
            dismissButton = { TextButton(onClick = { confirmCoverRemoval = false }) { Text(tr("Annuler")) } }
        )
        return
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().padding(16.dp).clickable(interactionSource = outside, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 680.dp)
                    .clickable(interactionSource = inside, indication = null, onClick = {}),
                shape = RoundedCornerShape(32.dp),
                color = colors.surface,
                contentColor = colors.onSurface,
                shadowElevation = 8.dp
            ) {
                Box {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // En-tête centré : couverture posée, titre, auteur, puis trois chiffres clés
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BookCover(
                            book,
                            Modifier.width(132.dp).height(198.dp).coverFrame()
                        )
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CoverAction(OrynIcons.Image, tr("Changer"), modifier = Modifier.weight(1f)) { pickCover.launch(arrayOf("image/*")) }
                            if (hasCover) {
                                androidx.compose.material3.IconButton(
                                    onClick = { confirmCoverRemoval = true },
                                    modifier = Modifier.size(48.dp)
                                        .clip(CircleShape)
                                        .background(colors.errorContainer)
                                ) {
                                    Icon(
                                        OrynIcons.Trash,
                                        contentDescription = tr("Supprimer la couverture"),
                                        tint = colors.onErrorContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
                        )
                        Text(
                            book.author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = inkSecondary(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        RatingRow(book.id, info)
                        Row(
                            Modifier.padding(top = 10.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InfoStat(tr("Format"), book.format, Modifier.weight(1f))
                            Box(Modifier.width(1.dp).height(30.dp).background(colors.onSurface.copy(alpha = 0.12f)))
                            InfoStat(tr("Progression"), if (book.progress < 0.01f) "0 %" else "${(book.progress * 100).toInt()} %", Modifier.weight(1f))
                            Box(Modifier.width(1.dp).height(30.dp).background(colors.onSurface.copy(alpha = 0.12f)))
                            InfoStat(tr("Année"), info?.year?.toString() ?: "—", Modifier.weight(1f))
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            InfoStat(tr("Pages"), info?.pages?.toString() ?: "—", Modifier.weight(1f))
                            InfoStat(tr("Éditeur"), info?.publisher ?: "—", Modifier.weight(2f))
                        }
                        Text(
                            tr("Données du catalogue ou saisies dans Oryn ; l’édition locale peut différer."),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        LinearProgressIndicator(
                            progress = { book.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(5.dp).clip(CircleShape),
                            color = if (colors.background.luminance() < 0.5f) colors.primaryContainer else colors.primary,
                            trackColor = colors.onSurface.copy(alpha = 0.12f)
                        )
                        Row(
                            Modifier
                                .padding(top = 6.dp)
                                .clip(CircleShape)
                                .background(colors.onSurface.copy(alpha = 0.08f))
                                .clickable { editing = true }
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(OrynIcons.Edit, null, tint = colors.onSurface, modifier = Modifier.size(16.dp))
                            Text(tr("Modifier"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr("Description"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        val current = info
                        when {
                            current == null -> Text(
                                tr("Recherche du résumé…"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = inkSecondary()
                            )
                            current.description == null -> Text(
                                tr("Aucun résumé disponible pour ce livre (ou pas de connexion pour le chercher)."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = inkSecondary()
                            )
                            else -> {
                                val desc: String = current.description ?: ""
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = if (expanded) Int.MAX_VALUE else 7,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (desc.length > 280) {
                                    Text(
                                        if (expanded) tr("Réduire") else tr("Lire la suite"),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp)
                                    )
                                }
                                current.descriptionSource?.let {
                                    Text(
                                        trf("Source : %s", it),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = inkSecondary()
                                    )
                                }
                            }
                        }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.onSurface.copy(alpha = 0.10f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(OrynIcons.Close, tr("Fermer"), tint = colors.onSurface, modifier = Modifier.size(18.dp))
                }
                }
            }
        }
    }
}

@Composable
private fun EditInfoDialog(
    book: BookEntity,
    info: BookInfo?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int?, String, Int?) -> Unit
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author) }
    var description by remember(book.id, info?.description) { mutableStateOf(info?.description.orEmpty()) }
    var year by remember(book.id, info?.year) { mutableStateOf(info?.year?.toString().orEmpty()) }
    var publisher by remember(book.id, info?.publisher) { mutableStateOf(info?.publisher.orEmpty()) }
    var pages by remember(book.id, info?.pages) { mutableStateOf(info?.pages?.toString().orEmpty()) }
    val parsedYear = year.trim().toIntOrNull()
    val yearError = year.isNotBlank() && (parsedYear == null || parsedYear !in 1..2100)
    val parsedPages = pages.trim().toIntOrNull()
    val pagesError = pages.isNotBlank() && (parsedPages == null || parsedPages !in 1..200_000)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Modifier les informations")) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(tr("Titre")) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(tr("Auteur")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text(tr("Année de publication")) },
                    isError = yearError,
                    supportingText = if (yearError) ({ Text(tr("Année invalide")) }) else null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = publisher,
                    onValueChange = { publisher = it.take(160) },
                    label = { Text(tr("Éditeur")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pages,
                    onValueChange = { pages = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text(tr("Nombre de pages")) },
                    isError = pagesError,
                    supportingText = if (pagesError) ({ Text(tr("Nombre de pages invalide")) }) else null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(12_000) },
                    label = { Text(tr("Description du livre")) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !yearError && !pagesError && title.isNotBlank() && author.isNotBlank(),
                onClick = { onSave(title.trim(), author.trim(), description, parsedYear, publisher, parsedPages) }
            ) { Text(tr("Enregistrer")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annuler")) } }
    )
}

@Composable
private fun CoverAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tone = if (danger) colors.error else colors.onSurface
    Row(
        modifier
            .clip(CircleShape)
            .background(tone.copy(alpha = 0.10f))
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = tone, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tone)
    }
}

/** The reader's personal rating is local and editable; online community ratings are read-only. */
@Composable
private fun RatingRow(bookId: Long, info: BookInfo?) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("oryn_book_ratings", android.content.Context.MODE_PRIVATE) }
    var personalRating by remember(bookId) { androidx.compose.runtime.mutableIntStateOf(prefs.getInt("book_$bookId", 0).coerceIn(0, 5)) }
    val starColor = if (colors.background.luminance() < 0.5f) Color(0xFFFFD27C) else Color(0xFF935000)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            if (personalRating > 0) trf("Ma note : %s/5", personalRating) else tr("Ma note : non noté"),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            (1..5).forEach { score ->
                androidx.compose.material3.IconToggleButton(
                    modifier = Modifier.size(48.dp),
                    checked = personalRating == score,
                    onCheckedChange = { checked ->
                        val next = if (checked) score else 0
                        personalRating = next
                        prefs.edit().putInt("book_$bookId", next).apply()
                    }
                ) {
                    Icon(
                        OrynIcons.Star,
                        contentDescription = if (personalRating == score) trf("Effacer ma note de %s étoiles", score) else trf("Noter %s étoiles", score),
                        tint = if (score <= personalRating) starColor else colors.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
        if (info?.rating != null && info.ratingCount > 0) {
            Text(
                trf("Note Open Library : %s/5 · %s avis",
                    String.format(Lang.locale, "%.1f", info.rating), info.ratingCount),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

/** Un chiffre clé : légende en petites capitales, valeur dessous. */
@Composable
private fun InfoStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = inkSecondary(), maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
