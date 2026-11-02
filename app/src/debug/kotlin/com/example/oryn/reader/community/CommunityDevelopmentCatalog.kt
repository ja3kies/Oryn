package com.example.oryn.reader.community

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.UUID

/** Compiled exclusively into debug. No real author, publication, file or remote URL. */
object CommunityDevelopmentCatalog {
    const val available = true
    fun repository(scenario: CommunitySimulationScenario): CommunityCatalogRepository? =
        SimulatedCommunityCatalogRepository(DevelopmentCommunitySource(scenario))
}

internal class DevelopmentCommunitySource(
    private val scenario: CommunitySimulationScenario,
    private val latencyMs: Long = 400
) : CommunityCatalogDataSource {
    private val records = (1..18).map { index ->
        CommunityBookDetails(CommunityBook(
            UUID.fromString("00000000-0000-4000-8000-" + index.toString().padStart(12, '0')),
            "EPUB de démonstration " + String.format(Locale.ROOT, "%02d", index),
            "Auteur de démonstration", if (index % 2 == 0) "en" else "fr", CommunityOrigin.Simulation
        ), "Fiche simulée pour vérifier le catalogue. Aucun EPUB n’est publié ou téléchargeable.")
    }
    private suspend fun beforeRead() {
        delay(latencyMs)
        when (scenario) {
            CommunitySimulationScenario.Loading -> awaitCancellation()
            CommunitySimulationScenario.Error -> throw CommunitySourceFailure(CommunityCatalogState.Error)
            CommunitySimulationScenario.Offline -> throw CommunitySourceFailure(CommunityCatalogState.Offline)
            CommunitySimulationScenario.SignInRequired -> throw CommunitySourceFailure(CommunityCatalogState.SignInRequired)
            CommunitySimulationScenario.Unavailable -> throw CommunitySourceFailure(CommunityCatalogState.Unavailable)
            else -> Unit
        }
    }
    override suspend fun search(query: CommunityQuery): CommunityPage {
        beforeRead()
        if (scenario == CommunitySimulationScenario.Empty) return CommunityPage(emptyList(), null)
        // The demonstration UI translates these labels; search must also match the visible English text.
        val aliases = records.associate { it.book.id to listOf(
            "Demonstration EPUB " + it.book.title.takeLast(2), "Demonstration author"
        ) }
        return communityPage(records.map { it.book }, query, aliases)
    }
    override suspend fun details(id: UUID): CommunityBookDetails? {
        beforeRead()
        return if (scenario == CommunitySimulationScenario.Empty) null else records.find { it.book.id == id }
    }
}
