package com.example.oryn.reader.community

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.oryn.reader.BuildConfig
import com.example.oryn.reader.community.auth.AuthApi
import com.example.oryn.reader.community.auth.AuthConfiguration
import com.example.oryn.reader.community.auth.AuthStatus
import com.example.oryn.reader.community.auth.AuthStore
import com.example.oryn.reader.community.auth.AuthTokens
import com.example.oryn.reader.community.auth.StoredAuth
import com.example.oryn.reader.community.auth.SupabaseAuthController
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.ui.screen.CommunityBookDetailDialog
import com.example.oryn.reader.ui.screen.CommunityCatalogSection
import com.example.oryn.reader.ui.screen.CommunityShareDialog
import com.example.oryn.reader.ui.theme.OrynTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

/** Hermetic Android scenarios in the Compose test Activity, never MainActivity/Room.
 * No browser, real account, real file, HTTP endpoint or publication is simulated as a live success.
 * Real OAuth/App Links/Keystore validation is specified separately in the phase 5 report.
 */
@RunWith(AndroidJUnit4::class)
class CommunityAndroidScenariosTest {
    @get:Rule val compose = createComposeRule()
    @Before fun noLiveAuth() { assumeFalse("Hermetic scenarios require Auth disabled", BuildConfig.COMMUNITY_AUTH_ENABLED) }

    @Test fun pkceControllerOnAndroidRejectsForgedCallbackAndReplay() = runBlocking {
        val user = UUID.fromString("10000000-0000-4000-8000-000000000001")
        var record: StoredAuth? = null
        var exchanges = 0
        val vault = object : AuthStore {
            override suspend fun read() = record
            override suspend fun write(value: StoredAuth) { record = value }
            override suspend fun clear() { record = null }
        }
        val api = object : AuthApi {
            override suspend fun exchange(code: String, verifier: String): AuthTokens {
                exchanges++; return AuthTokens("fixture-access", "fixture-refresh", 3600)
            }
            override suspend fun refresh(refreshToken: String) = error("Not used")
            override suspend fun userId(accessToken: String) = user
            override suspend fun logout(accessToken: String) = Unit
        }
        val config = AuthConfiguration("https://auth.example.test", "sb_publishable_android_fixture_12345", "reader.example.test", "github", true, true, true)
        val controller = SupabaseAuthController(config, api, vault) { 1000L }
        val url = controller.beginSignIn()!!
        val redirect = URI(url).rawQuery.split('&').first { it.startsWith("redirect_to=") }.substringAfter('=')
        val callback = URLDecoder.decode(redirect, "UTF-8") + "&code=fixture-code"
        controller.receiveCallback(callback.replace("oryn_state=", "oryn_state=forged"))
        assertEquals(0, exchanges)
        controller.receiveCallback(callback)
        assertEquals(AuthStatus.SignedIn, controller.state.value.status)
        controller.receiveCallback(callback)
        assertEquals(1, exchanges)
        // Recreate the controller using persisted state; device browser/Keystore still need real validation.
        val restored = SupabaseAuthController(config, api, vault) { 1000L }
        restored.restore()
        assertTrue(restored.isCurrent("fixture-access"))
        restored.signOut()
        assertFalse(restored.isCurrent("fixture-access"))
        assertNull(record)
    }

    @Test fun searchFilterAndPaginationRemainExplicitlySimulated() {
        compose.setContent { OrynTheme(darkTheme = false) {
            Column(Modifier.verticalScroll(rememberScrollState())) { CommunityCatalogSection() }
        } }
        compose.onNodeWithText(tr("Explorer la démonstration · développement")).performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText(tr("EPUB de démonstration 01")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(tr("Rechercher dans la communauté")).performScrollTo().performTextReplacement("Demonstration EPUB 01")
        compose.waitUntil(5000) { compose.onAllNodesWithText(tr("EPUB de démonstration 01")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(tr("Français")).performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText(tr("EPUB de démonstration 01")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(tr("Réinitialiser les filtres")).performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText(tr("EPUB de démonstration 06")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(tr("Page suivante")).performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText(tr("EPUB de démonstration 07")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(tr("Démonstration · données simulées, aucun EPUB réellement publié.")).performScrollTo()
    }

    @Test fun downloadReviewCannotExecuteOrImportTheDemonstrationFile() {
        val details = CommunityBookDetails(CommunityBook(UUID.randomUUID(), "EPUB de démonstration 01", "Auteur de démonstration", "fr", CommunityOrigin.Simulation))
        compose.setContent { OrynTheme(darkTheme = false) { CommunityBookDetailDialog(CommunityDetailState.Content(details), {}, {}) } }
        compose.onNodeWithText(tr("Examiner le téléchargement")).performScrollTo().performClick()
        compose.onNodeWithText(tr("Télécharger · indisponible")).performScrollTo().assertIsNotEnabled()
        assertEquals(CommunityPreparation.Rejected(CommunityPreparationIssue.DemonstrationOnly), CommunityTransfers.prepareDownload(details))
    }

    @Test fun bothRightsAndSharingConsentResetForEveryNewSelection() {
        compose.setContent { OrynTheme(darkTheme = false) {
            CommunityShareDialog(onClose = {}, selectDocument = { result ->
                result(CommunityShareDraft(UUID.randomUUID(), "instrumentation-fixture.epub", 4096, "application/epub+zip"))
            })
        } }
        val review = tr("Vérifier mon choix")
        compose.onNodeWithText(review).assertIsNotEnabled()
        compose.onNodeWithText(tr("Choisir un EPUB")).performScrollTo().performClick()
        compose.onNodeWithText(tr("Je confirme disposer des droits nécessaires pour partager cet EPUB.")).performScrollTo().performClick()
        compose.onNodeWithText(review).assertIsNotEnabled()
        compose.onNodeWithText(tr("Je souhaite rendre cet EPUB accessible à la communauté après validation.")).performScrollTo().performClick()
        compose.onNodeWithText(review).assertIsEnabled().performClick()
        compose.onNodeWithText(tr("Choisir un EPUB")).performScrollTo().performClick()
        compose.onNodeWithText(review).assertIsNotEnabled()
    }
    @Test fun revokedReviewCannotExecuteOrReachNetworkOnAndroid() = runBlocking {
        val draft = CommunityShareDraft(UUID.randomUUID(), "instrumentation-fixture.epub", 4096, "application/epub+zip")
        val plan = (CommunityTransfers.prepareShare(draft, CommunityShareConsent(draft.selectionId, true, true)) as CommunityPreparation.Review).value
        val input = com.example.oryn.reader.community.edge.CreateCommunityUploadInput(plan, "test title", "test author", "fr")
        plan.revoke()
        assertEquals(CommunityTransferExecution.Disabled, CommunityTransfers.executeShare(plan))
        val unavailableSession = object : CommunitySessionProvider {
            override suspend fun accessToken(): String? = error("Disabled operation must not read Auth")
            override suspend fun clear() = error("Disabled operation must not clear Auth")
        }
        val result = com.example.oryn.reader.community.edge.CommunityEdgeRepository.fromBuild(unavailableSession).createUpload(input)
        assertEquals(com.example.oryn.reader.community.edge.CommunityEdgeFailureCode.Disabled,
            (result as com.example.oryn.reader.community.edge.CommunityEdgeResult.Failure).code)
    }

}
