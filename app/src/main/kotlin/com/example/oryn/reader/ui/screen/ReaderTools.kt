package com.example.oryn.reader.ui.screen

import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.example.oryn.reader.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.oryn.reader.data.importer.TocEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Sommaire du livre : le chapitre en cours est mis en évidence, toucher une ligne y mène. */
@Composable
internal fun TocDialog(
    entries: List<TocEntry>,
    currentOffset: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val currentIndex = remember(entries, currentOffset) {
        entries.indices.filter { entries[it].offset <= currentOffset }.maxByOrNull { entries[it].offset } ?: 0
    }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (entries.isNotEmpty()) listState.scrollToItem((currentIndex - 2).coerceAtLeast(0)) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
            contentColor = colors.onSurface,
            modifier = Modifier.padding(16.dp).fillMaxWidth().heightIn(max = 640.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Sommaire"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (entries.isEmpty()) {
                    Text(
                        tr("Ce livre n'a pas de sommaire et aucun titre n'a pu être repéré dans le texte."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                LazyColumn(Modifier.weight(1f, fill = false), state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(entries) { index, entry ->
                        val current = index == currentIndex
                        Text(
                            entry.title,
                            style = if (entry.level == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                            fontWeight = if (current || entry.level == 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (current) colors.primary else colors.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (current) colors.primary.copy(alpha = 0.12f) else colors.surface)
                                .clickable { onJump(entry.offset) }
                                .heightIn(min = 48.dp)
                                .padding(start = (12 + entry.level.coerceAtMost(4) * 16).dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
                        )
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(tr("Fermer")) }
            }
        }
    }
}

private class SearchHit(val offset: Int, val length: Int, val snippet: androidx.compose.ui.text.AnnotatedString)

/** Recherche dans le texte du livre : liste des occurrences avec leur contexte, toucher pour y aller. */
@Composable
internal fun SearchDialog(
    text: String,
    onJump: (offset: Int, length: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val hits by produceState<List<SearchHit>?>(emptyList(), query) {
        val q = query.trim()
        if (q.length < 2) {
            value = emptyList()
            return@produceState
        }
        value = null // recherche en cours
        delay(250) // attend la fin de la saisie
        value = withContext(Dispatchers.Default) {
            val found = ArrayList<SearchHit>()
            var from = 0
            while (found.size < 200) {
                val i = text.indexOf(q, from, ignoreCase = true)
                if (i < 0) break
                val a = (i - 40).coerceAtLeast(0)
                val b = (i + q.length + 60).coerceAtMost(text.length)
                val snippet = buildAnnotatedString {
                    append((if (a > 0) "…" else "") + text.substring(a, i).replace('\n', ' '))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.primary)) {
                        append(text.substring(i, i + q.length).replace('\n', ' '))
                    }
                    append(text.substring(i + q.length, b).replace('\n', ' ') + (if (b < text.length) "…" else ""))
                }
                found += SearchHit(i, q.length, snippet)
                from = i + q.length
            }
            found
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
            contentColor = colors.onSurface,
            modifier = Modifier.padding(16.dp).fillMaxWidth().heightIn(max = 640.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Rechercher dans le livre"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(tr("Mot ou expression")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
                val list = hits
                val label = when {
                    query.trim().length < 2 -> tr("Saisis au moins 2 lettres.")
                    list == null -> tr("Recherche…")
                    list.isEmpty() -> tr("Aucun résultat.")
                    list.size >= 200 -> tr("200 premiers résultats")
                    else -> trf("%s résultat(s)", list.size)
                }
                Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(list.orEmpty()) { _, hit ->
                        Text(
                            hit.snippet,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.onSurface.copy(alpha = 0.06f))
                                .clickable { onJump(hit.offset, hit.length) }
                                .padding(12.dp)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(tr("Fermer")) }
                }
            }
        }
    }
}
