package com.example.oryn.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.ui.screen.LookupMode
import com.example.oryn.reader.ui.screen.LookupRequest
import com.example.oryn.reader.ui.screen.LookupSheet
import com.example.oryn.reader.ui.theme.OrynTheme
import com.example.oryn.reader.i18n.AppLanguage
import com.example.oryn.reader.i18n.Lang
import org.junit.After
import org.junit.Rule
import org.junit.Test

class LookupSheetTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val original = Lang.mode
    @After fun restore() { Lang.set(rule.activity, original) }

    @Test fun compactEntryShowsFirstSenseAndBookExampleBeforeOtherMeanings() {
        val entry = com.example.oryn.reader.data.remote.DictEntry("Nom commun", listOf(
            com.example.oryn.reader.data.remote.DictSense("Hospitalité."),
            com.example.oryn.reader.data.remote.DictSense("Deuxième sens de test.")))
        val result = com.example.oryn.reader.data.remote.DictionaryResult("teranga", null, listOf(entry), "Wiktionnaire", "wo")
        rule.setContent { OrynTheme(darkTheme = false) {
            com.example.oryn.reader.ui.screen.DefinitionContent(result, "Un passage du livre.") {}
        } }
        rule.onNodeWithText("Hospitalité.").assertExists()
        rule.onNodeWithText(tr("Dans ton livre")).assertExists()
        rule.onNodeWithText("Deuxième sens de test.").assertDoesNotExist()
        rule.onNodeWithText(tr("Voir les autres sens")).performClick()
        rule.onNodeWithText("Deuxième sens de test.").assertExists()
    }

    @Test fun ambiguousWordRequiresSourceAndLanguageChoicesCarryAcrossTabs() {
        Lang.set(rule.activity, AppLanguage.FRENCH)
        rule.activity.getSharedPreferences("lookup", 0).edit().putString("target_TRANSLATE", "de").commit()
        rule.setContent {
            OrynTheme(darkTheme = false) { LookupSheet(LookupRequest(LookupMode.DEFINE, "On")) {} }
        }
        rule.onNodeWithText(tr("Traduire")).performClick()
        rule.onNodeWithText(tr("Choisis la langue du passage pour continuer.")).assertExists()
        rule.onNodeWithText(tr("Langue du texte"), substring = true).performClick()
        rule.onNodeWithText("English").performClick()
        rule.onNodeWithText(tr("MyMemory recevra uniquement le texte sélectionné.")).assertExists()
        rule.onNodeWithText(tr("Traduire le passage")).assertExists()
        rule.onNodeWithText("Traduire vers · français").assertExists()
        rule.runOnIdle { Lang.set(rule.activity, AppLanguage.ENGLISH) }
        rule.onNodeWithText("Translate to · English").assertExists()
        rule.onNodeWithText(com.example.oryn.reader.i18n.trf("Ce passage est déjà en %s.", "English")).assertExists()
    }

    @Test fun systemGermanSuppliesTheDefinitionLanguage() {
        Lang.set(rule.activity, AppLanguage.SYSTEM)
        val config = android.content.res.Configuration(rule.activity.resources.configuration)
        config.setLocale(java.util.Locale.GERMAN)
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides config) {
                OrynTheme(darkTheme = false) { LookupSheet(LookupRequest(LookupMode.DEFINE, "On")) {} }
            }
        }
        rule.onNodeWithText(tr("Définition en") + " · " + java.util.Locale.GERMAN.getDisplayLanguage(Lang.locale)).assertExists()
    }
}
