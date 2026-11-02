package com.example.oryn.reader.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.oryn.reader.community.CommunityBook
import com.example.oryn.reader.community.CommunityCatalogState
import com.example.oryn.reader.community.CommunityCatalogViewModel
import com.example.oryn.reader.community.CommunityConfiguration
import com.example.oryn.reader.community.CommunityDetailState
import com.example.oryn.reader.community.CommunityDevelopmentCatalog
import com.example.oryn.reader.community.CommunityOrigin
import com.example.oryn.reader.community.CommunitySimulationScenario
import com.example.oryn.reader.community.CommunitySort
import com.example.oryn.reader.community.RemoteCommunityCatalogRepository
import com.example.oryn.reader.community.SupabaseCommunityCatalogApi
import com.example.oryn.reader.community.auth.CommunityAuthRuntime
import com.example.oryn.reader.i18n.Text
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import com.example.oryn.reader.ui.components.GlassChip
import com.example.oryn.reader.ui.theme.OrynShape
import com.example.oryn.reader.ui.theme.OrynSpace

@Composable
fun CommunityCatalogSection() {
    val context = LocalContext.current
    val runtime = remember(context.applicationContext) { CommunityAuthRuntime.get(context.applicationContext) }
    val remote = remember(runtime) {
        val config = CommunityConfiguration.fromBuild()
        RemoteCommunityCatalogRepository(config, runtime.controller, SupabaseCommunityCatalogApi(config))
    }
    var demonstration by rememberSaveable { mutableStateOf(false) }
    var scenario by rememberSaveable { mutableStateOf(CommunitySimulationScenario.Content) }
    val repository = remember(demonstration, scenario, remote) {
        if (demonstration && CommunityDevelopmentCatalog.available) CommunityDevelopmentCatalog.repository(scenario) ?: remote else remote
    }
    val model = viewModel<CommunityCatalogViewModel> { CommunityCatalogViewModel(repository) }
    LaunchedEffect(repository) { model.setRepository(repository) }
    val state by model.state.collectAsState()
    var shareOpen by remember(repository) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(OrynSpace.md)) {
        if (CommunityDevelopmentCatalog.available) {
            OutlinedButton(onClick = { demonstration = !demonstration }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(tr(if (demonstration) "Quitter la démonstration" else "Explorer la démonstration · développement"))
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(OrynShape.card), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(OrynSpace.md), verticalArrangement = Arrangement.spacedBy(OrynSpace.sm)) {
                Text(tr("Communauté"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                if (state.origin == CommunityOrigin.Simulation) {
                    CommunityDemoNotice()
                    Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(OrynSpace.xs)) {
                        CommunitySimulationScenario.entries.forEach { value ->
                            GlassChip(tr(scenarioLabel(value)), scenario == value, { scenario = value },
                                Modifier.semantics { selected = scenario == value; role = Role.RadioButton })
                        }
                    }
                }
                OutlinedTextField(
                    value = state.query.text, onValueChange = model::setText,
                    label = { Text(tr("Rechercher dans la communauté")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(OrynShape.control)
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(OrynSpace.xs)) {
                    listOf(null to "Toutes les langues", "fr" to "Français", "en" to "English").forEach { (code, label) ->
                        FilterChip(selected = state.query.language == code, onClick = { model.setLanguage(code) },
                            label = { Text(tr(label)) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(OrynSpace.xs)) {
                    CommunitySort.entries.forEach { sort ->
                        FilterChip(selected = state.query.sort == sort, onClick = { model.setSort(sort) },
                            label = { Text(tr(if (sort == CommunitySort.TitleAscending) "Titre croissant" else "Titre décroissant")) },
                            modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                if (state.query.text.isNotBlank() || state.query.language != null || state.query.sort != CommunitySort.TitleAscending) {
                    TextButton(onClick = model::resetFilters, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Réinitialiser les filtres")) }
                }
                CommunityCatalogStatus(state.result, model::refresh)
                val content = state.result as? CommunityCatalogState.Content
                if (content != null) {
                    content.books.forEach { book -> CommunityBookRow(book) { model.openBook(book.id) } }
                }
                if (content != null || (state.result == CommunityCatalogState.Empty && state.query.page > 1)) {
                    Text(trf("Page %s", state.query.page), style = MaterialTheme.typography.bodyMedium)
                    if (state.loadingMore) {
                        Text(tr("Chargement de la page…"), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        CircularProgressIndicator()
                    }
                    state.appendFailure?.let { failure -> CommunityCatalogStatus(failure, null) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OrynSpace.xs)) {
                        OutlinedButton(onClick = model::previousPage, enabled = state.query.page > 1 && !state.loadingMore,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(tr("Page précédente")) }
                        OutlinedButton(onClick = model::nextPage, enabled = content?.nextPage != null && !state.loadingMore,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(tr("Page suivante")) }
                    }
                }
                Text(tr("Aucun partage automatique. Chaque EPUB nécessitera ton consentement explicite."), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { shareOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(tr("Préparer le partage d’un EPUB"))
                }
            }
        }
        // Demonstration never changes the real account or simulates a successful sign-in.
        CommunityAuthSection(runtime)
    }
    if (state.detail != CommunityDetailState.Closed) {
        CommunityBookDetailDialog(state.detail, model::closeDetail, model::retryDetails)
    }
    if (shareOpen) CommunityShareDialog(onClose = { shareOpen = false })
}

@Composable
internal fun CommunityDemoNotice() {
    Text(tr("Démonstration · données simulées, aucun EPUB réellement publié."),
        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

internal fun communityBookTitle(book: CommunityBook): String = tr(book.title)

@Composable
private fun CommunityBookRow(book: CommunityBook, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(OrynShape.medium), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(OrynSpace.md), verticalArrangement = Arrangement.spacedBy(OrynSpace.xxs)) {
            if (book.origin == CommunityOrigin.Simulation) Text(tr("Exemple simulé · aucun fichier"), style = MaterialTheme.typography.labelMedium)
            Text(communityBookTitle(book), style = MaterialTheme.typography.titleMedium)
            Text(book.author, style = MaterialTheme.typography.bodyMedium)
            Text(trf("Langue : %s", book.language), style = MaterialTheme.typography.bodySmall)
            Text(tr("Consulter la fiche"), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun CommunityCatalogStatus(state: CommunityCatalogState, onRetry: (() -> Unit)?) {
    val message = when (state) {
        CommunityCatalogState.Unavailable -> "Le catalogue communautaire n’est pas encore disponible. Ta bibliothèque reste accessible hors ligne."
        CommunityCatalogState.SignInRequired -> "Une connexion à ton compte sera nécessaire pour consulter la communauté."
        CommunityCatalogState.Loading -> "Chargement du catalogue communautaire…"
        CommunityCatalogState.Empty -> "Aucun résultat pour cette recherche."
        CommunityCatalogState.Offline -> "Connexion réseau indisponible. Réessaie quand tu seras en ligne."
        CommunityCatalogState.Error -> "Impossible de charger le catalogue communautaire. Réessaie plus tard."
        is CommunityCatalogState.Content -> null
    }
    message?.let { Text(tr(it), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    if (state == CommunityCatalogState.Loading) CircularProgressIndicator()
    if (onRetry != null && (state == CommunityCatalogState.Error || state == CommunityCatalogState.Offline)) {
        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Réessayer")) }
    }
}

private fun scenarioLabel(value: CommunitySimulationScenario) = when (value) {
    CommunitySimulationScenario.Content -> "Exemples"
    CommunitySimulationScenario.Loading -> "Chargement"
    CommunitySimulationScenario.Empty -> "Vide"
    CommunitySimulationScenario.Error -> "Erreur"
    CommunitySimulationScenario.Offline -> "Hors ligne"
    CommunitySimulationScenario.SignInRequired -> "Connexion requise"
    CommunitySimulationScenario.Unavailable -> "Indisponible"
}
