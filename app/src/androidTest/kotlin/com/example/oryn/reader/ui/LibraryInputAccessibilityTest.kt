package com.example.oryn.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.ui.components.FavoriteButton
import com.example.oryn.reader.ui.components.GlassSearchField
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryInputAccessibilityTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun searchFieldRetainsItsAccessibleLabelAndHasAnAccessibleClearTarget() {
        val query = mutableStateOf("")
        rule.setContent { MaterialTheme { GlassSearchField(query.value, { query.value = it }, "Library search") } }
        val field = rule.onNode(hasContentDescription("Library search") and hasSetTextAction())
        field.performTextInput("Unicode résumé")
        field.assertTextEquals("Unicode résumé")
        rule.onNodeWithContentDescription(tr("Effacer")).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        rule.runOnIdle { assertEquals("", query.value) }
        field.assertExists()
    }

    @Test fun searchImeActionInvokesSearchAndReleasesKeyboardFocus() {
        var searches = 0
        rule.setContent { MaterialTheme { GlassSearchField("query", {}, "Library search", onSearch = { searches++ }) } }
        val field = rule.onNode(hasContentDescription("Library search") and hasSetTextAction())
        field.performClick().assertIsFocused().performImeAction()
        field.assertIsNotFocused()
        rule.runOnIdle { assertEquals(1, searches) }
    }

    @Test fun smallFavoriteGlyphStillProvides48DpToggleTarget() {
        val favorite = mutableStateOf(false)
        rule.setContent { MaterialTheme { FavoriteButton(favorite.value, { favorite.value = !favorite.value }, size = 28.dp) } }
        rule.onNodeWithContentDescription(tr("Ajouter aux favoris"))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).assertIsOff().performClick()
        rule.onNodeWithContentDescription(tr("Retirer des favoris")).assertIsOn()
    }
}
