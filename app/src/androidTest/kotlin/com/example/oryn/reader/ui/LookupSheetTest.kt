package com.example.oryn.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.ui.screen.LookupMode
import com.example.oryn.reader.ui.screen.LookupRequest
import com.example.oryn.reader.ui.screen.LookupSheet
import com.example.oryn.reader.ui.theme.OrynTheme
import org.junit.Rule
import org.junit.Test

class LookupSheetTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun ambiguousWordRequiresSourceAndLanguageChoicesCarryAcrossTabs() {
        rule.activity.getSharedPreferences("lookup", 0).edit().putString("target_TRANSLATE", "fr").commit()
        rule.setContent {
            OrynTheme(darkTheme = false) { LookupSheet(LookupRequest(LookupMode.DEFINE, "On")) {} }
        }
        rule.onNodeWithText(tr("Choisis la langue du passage pour continuer.")).assertExists()
        rule.onNodeWithText(tr("Traduction")).performClick()
        rule.onNodeWithText(tr("Langue du texte"), substring = true).performClick()
        rule.onNodeWithText("English").performClick()
        rule.onNodeWithText(tr("MyMemory recevra uniquement le texte sélectionné.")).assertExists()
        rule.onNodeWithText(tr("Traduire le passage")).assertExists()
        rule.onNodeWithText(tr("Traduire vers"), substring = true).performClick()
        rule.onNodeWithText("English", useUnmergedTree = true).performClick()
        rule.onNodeWithText(com.example.oryn.reader.i18n.trf("Ce passage est déjà en %s.", "English")).assertExists()
    }
}
