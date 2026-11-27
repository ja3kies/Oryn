package com.example.oryn.reader.ui.screen

import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale
import com.example.oryn.reader.i18n.AppLanguage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
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
data class LookupRequest(val mode: LookupMode, val text: String, val excerpt: String = "")

private sealed interface Load<out T> {
    object Loading : Load<Nothing>
    object Quota : Load<Nothing>
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
    var mode by rememberSaveable(request.text, request.mode) { mutableStateOf(request.mode) }
    val suggested = remember(request) { LookupClient.guessLanguage(request.excerpt) ?: LookupClient.guessLanguage(request.text) }
    var source by rememberSaveable(request.text, request.excerpt) { mutableStateOf(suggested.orEmpty()) }
    val target = when (Lang.mode) {
        AppLanguage.FRENCH -> "fr"
        AppLanguage.ENGLISH -> "en"
        AppLanguage.SYSTEM -> LocalConfiguration.current.locales[0].language
    }
    val targetName = Locale.forLanguageTag(target).getDisplayLanguage(Lang.locale)
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
            var expanded by rememberSaveable(request.text) { mutableStateOf(false) }
            val longPassage = request.text.length > 160 || request.text.count { it == '\n' } >= 3
            Text(request.text.trim(), style = if (request.text.length < 45) MaterialTheme.typography.headlineMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif) else MaterialTheme.typography.bodyLarge,
                maxLines = if (longPassage && !expanded) 3 else Int.MAX_VALUE,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (longPassage) androidx.compose.material3.TextButton(onClick = { expanded = !expanded }) {
                Text(tr(if (expanded) "Réduire le passage" else "Lire tout le passage"))
            }
            if (mode == LookupMode.TRANSLATE) LanguageChoice(tr("Langue du texte"), source) { source = it }
            if (mode == LookupMode.TRANSLATE) Text(tr("Traduire vers") + " · " + targetName, style = MaterialTheme.typography.labelLarge)
            key(mode, source, target, request.text) {
                if (source.isEmpty() && mode == LookupMode.TRANSLATE) {
                    StatusLine(tr("Choisis la langue du passage pour continuer."))
                } else when (mode) {
                    LookupMode.DEFINE -> DefineBody(request, source, target)
                    LookupMode.TRANSLATE -> TranslateBody(request.text, source, target)
                }
            }
            androidx.compose.material3.TextButton(onClick = {
                mode = if (mode == LookupMode.DEFINE) LookupMode.TRANSLATE else LookupMode.DEFINE
            }) { Text(tr(if (mode == LookupMode.DEFINE) "Traduire" else "Définir")) }
        }
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
                    .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onAction)
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
private fun LanguageChoice(label: String, code: String, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        androidx.compose.material3.OutlinedButton(onClick = { expanded = true }) {
            Text("$label · ${LookupClient.targetLanguages.firstOrNull { it.first == code }?.second ?: tr("Choisir")}")
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LookupClient.targetLanguages.forEach { (value, name) ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(name) }, onClick = { change(value); expanded = false })
            }
        }
    }
}

@Composable
private fun DefineBody(request: LookupRequest, language: String, target: String) {
    val text = request.text
    if (LookupClient.wordCount(text) > 3) {
        StatusLine(
            tr("Sélectionne un seul mot pour voir sa définition.")
        )
        return
    }
    var selected by remember(request) { mutableStateOf(language) }
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<Load<DictionaryResult>>(Load.Loading, text, selected, target, attempt) {
        value = Load.Loading
        value = try {
            Load.Done(LookupClient.define(text, selected, target))
        } catch (e: CancellationException) {
            throw e
        } catch (e: LookupClient.QuotaExceeded) {
            Load.Quota
        } catch (e: IOException) {
            Load.Offline
        } catch (e: Exception) {
            Load.Offline
        }
    }
    when (val s = state) {
        is Load.Loading -> LoadingRow()
        Load.Quota -> StatusLine(tr("Limite du service atteinte. Réessaie plus tard."))
        is Load.Offline -> StatusLine(tr("Service inaccessible. Vérifie ta connexion ou réessaie."), tr("Réessayer")) { attempt++ }
        is Load.Done -> {
            val result = s.value
            if (result == null) {
                StatusLine(
                    trf("Aucune définition trouvée pour « %s ».", LookupClient.cleanWord(text))
                )
            } else {
                DefinitionContent(result, request.excerpt) { selected = it }
            }
        }
    }
}

@Composable
internal fun DefinitionContent(result: DictionaryResult, excerpt: String, choose: (String) -> Unit) {
    val senses = result.entries.flatMap { entry -> entry.senses.map { entry.partOfSpeech to it } }
    var all by rememberSaveable(result.word, result.language) { mutableStateOf(false) }
    var languages by remember { mutableStateOf(false) }
    val language = Locale.forLanguageTag(result.language).getDisplayLanguage(Lang.locale)
    val metadata = listOfNotNull(language, senses.firstOrNull()?.first?.takeIf(String::isNotBlank), result.phonetic)
        .joinToString(" · ")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (result.availableLanguages.size > 1) {
            Box {
                androidx.compose.material3.TextButton(onClick = { languages = true }) {
                    Text(metadata)
                    Icon(androidx.compose.material.icons.Icons.Default.ArrowDropDown, contentDescription = tr("Langue du mot"))
                }
                androidx.compose.material3.DropdownMenu(languages, { languages = false }) {
                    result.availableLanguages.forEach { code ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(Locale.forLanguageTag(code).getDisplayLanguage(Lang.locale)) },
                            onClick = { languages = false; choose(code) })
                    }
                }
            }
        } else Text(metadata, style = MaterialTheme.typography.labelLarge, color = inkSecondary())
        (if (all) senses else senses.take(1)).forEachIndexed { index, (part, sense) ->
            if (index > 0) Text("${index + 1} · $part", style = MaterialTheme.typography.labelMedium, color = inkSecondary())
            SelectionContainer { Text(sense.definition, style = MaterialTheme.typography.bodyLarge) }
            sense.example?.takeIf(String::isNotBlank)?.let {
                Text("« $it »", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = inkSecondary())
            }
        }
        if (senses.firstOrNull()?.second?.example.isNullOrBlank() && excerpt.isNotBlank()) {
            Text(tr("Dans ton livre"), style = MaterialTheme.typography.labelMedium, color = inkSecondary())
            Text(excerpt, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
        }
        if (senses.size > 1) androidx.compose.material3.TextButton(onClick = { all = !all }) {
            Text(tr(if (all) "Masquer les autres sens" else "Voir les autres sens"))
        }
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        val nativeFrench = result.source.startsWith("Wiktionnaire")
        val host = if (nativeFrench) "fr.wiktionary.org" else "en.wiktionary.org"
        Text(result.source + (if (result.translated) " · MyMemory" else ""),
            modifier = Modifier.clickable(role = Role.Button) { uriHandler.openUri("https://$host/wiki/${android.net.Uri.encode(result.word)}") }.heightIn(min = 48.dp).padding(vertical = 12.dp), style = MaterialTheme.typography.labelSmall, color = inkSecondary())
    }
}

@Composable
private fun TranslateBody(text: String, source: String, target: String) {
    if (source == target) {
        StatusLine(trf("Ce passage est déjà en %s.", LookupClient.targetLanguages.first { it.first == source }.second))
        return
    }
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    if (attempt == 0) {
        StatusLine(tr("MyMemory recevra uniquement le texte sélectionné."), tr("Traduire le passage")) { attempt++ }
        return
    }
    val state by produceState<Load<TranslationResult>>(Load.Loading, attempt) {
        value = Load.Loading
        value = try {
            Load.Done(LookupClient.translate(text, source, target))
        } catch (e: CancellationException) { throw e }
        catch (e: LookupClient.QuotaExceeded) { Load.Quota }
        catch (e: Exception) { Load.Offline }
    }
    when (val result = state) {
        Load.Loading -> LoadingRow()
        Load.Quota -> StatusLine(tr("Limite du service atteinte. Réessaie plus tard."))
        Load.Offline -> StatusLine(tr("Service inaccessible. Vérifie ta connexion ou réessaie."), tr("Réessayer")) { attempt++ }
        is Load.Done -> result.value?.let { translation ->
            val clipboard = LocalClipboardManager.current
            var copied by remember { mutableStateOf(false) }
            SelectionContainer { Text(translation.text, style = MaterialTheme.typography.titleMedium) }
            StatusLine("MyMemory", if (copied) tr("Copié") else tr("Copier la traduction")) {
                clipboard.setText(AnnotatedString(translation.text))
                copied = true
            }
        }
    }
}
