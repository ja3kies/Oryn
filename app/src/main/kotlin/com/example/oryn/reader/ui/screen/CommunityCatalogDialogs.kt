package com.example.oryn.reader.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.oryn.reader.community.CommunityBookDetails
import com.example.oryn.reader.community.CommunityDetailState
import com.example.oryn.reader.community.CommunityOrigin
import com.example.oryn.reader.community.CommunityPreparation
import com.example.oryn.reader.community.CommunityPreparationIssue
import com.example.oryn.reader.community.CommunityShareConsent
import com.example.oryn.reader.community.CommunityShareDraft
import com.example.oryn.reader.community.CommunityTransfers
import com.example.oryn.reader.i18n.Text
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import com.example.oryn.reader.ui.theme.OrynSpace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
internal fun CommunityBookDetailDialog(state: CommunityDetailState, onClose: () -> Unit, onRetry: () -> Unit) {
    val details = (state as? CommunityDetailState.Content)?.details
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(details?.book?.let(::communityBookTitle) ?: tr("Fiche EPUB")) },
        text = {
            Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.6f).dp)
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(OrynSpace.sm)) {
                if (details != null) CommunityBookDetailContent(details) else {
                    val message = when (state) {
                        CommunityDetailState.Loading -> "Chargement de la fiche…"
                        CommunityDetailState.NotFound -> "Cette fiche n’est plus disponible."
                        CommunityDetailState.SignInRequired -> "Une connexion à ton compte sera nécessaire pour consulter la communauté."
                        CommunityDetailState.Offline -> "Connexion réseau indisponible. Réessaie quand tu seras en ligne."
                        CommunityDetailState.Error -> "Impossible de charger cette fiche."
                        else -> "Le catalogue communautaire n’est pas encore disponible. Ta bibliothèque reste accessible hors ligne."
                    }
                    Text(tr(message), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    if (state == CommunityDetailState.Loading) CircularProgressIndicator()
                    if (state == CommunityDetailState.Offline || state == CommunityDetailState.Error) {
                        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Réessayer")) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Fermer")) } }
    )
}

@Composable
private fun CommunityBookDetailContent(details: CommunityBookDetails) {
    var downloadReview by remember(details.book.id) { mutableStateOf(false) }
    if (details.book.origin == CommunityOrigin.Simulation) CommunityDemoNotice()
    Text(details.book.author, style = MaterialTheme.typography.titleMedium)
    Text(trf("Langue : %s", details.book.language))
    Text(tr("Format : EPUB"))
    Text(details.description ?: tr("Description non disponible."))
    if (details.sizeBytes != null) Text(trf("Taille : %s Ko", (details.sizeBytes + 1023) / 1024))
    else Text(tr("Taille du fichier indisponible."))
    Text(tr(if (details.sha256 == null) "Intégrité non vérifiée · téléchargement impossible." else "Une vérification du fichier sera nécessaire avant tout import."))
    OutlinedButton(onClick = { downloadReview = true }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(tr("Examiner le téléchargement"))
    }
    if (downloadReview) {
        val preparation = CommunityTransfers.prepareDownload(details)
        Text(tr(if (preparation is CommunityPreparation.Rejected && preparation.issue == CommunityPreparationIssue.DemonstrationOnly)
            "Exemple uniquement : aucun fichier EPUB n’est associé à cette fiche."
            else "Le fichier devra être autorisé et vérifié avant d’être ajouté à ta bibliothèque."))
        Text(tr("Téléchargement désactivé. Aucun fichier ne sera ajouté."),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Button(onClick = {}, enabled = false, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Télécharger · indisponible")) }
    }
}

@Composable
internal fun CommunityShareDialog(
    onClose: () -> Unit,
    selectDocument: (((CommunityShareDraft?) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var candidate by remember { mutableStateOf<CommunityShareDraft?>(null) }
    var selectionTicket by remember { mutableIntStateOf(0) }
    var inspecting by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    var pickerUnavailable by remember { mutableStateOf(false) }
    var rights by remember(candidate?.selectionId) { mutableStateOf(false) }
    var sharing by remember(candidate?.selectionId) { mutableStateOf(false) }
    var reviewed by remember(candidate?.selectionId) { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ticket = ++selectionTicket
            scope.launch {
                inspecting = true
                val result = inspectCommunityEpub(context, uri)
                if (ticket == selectionTicket) {
                    candidate = result
                    invalid = result?.isEligible != true
                    inspecting = false
                }
            }
        }
    }
    val draft = candidate
    val canReview = draft?.isEligible == true && rights && sharing && !inspecting
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Préparer le partage d’un EPUB")) },
        text = {
            Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.6f).dp)
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(OrynSpace.sm)) {
                Text(tr("Préparation locale uniquement. Aucun EPUB ne peut être téléversé."))
                Text(tr("Seules les informations du fichier sont lues. Son contenu n’est ni vérifié ni envoyé."))
                Text(tr("Une connexion et une nouvelle confirmation seront nécessaires lors de l’activation du service."))
                OutlinedButton(onClick = {
                    selectionTicket++; candidate = null; invalid = false; reviewed = false; rights = false; sharing = false
                    pickerUnavailable = false
                    val ticket = selectionTicket
                    try {
                        if (selectDocument == null) picker.launch(arrayOf("application/epub+zip"))
                        else selectDocument { selected ->
                            if (ticket == selectionTicket) { candidate = selected; invalid = selected?.isEligible != true }
                        }
                    }
                    catch (_: ActivityNotFoundException) { pickerUnavailable = true }
                    catch (_: SecurityException) { pickerUnavailable = true }
                }, modifier = Modifier.heightIn(min = 48.dp), enabled = !inspecting) { Text(tr("Choisir un EPUB")) }
                if (inspecting) { Text(tr("Lecture des informations du fichier…")); CircularProgressIndicator() }
                draft?.let {
                    Text(it.displayName, style = MaterialTheme.typography.titleMedium)
                    if (it.isEligible) Text(trf("Taille : %s Ko", (it.sizeBytes + 1023) / 1024))
                }
                if (pickerUnavailable) Text(tr("Impossible d’ouvrir le sélecteur de fichiers."),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (invalid) Text(tr("Choisis un EPUB identifié, de taille connue, entre 1 octet et 8 Mo."),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                CommunityConsentCheck(tr("Je confirme disposer des droits nécessaires pour partager cet EPUB."), rights,
                    draft?.isEligible == true && !inspecting) { rights = it; reviewed = false }
                CommunityConsentCheck(tr("Je souhaite rendre cet EPUB accessible à la communauté après validation."), sharing,
                    draft?.isEligible == true && !inspecting) { sharing = it; reviewed = false }
                if (reviewed) Text(tr("Choix vérifié sur cet appareil. Aucun fichier n’est envoyé et ce consentement ne sera pas réutilisé."),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text(tr("Fermer cette fenêtre ou changer de fichier efface les confirmations."))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (draft != null) reviewed = CommunityTransfers.prepareShare(draft,
                    CommunityShareConsent(draft.selectionId, rights, sharing)) is CommunityPreparation.Review
            }, enabled = canReview, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Vérifier mon choix")) }
        },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Fermer")) } }
    )
}

@Composable
private fun CommunityConsentCheck(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, enabled = enabled,
        role = Role.Checkbox, onValueChange = onChange).padding(vertical = OrynSpace.xs)) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = null)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

/** User-triggered metadata only. Never opens/copies bytes or persists a URI grant. */
private suspend fun inspectCommunityEpub(context: Context, uri: Uri): CommunityShareDraft? = withContext(Dispatchers.IO) {
    try {
        if (uri.scheme != "content") return@withContext null
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex < 0 || sizeIndex < 0 || cursor.isNull(nameIndex) || cursor.isNull(sizeIndex)) return@use null
            val name = cursor.getString(nameIndex) ?: return@use null
            if (name.length > 255) return@use null
            CommunityShareDraft(UUID.randomUUID(), name, cursor.getLong(sizeIndex), mime)
        }
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) { null }
}
