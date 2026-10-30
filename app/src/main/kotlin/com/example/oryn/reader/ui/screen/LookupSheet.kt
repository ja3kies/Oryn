package com.example.oryn.reader.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.oryn.reader.data.remote.DictionaryResult
import com.example.oryn.reader.data.remote.LookupClient
import com.example.oryn.reader.data.remote.TranslationResult
import com.example.oryn.reader.i18n.Lang
import com.example.oryn.reader.i18n.Text
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import com.example.oryn.reader.ui.components.OrynIcons
import com.example.oryn.reader.ui.components.inkSecondary
import com.example.oryn.reader.ui.theme.accent
import com.example.oryn.reader.ui.theme.onAccent
import kotlinx.coroutines.CancellationException
import java.io.IOException

enum class LookupMode { DEFINE, TRANSLATE }

/** Demande de recherche depuis le lecteur : le mode demandé et le texte sélectionné. */
data class LookupRequest(val mode: LookupMode, val text: String)

private sealed interface Load<out T> {
    object Loading : Load<Nothing>
    object Offline : Load<Nothing>
    class Done<T>(val value: T?) : Load<T>
}

/**
 * Fiche du bas d'écran ouverte par « Définir » / « Traduire » : deux onglets (Définition, Traduction),
 * chargement visible, message clair hors connexion, et langue cible au choix.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupSheet(request: LookupRequest, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    var mode by remember(request) { mutableStateOf(request.mode) }
    var target by remember { mutableStateOf(if (Lang.isEnglish) "en" else "fr") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = if (dark) colors.surfaceContainerHigh else colors.surfaceContainerLowest
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Le passage sélectionné, en citation courte
            Text(
                "« " + request.text.trim() + " »",
                style = MaterialTheme.typography.bodyMedium,
                color = inkSecondary(),
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LookupTab(tr("Définition"), mode == LookupMode.DEFINE) { mode = LookupMode.DEFINE }
                LookupTab(tr("Traduction"), mode == LookupMode.TRANSLATE) { mode = LookupMode.TRANSLATE }
            }
            when (mode) {
                LookupMode.DEFINE -> DefineBody(request.text, onTranslateInstead = { mode = LookupMode.TRANSLATE })
                LookupMode.TRANSLATE -> TranslateBody(request.text, target) { target = it }
            }
        }
    }
}

@Composable
private fun LookupTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.onAccent else colors.onSurface
        )
    }
}

@Composable
private fun StatusLine(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = inkSecondary())
        if (actionLabel != null) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(colors.onSurface.copy(alpha = 0.08f))
                    .clickable(onClick = onAction)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
        Text(tr("Chargement…"), style = MaterialTheme.typography.bodyMedium, color = inkSecondary())
    }
}

@Composable
private fun DefineBody(text: String, onTranslateInstead: () -> Unit) {
    if (LookupClient.wordCount(text) > 3) {
        StatusLine(
            tr("Sélectionne un seul mot pour voir sa définition."),
            tr("Traduire ce passage"),
            onTranslateInstead
        )
        return
    }
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<Load<DictionaryResult>>(Load.Loading, text, attempt) {
        value = Load.Loading
        val languages = if (Lang.isEnglish) listOf("en", "fr") else listOf("fr", "en")
        value = try {
            Load.Done(LookupClient.define(text, languages))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Load.Offline
        } catch (e: Exception) {
            Load.Done(null)
        }
    }
    when (val s = state) {
        is Load.Loading -> LoadingRow()
        is Load.Offline -> StatusLine(tr("Pas de connexion : impossible de joindre le service."), tr("Réessayer")) { attempt++ }
        is Load.Done -> {
            val result = s.value
            if (result == null) {
                StatusLine(
                    trf("Aucune définition trouvée pour « %s ».", LookupClient.cleanWord(text)),
                    tr("Traduire ce passage"),
                    onTranslateInstead
                )
            } else {
                DefinitionContent(result)
            }
        }
    }
}

@Composable
private fun DefinitionContent(result: DictionaryResult) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(result.word, style = MaterialTheme.typography.headlineSmall)
            result.phonetic?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = inkSecondary()) }
        }
        result.entries.forEach { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.partOfSpeech.uppercase(), style = MaterialTheme.typography.labelSmall, color = inkSecondary())
                entry.senses.forEachIndexed { index, sense ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = inkSecondary(),
                            modifier = Modifier.width(22.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(sense.definition, style = MaterialTheme.typography.bodyMedium)
                            sense.example?.let {
                                Text("« $it »", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = inkSecondary())
                            }
                        }
                    }
                }
            }
        }
        Text(trf("Source : %s. Le mot sélectionné est envoyé à ce service.", result.source), style = MaterialTheme.typography.labelSmall, color = inkSecondary())
    }
}

@Composable
private fun TranslateBody(text: String, target: String, onTarget: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(tr("Traduire vers").uppercase(), style = MaterialTheme.typography.labelSmall, color = inkSecondary())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LookupClient.targetLanguages.forEach { (code, name) ->
                val selected = code == target
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (selected) colors.accent else colors.onSurface.copy(alpha = 0.08f))
                        .clickable { onTarget(code) }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) colors.onAccent else colors.onSurface
                    )
                }
            }
        }

        var attempt by remember { mutableIntStateOf(0) }
        val state by produceState<Load<TranslationResult>>(Load.Loading, text, target, attempt) {
            value = Load.Loading
            value = try {
                Load.Done(LookupClient.translate(text, target))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Load.Offline
            } catch (e: Exception) {
                Load.Done(null)
            }
        }
        when (val s = state) {
            is Load.Loading -> LoadingRow()
            is Load.Offline -> StatusLine(tr("Pas de connexion : impossible de joindre le service."), tr("Réessayer")) { attempt++ }
            is Load.Done -> {
                val result = s.value
                if (result == null) {
                    StatusLine(tr("Traduction indisponible pour ce passage."), tr("Réessayer")) { attempt++ }
                } else {
                    TranslationContent(result, target)
                }
            }
        }
    }
}

@Composable
private fun TranslationContent(result: TranslationResult, target: String) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val targetName = LookupClient.targetLanguages.firstOrNull { it.first == target }?.second ?: target
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (result.sameLanguage) {
            Text(trf("Ce passage est déjà en %s.", targetName), style = MaterialTheme.typography.bodyMedium, color = inkSecondary())
        } else {
            Text(result.text, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(colors.onSurface.copy(alpha = 0.08f))
                        .clickable {
                            clipboard.setText(AnnotatedString(result.text))
                            Toast.makeText(context, tr("Copié"), Toast.LENGTH_SHORT).show()
                        }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(OrynIcons.Pages, null, tint = colors.onSurface, modifier = Modifier.size(18.dp))
                    Text(tr("Copier"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                Text(tr("Source : MyMemory. Le texte sélectionné est envoyé à ce service."), style = MaterialTheme.typography.labelSmall, color = inkSecondary())
            }
        }
        if (result.truncated) {
            Text(
                tr("Passage coupé : seuls les premiers caractères sont traduits."),
                style = MaterialTheme.typography.labelSmall,
                color = inkSecondary()
            )
        }
    }
}
