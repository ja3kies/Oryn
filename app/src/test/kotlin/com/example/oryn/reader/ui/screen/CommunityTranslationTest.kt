package com.example.oryn.reader.ui.screen

import com.example.oryn.reader.community.CommunityOrigin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CommunityTranslationTest {
    private val original = Locale.getDefault()
    @After fun restoreLocale() { Locale.setDefault(original) }

    @Test fun simulatedTitleAuthorAndDescriptionUseDevelopmentTranslationsInEnglish() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Demonstration EPUB 01", communityMetadata("EPUB de démonstration 01", CommunityOrigin.Simulation))
        assertEquals("Demonstration author", communityMetadata("Auteur de démonstration", CommunityOrigin.Simulation))
        assertEquals("Simulated details for testing the catalog. No EPUB is published or available to download.",
            communityMetadata("Fiche simulée pour vérifier le catalogue. Aucun EPUB n’est publié ou téléchargeable.", CommunityOrigin.Simulation))
    }
    @Test fun remoteContentNeverEntersApplicationTranslationDictionary() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Chargement", communityMetadata("Chargement", CommunityOrigin.Remote))
        assertEquals("Auteur de démonstration", communityMetadata("Auteur de démonstration", CommunityOrigin.Remote))
    }
    @Test fun frenchSimulationKeepsItsSourceText() {
        Locale.setDefault(Locale.FRENCH)
        assertEquals("EPUB de démonstration 01", communityMetadata("EPUB de démonstration 01", CommunityOrigin.Simulation))
    }
}
