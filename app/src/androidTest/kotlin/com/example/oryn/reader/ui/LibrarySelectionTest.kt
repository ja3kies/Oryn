package com.example.oryn.reader.ui

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.oryn.reader.data.local.entity.BookEntity
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.i18n.trf
import com.example.oryn.reader.ui.screen.LibraryContent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises library selection against live entity replacements without Room or external files. */
@RunWith(AndroidJUnit4::class)
class LibrarySelectionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val original = BookEntity(1, "Selection fixture", "Author", "unused.epub", "EPUB", coverPath = "")
    private val books = mutableStateOf(listOf(original))
    private var deleted: List<BookEntity> = emptyList()

    private fun show() {
        rule.setContent {
            MaterialTheme {
                LibraryContent(books.value, false, {}, {}, {}, onDeleteBooks = { deleted = it })
            }
        }
    }

    private fun selectBook() {
        rule.onNodeWithText(original.title).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        rule.onNodeWithText(tr("Sélectionner")).performClick()
        rule.onNodeWithContentDescription(tr("Annuler la sélection")).assertExists()
        rule.onNodeWithText(tr("Supprimer de la bibliothèque")).assertDoesNotExist()
    }

    @Test fun deletingSelectionRequiresExplicitConfirmationAndCancellationKeepsBooks() {
        show()
        selectBook()
        rule.onNodeWithContentDescription(tr("Supprimer la sélection")).performClick()
        rule.runOnIdle { assertTrue(deleted.isEmpty()) }
        rule.onNodeWithText(tr("Annuler")).performClick()
        rule.runOnIdle { assertTrue(deleted.isEmpty()) }
        rule.onNodeWithContentDescription(tr("Annuler la sélection")).assertExists()
        rule.onNodeWithContentDescription(tr("Supprimer la sélection")).performClick()
        rule.onNodeWithText(tr("Supprimer")).performClick()
        rule.runOnIdle { assertEquals(listOf(original), deleted) }
        rule.onNodeWithContentDescription(tr("Annuler la sélection")).assertDoesNotExist()
    }

    @Test fun selectionSurvivesFavoriteAndProgressUpdatesAndDeletesTheCurrentEntity() {
        show()
        selectBook()
        val updated = original.copy(progress = 0.25f, isFavorite = true)
        rule.runOnIdle { books.value = listOf(updated) }
        rule.onNodeWithText(original.title).assertIsSelected()
        rule.onNodeWithContentDescription(tr("Supprimer la sélection")).performClick()
        rule.onNodeWithText(tr("Supprimer")).performClick()
        rule.runOnIdle { assertEquals(listOf(updated), deleted) }
    }

    @Test fun selectionModeHidesFavoriteActionsAndDisablesEmptyDeletion() {
        show()
        selectBook()
        // Selecting a book must not accidentally toggle the unrelated favorite action.
        rule.onNodeWithContentDescription(tr("Ajouter aux favoris")).assertDoesNotExist()
        rule.onNodeWithContentDescription(tr("Retirer des favoris")).assertDoesNotExist()
        rule.onNodeWithText(original.title).performClick() // Deselect the only item.
        rule.onNodeWithContentDescription(tr("Supprimer la sélection")).assertIsNotEnabled()
        rule.runOnIdle { assertTrue(deleted.isEmpty()) }
    }

    @Test fun backCancelsSelectionWithoutDeleting() {
        show()
        selectBook()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(tr("Annuler la sélection")).assertDoesNotExist()
        rule.runOnIdle { assertTrue(deleted.isEmpty()) }
        rule.onNodeWithText(original.title).assertExists()
    }

    @Test fun selectionMenuClosesInListViewAsWellAsGridView() {
        show()
        rule.onNodeWithContentDescription(trf("Passer en affichage %s", tr("Liste").lowercase())).performClick()
        selectBook()
        rule.onNodeWithText(original.title).assertIsSelected()
    }
}
