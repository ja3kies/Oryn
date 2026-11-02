package com.example.oryn.reader.ui.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.oryn.reader.community.auth.AuthStatus
import com.example.oryn.reader.community.auth.CommunityAuthRuntime
import com.example.oryn.reader.i18n.Text
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.ui.theme.OrynShape
import com.example.oryn.reader.ui.theme.OrynSpace
import kotlinx.coroutines.delay

@Composable
internal fun CommunityAuthSection(runtime: CommunityAuthRuntime) {
    val state by runtime.controller.state.collectAsState()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(runtime, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            runtime.restore()
            while (true) { delay(30_000); runtime.tick() }
        }
    }
    LaunchedEffect(runtime, context) {
        runtime.browserUrls.collect { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
            } catch (_: ActivityNotFoundException) { runtime.browserUnavailable() }
            catch (_: SecurityException) { runtime.browserUnavailable() }
        }
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(OrynShape.card), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(OrynSpace.md), verticalArrangement = Arrangement.spacedBy(OrynSpace.sm)) {
            Text(tr("Compte communautaire"), style = MaterialTheme.typography.titleMedium)
            val message = when (state.status) {
                AuthStatus.Disabled -> "La connexion communautaire n’est pas encore disponible. Ta bibliothèque reste accessible sans compte."
                AuthStatus.SignedOut -> "Connecte-toi dans ton navigateur. Ta bibliothèque reste indépendante de ton compte."
                AuthStatus.Busy -> "Vérification de la session…"
                AuthStatus.AwaitingCallback -> "Termine la connexion dans ton navigateur, ou annule ici."
                AuthStatus.SignedIn -> "Connexion établie. Le catalogue et le partage restent désactivés."
                AuthStatus.Offline -> "Connexion réseau indisponible. La session n’a pas pu être vérifiée."
                AuthStatus.InvalidSession -> "La session n’est plus valide. Reconnecte-toi."
                AuthStatus.InvalidCallback -> "Lien de connexion refusé. Reviens à la tentative en cours ou recommence."
                AuthStatus.Cancelled -> "Connexion annulée."
                AuthStatus.Expired -> "La tentative de connexion a expiré. Recommence."
                AuthStatus.Error -> "Connexion impossible pour le moment. Réessaie plus tard."
                AuthStatus.StorageError -> "Le stockage sécurisé est indisponible. Aucune session ne peut être conservée."
                AuthStatus.RedirectUnavailable -> "Le navigateur ou le lien de retour vérifié est indisponible."
                AuthStatus.SignedOutRevocationUnconfirmed -> "Déconnexion locale effectuée. La révocation serveur n’a pas pu être confirmée ; la session distante peut rester active jusqu’à expiration."
            }
            Text(tr(message), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (state.status != AuthStatus.Disabled) {
                when {
                    state.hasPending -> Button(onClick = runtime::cancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Annuler la connexion")) }
                    state.hasSession -> {
                        if (state.status != AuthStatus.SignedIn && state.status != AuthStatus.Busy) {
                            Button(onClick = runtime::restore, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Réessayer")) }
                        }
                        OutlinedButton(onClick = runtime::signOut, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Se déconnecter")) }
                    }
                    state.status == AuthStatus.Busy -> Button(onClick = runtime::cancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Annuler la connexion")) }
                    else -> Button(onClick = runtime::signIn, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Se connecter")) }
                }
            }
        }
    }
}
