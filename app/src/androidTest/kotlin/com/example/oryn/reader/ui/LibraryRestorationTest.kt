package com.example.oryn.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.oryn.reader.data.local.entity.BookEntity
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.ui.components.HomeSection
import com.example.oryn.reader.ui.components.OrynNav
import com.example.oryn.reader.ui.screen.BookmarksScreen
import com.example.oryn.reader.ui.screen.HomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Lazy item keys must be Android-saveable, even when a cover changes. */
@RunWith(AndroidJUnit4::class)
class LibraryRestorationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val book = BookEntity(1, "Restoration fixture", "Author", "unused.epub", "EPUB", progress = 0.2f, isFavorite = true, coverPath = "")

    @Test fun favoritesGridCanSaveAndRestoreItsVisibleItems() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { MaterialTheme { BookmarksScreen(listOf(book), false, {}, {}, {}) } }
        rule.onNodeWithText(book.title).assertExists()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText(book.title).assertExists()
    }

    @Test fun continueReadingCarouselCanSaveAndRestoreItsVisibleItems() {
        val previousSection = OrynNav.section
        try {
            rule.runOnUiThread { OrynNav.section = HomeSection.HOME }
            val restoration = StateRestorationTester(rule)
            restoration.setContent { MaterialTheme { HomeScreen(listOf(book), false, {}, {}, {}) } }
            rule.onNodeWithText(tr("Ta lecture en cours")).performScrollTo()
            rule.onAllNodesWithText(book.title).onFirst().assertExists()
            restoration.emulateSavedInstanceStateRestore()
            rule.onNodeWithText(tr("Ta lecture en cours")).performScrollTo()
            rule.onAllNodesWithText(book.title).onFirst().assertExists()
        } finally {
            rule.runOnUiThread { OrynNav.section = previousSection }
        }
    }
}
