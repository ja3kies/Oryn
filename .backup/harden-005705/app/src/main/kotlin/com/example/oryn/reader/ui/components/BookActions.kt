package com.example.oryn.reader.ui.components

import com.example.oryn.reader.i18n.Lang
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.example.oryn.reader.data.remote.BookInfoClient
import com.example.oryn.reader.rememberMainViewModel
import java.util.Locale

private enum class Stage { MENU, INFO, CONFIRM }

@Composable
fun BookActionsHost(book: BookEntity, visible: Boolean, onDismiss: () -> Unit, onSelect: () -> Unit = {}) {
    if (!visible) return
    var stage by remember { mutableStateOf(Stage.MENU) }
    val viewModel = rememberMainViewModel()

    when (stage) {
        Stage.MENU -> ContextCard(
            book = book,
            onDismiss = onDismiss,
            onInfo = { stage = Stage.INFO },
            onDelete = { stage = Stage.CONFIRM },
            onSelect = onSelect
        )
        Stage.INFO -> InfoDialog(book = book, onDismiss = onDismiss)
        Stage.CONFIRM -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(tr("Supprimer ce livre ?")) },
            text = { Text(trf("« %s » sera retiré de ta bibliothèque. Tes fichiers importés ne sont pas touchés.", book.title)) },
            confirmButton = {
                TextButton(onClick = { onDismiss(); viewModel.deleteBook(book) }) { Text(tr("Supprimer")) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annuler")) } }
        )
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 15.dp),
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
    val tint = rememberCoverTint(book)
    val outside = noRipple()
    val inside = noRipple()
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.setCover(book, uri)
    }
    val info by produceState<BookInfo?>(initialValue = null, book.id) {
        value = runCatching { BookInfoClient.load(context, book) }.getOrDefault(BookInfo())
    }
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val hasCover = !book.coverPath.isNullOrBlank()

    if (editing) {
        EditInfoDialog(book, onDismiss = { editing = false }) { t, a ->
            viewModel.updateBookInfo(book, t, a)
            editing = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().padding(16.dp).clickable(interactionSource = outside, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .glass(RoundedCornerShape(32.dp), tint, frosted = true)
                    .clickable(interactionSource = inside, indication = null, onClick = {})
            ) {
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
                            CoverAction(OrynIcons.Image, tr("Changer")) { pickCover.launch(arrayOf("image/*")) }
                            if (hasCover) {
                                CoverAction(OrynIcons.Trash, tr("Supprimer"), danger = true) { viewModel.removeCover(book) }
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
                        RatingRow(info)
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
                                .heightIn(min = 44.dp)
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

@Composable
private fun EditInfoDialog(book: BookEntity, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Modifier le livre")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, author) }) { Text(tr("Enregistrer")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annuler")) } }
    )
}

@Composable
private fun CoverAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tone = if (danger) colors.error else colors.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(tone.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = tone, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tone)
    }
}

@Composable
private fun RatingRow(info: BookInfo?) {
    val colors = MaterialTheme.colorScheme
    val amber = Color(0xFFF5A524)
    val rating = info?.rating
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(5) { i ->
            Icon(
                OrynIcons.Star, null,
                tint = if (rating != null && i < Math.round(rating)) amber else colors.onSurface.copy(alpha = 0.18f),
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            when {
                info == null -> "  …"
                rating == null -> tr("  Pas encore noté")
                else -> "  " + String.format(Lang.locale, "%.1f", rating) +
                    if (info.ratingCount > 0) trf(" · %s avis", info.ratingCount) else ""
            },
            style = MaterialTheme.typography.labelMedium,
            color = inkSecondary()
        )
    }
}

/** Un chiffre clé : légende en petites capitales, valeur dessous. */
@Composable
private fun InfoStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = inkSecondary(), maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
