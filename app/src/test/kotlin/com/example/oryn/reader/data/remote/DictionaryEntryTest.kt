package com.example.oryn.reader.data.remote

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DictionaryEntryTest {
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
