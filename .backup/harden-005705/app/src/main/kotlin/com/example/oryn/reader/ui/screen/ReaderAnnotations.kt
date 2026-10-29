package com.example.oryn.reader.ui.screen

import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.example.oryn.reader.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.oryn.reader.ui.components.OrynIcons
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.oryn.reader.data.local.entity.AnnotationEntity
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Chartreuse, beurre, corail et pêche : quatre surlignages vifs, lisibles sous le texte. */
internal val HighlightColors = listOf(
    Color(0xFFC8F135), Color(0xFFFFE04A), Color(0xFFFF8F72), Color(0xFFFFC9A8)
)

/**
 * Menu flottant de sélection (sombre) : en haut les quatre couleurs de surlignage et la note, puis une rangée
 * d'actions (copier, souligner, étendre, définir, traduire) avec légende, et « Supprimer » seul en bas s'il existe.
 * Trois niveaux seulement, groupés par proximité : moins de choix à la fois (loi de Hick).
 */
@Composable
internal fun SelectionBar(
    existing: AnnotationEntity?,
    onColor: (Int) -> Unit,
    onUnderline: () -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    onDelete: (() -> Unit)?,
    onDefine: (() -> Unit)?,
    onTranslate: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onExtend: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val ink = Color(0xFFF2F2EF)
    Surface(
        modifier = modifier.width(272.dp),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF0E0E0E),
        contentColor = ink,
        shadowElevation = 14.dp
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HighlightColors.forEachIndexed { i, c ->
                    val selected = existing != null && existing.kind == "HIGHLIGHT" && existing.color == i
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = tr("Surligner"), onClick = { onColor(i) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                BarAction(OrynIcons.Edit, tr("Note"), false, ink, onNote, Modifier.width(60.dp))
            }
            Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.10f)))
            Row(Modifier.fillMaxWidth()) {
                BarAction(OrynIcons.Pages, tr("Copier"), false, ink, onCopy, Modifier.weight(1f))
                BarAction(OrynIcons.Highlighter, tr("Souligner"), existing != null && existing.kind == "UNDERLINE", ink, onUnderline, Modifier.weight(1f))
                onExtend?.let { BarAction(OrynIcons.Select, tr("Étendre"), false, ink, it, Modifier.weight(1f)) }
                onDefine?.let { BarAction(OrynIcons.BookOpen, tr("Définir"), false, ink, it, Modifier.weight(1f)) }
                onTranslate?.let { BarAction(OrynIcons.Globe, tr("Traduire"), false, ink, it, Modifier.weight(1f)) }
            }
            onDelete?.let {
                Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.10f)))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = it)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(OrynIcons.Trash, null, tint = Color(0xFFFF8F72), modifier = Modifier.size(20.dp))
                    Text(tr("Supprimer"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF8F72))
                }
            }
        }
    }
}

/** Action à icône et légende (zone tactile ≥ 48 dp) ; active = accent. */
@Composable
private fun BarAction(icon: ImageVector, label: String, active: Boolean, ink: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) colors.primaryContainer else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, tint = if (active) colors.onPrimaryContainer else ink, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) colors.onPrimaryContainer else ink.copy(alpha = 0.82f), maxLines = 1)
    }
}

@Composable
internal fun NoteDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Note")) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 6
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }) { Text(tr("Enregistrer")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annuler")) } }
    )
}

/**
 * Poignée de sélection : se glisse pour étendre ou réduire le passage (mot par mot, comme sur le web).
 * [anchor] est en pixels, dans le repère du texte. Zone tactile de 48 dp ; le geste est capté dès l'appui
 * pour que le tournage de page ne le récupère pas.
 */
@Composable
internal fun SelectionHandle(anchor: Offset, color: Color, onDrag: (Offset) -> Unit, onDone: () -> Unit) {
    val currentAnchor by rememberUpdatedState(anchor)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDone by rememberUpdatedState(onDone)
    val half = with(LocalDensity.current) { 24.dp.toPx() }
    val lift = with(LocalDensity.current) { 6.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset((anchor.x - half).roundToInt(), (anchor.y - lift).roundToInt()) }
            .size(48.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var pos = currentAnchor
                    drag(down.id) { change ->
                        change.consume()
                        pos += change.positionChange()
                        // on vise la ligne juste au-dessus du doigt
                        currentDrag(Offset(pos.x, pos.y - 30f))
                    }
                    currentDone()
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
        )
    }
}

/** Liste de toutes les annotations du livre : toucher pour y aller, exporter en texte. */
@Composable
internal fun AnnotationsDialog(
    title: String,
    author: String,
    annotations: List<AnnotationEntity>,
    onJump: (Int) -> Unit,
    onDelete: (AnnotationEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
            contentColor = colors.onSurface,
            modifier = Modifier.padding(16.dp).fillMaxWidth().heightIn(max = 640.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        trf("Annotations (%s)", annotations.size),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (annotations.isNotEmpty()) {
                        TextButton(onClick = {
                            val text = buildString {
                                appendLine("$title — $author")
                                appendLine()
                                for (a in annotations) {
                                    appendLine("« ${a.quote} »")
                                    a.note?.takeIf { it.isNotBlank() }?.let { appendLine("→ $it") }
                                    appendLine()
                                }
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) { Text(tr("Exporter")) }
                    }
                }
                if (annotations.isEmpty()) {
                    Text(
                        tr("Aucune annotation. Appuie longuement sur un mot dans le livre pour le sélectionner, puis surligne-le, souligne-le ou ajoute une note."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                LazyColumn(
                    Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(annotations, key = { it.id }) { a ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.onSurface.copy(alpha = 0.06f))
                                .clickable { onJump(a.startOffset) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .width(6.dp)
                                    .fillMaxHeight()
                                    .background(if (a.kind == "HIGHLIGHT") HighlightColors[a.color.coerceIn(0, 3)] else colors.onSurface.copy(alpha = 0.5f))
                            )
                            Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    a.quote,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                                a.note?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = colors.primary)
                                }
                                Text(
                                    dateFormat.format(Date(a.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            Text(
                                "✕",
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.clickable { onDelete(a) }.padding(14.dp)
                            )
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(tr("Fermer")) }
            }
        }
    }
}
