package com.example.oryn.reader.data.remote

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DictionaryEntryTest {
    @Test fun prospectusUsesTheFrenchSenseInFlatMediaWikiMarkup() {
        val html = """<div class="mw-heading"><h3><span class="titredef" id="fr-nom-1">Nom commun</span></h3></div>
            <p>prospectus</p><ol><li>Imprimé distribué au public.</li></ol>"""
        val result = LookupClient.frenchEntry("prospectus", "fr", html)!!
        assertEquals("Imprimé distribué au public.", result.entries.single().senses.single().definition)
    }

    @Test fun regionalWordFindsFrenchAndWolofWithoutAPresetLanguage() {
        fun section(code: String) = """<section><h3><span class="titredef" id="$code-nom-1">Nom commun</span></h3><ol><li>Hospitalité.</li></ol></section>"""
        val results = LookupClient.frenchEntries("teranga", section("fr") + section("wo"))
        assertEquals(listOf("fr", "wo"), results.map { it.language })
        assertEquals("Hospitalité.", results.last().entries.single().senses.single().definition)
    }

    @Test fun longMultilingualSelectionIsPreservedAcrossProviderRequests() {
        val passage = "Été à Montréal. ".repeat(60) + "📖".repeat(300) + "\n最後"
        val parts = LookupClient.chunks(passage).toList()
        assertEquals(passage, parts.joinToString(""))
        assertTrue(parts.all { it.toByteArray().size <= 500 && !it.first().isLowSurrogate() })
    }

    @Test fun rejectsUnreadableTranslatedGloss() {
        assertThrows(LookupClient.UnreadableResult::class.java) {
            LookupClient.translationText(JSONObject("""{"responseStatus":200,"responseData":{"translatedText":"?? ?? ? ??."}}"""))
        }
    }

    @Test fun rejectsQuotaAndProviderErrorsInsteadOfDisplayingThemAsTranslations() {
        assertThrows(LookupClient.QuotaExceeded::class.java) {
            LookupClient.translationText(JSONObject("""{"responseStatus":429,"responseData":{"translatedText":"Quota exceeded"}}"""))
        }
        assertThrows(java.io.IOException::class.java) {
            LookupClient.translationText(JSONObject("""{"responseStatus":403,"responseData":{"translatedText":"Denied"}}"""))
        }
        assertEquals("J’aime l’été & la mer", LookupClient.translationText(JSONObject(
            """{"responseStatus":200,"responseData":{"translatedText":"J’aime l’été &amp; la mer"}}"""
        )))
    }

    @Test fun frenchDefinitionUsesMatchingSectionAndSeparatesItsExample() {
        val article = """<section><h3><span class="titredef" id="fr-pronom-1">Pronom</span></h3>
            <ol><li>Une personne indéterminée.<ul><li><span class="example"><q>On frappe.</q></span></li></ul></li></ol></section>"""
        val result = LookupClient.frenchEntry("on", "fr", article)!!
        assertFalse(result.translated)
        assertEquals(DictSense("Une personne indéterminée.", "On frappe."), result.entries.single().senses.single())
        assertNull(LookupClient.frenchEntry("on", "en", article))
    }

    @Test fun homographUsesOnlyTheRequestedLanguageAndKeepsItsExample() {
        val response = JSONObject("""{"word":"on","entries":[
            {"language":{"code":"en"},"senses":[{"definition":"position"}]},
            {"language":{"code":"fr"},"partOfSpeech":"pronoun","senses":[
                {"definition":"an unspecified person","examples":["On frappe à la porte."]}]}
        ]}""")
        val result = LookupClient.dictionaryEntry(response, "fr")!!
        assertEquals("fr", result.language)
        assertEquals(DictSense("an unspecified person", "On frappe à la porte."), result.entries.single().senses.single())
        assertNull(LookupClient.dictionaryEntry(response, "de"))
        assertEquals("fr", LookupClient.guessLanguage("On ajoutait sur ce bout de papier que le Sénégal est respecté."))
    }
}
