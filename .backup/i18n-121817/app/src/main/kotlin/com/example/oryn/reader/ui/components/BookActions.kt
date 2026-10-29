package com.example.oryn.reader.ui.components

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
import androidx.compose.material3.Text
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
            title = { Text("Supprimer ce livre ?") },
            text = { Text("« ${book.title} » sera retiré de ta bibliothèque. Tes fichiers importés ne sont pas touchés.") },
            confirmButton = {
                TextButton(onClick = { onDismiss(); viewModel.deleteBook(book) }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
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
                    ActionRow(label = "Infos", onClick = onInfo) {
                        Icon(OrynIcons.Info, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.onSurface.copy(alpha = 0.10f)))
                    ActionRow(label = "Sélectionner", onClick = onSelect) {
                        Icon(OrynIcons.Select, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.onSurface.copy(alpha = 0.10f)))
                    ActionRow(label = "Supprimer de la bibliothèque", onClick = onDelete, danger = true) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.width(128.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BookCover(
                                book,
                                Modifier.width(128.dp).height(192.dp).shadow(14.dp, RoundedCornerShape(14.dp))
                            )
                            CoverAction(OrynIcons.Image, "Changer") { pickCover.launch(arrayOf("image/*")) }
                            if (hasCover) {
                                CoverAction(OrynIcons.Trash, "Supprimer", danger = true) { viewModel.removeCover(book) }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                book.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                book.author,
                                style = MaterialTheme.typography.bodyLarge,
                                color = inkSecondary()
                            )
                            Row(
                                Modifier
                                    .clip(CircleShape)
                                    .background(colors.primary.copy(alpha = 0.14f))
                                    .clickable { editing = true }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(OrynIcons.Edit, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                                Text("Modifier", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = colors.primary)
                            }
                            RatingRow(info)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                GlassTag(book.format)
                                info?.year?.let { GlassTag(it.toString()) }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { book.progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                    color = colors.primary,
                                    trackColor = colors.onSurface.copy(alpha = 0.12f)
                                )
                                Text(
                                    if (book.progress < 0.01f) "Pas encore commencé" else "${(book.progress * 100).toInt()} % lu",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = inkSecondary()
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Description", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        val current = info
                        when {
                            current == null -> Text(
                                "Recherche du résumé…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = inkSecondary()
                            )
                            current.description == null -> Text(
                                "Aucun résumé disponible pour ce livre (ou pas de connexion pour le chercher).",
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
                                        if (expanded) "Réduire" else "Lire la suite",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp)
                                    )
                                }
                                current.descriptionSource?.let {
                                    Text(
                                        "Source : $it",
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.onSurface.copy(alpha = 0.10f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(OrynIcons.Close, "Fermer", tint = colors.onSurface, modifier = Modifier.size(18.dp))
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
        title = { Text("Modifier le livre") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Auteur") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, author) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
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
                rating == null -> "  Pas encore noté"
                else -> "  " + String.format(Locale.FRENCH, "%.1f", rating) +
                    if (info.ratingCount > 0) " · ${info.ratingCount} avis" else ""
            },
            style = MaterialTheme.typography.labelMedium,
            color = inkSecondary()
        )
    }
}
