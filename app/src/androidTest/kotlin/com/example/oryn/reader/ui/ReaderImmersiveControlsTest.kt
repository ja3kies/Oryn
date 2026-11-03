package com.example.oryn.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.oryn.reader.data.importer.RichBook
import com.example.oryn.reader.data.local.preferences.ReaderSettings
import com.example.oryn.reader.data.local.preferences.ReaderThemePreset
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.ui.components.*
import com.example.oryn.reader.ui.screen.ReflowReader
import com.example.oryn.reader.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Actual pager, compact chrome and existing full ReaderSheet. No Room or external EPUB fixture. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderImmersiveControlsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val position = AtomicInteger(-1)
    private val progressWrites = AtomicInteger()
    private val bookmarkChanges = AtomicInteger()
    private val exits = AtomicInteger()
    private val timerRequests = AtomicInteger()

    private fun show(theme: ReaderThemePreset = ReaderThemePreset.LIGHT, fontScale: Float = 1f, short: Boolean = false) {
        val book = RichBook("A local reading paragraph with enough words to paginate.\n\n".repeat(300), emptyList(), emptyList())
        rule.setContent {
            var state by rememberSaveable { mutableStateOf(ReaderControlsState.HIDDEN) }
            var settings by remember { mutableStateOf(ReaderSettings(themePreset = theme)) }
            var bookmarked by remember { mutableStateOf(false) }
            var focus by remember { mutableStateOf(ReaderSheetFocus.ALL) }
            val originalDensity = LocalDensity.current
            val metrics = readerChromeMetrics(fontScale, true)
            val dispatch: (ReaderControlsEvent) -> Unit = { state = state.transition(it) }
            CompositionLocalProvider(LocalDensity provides Density(originalDensity.density, fontScale)) {
            OrynTheme(darkTheme = false) {
                ReaderControlsBackHandler(state, onEvent = dispatch, onExit = { exits.incrementAndGet() })
                Box((if (short) Modifier.fillMaxWidth().height(360.dp) else Modifier.fillMaxSize()).background(settings.themePreset.pageColor)
                    .readerControlsKeyModifier(state, true, dispatch).testTag("ReaderKeyboardSurface")) {
                    ReaderSurfaceTapBackdrop({ dispatch(ReaderControlsEvent.SURFACE_TAP) }, Modifier.matchParentSize())
                    Box(Modifier.fillMaxSize().padding(top = metrics.top + 16.dp, bottom = metrics.bottom + 16.dp).testTag("ReadingSurface")) {
                        ReflowReader(
                            bookId = 1, book = book, linkColor = settings.themePreset.linkColor,
                            initialProgress = 0.4f, settings = settings, fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            foregroundColor = settings.themePreset.textColor, backgroundColor = settings.themePreset.pageColor,
                            horizontalPadding = 16.dp, onProgress = { progressWrites.incrementAndGet() },
                            onCenterTap = { dispatch(ReaderControlsEvent.SURFACE_TAP) }, onVisiblePosition = { position.set(it) },
                            annotations = emptyList(), onAddAnnotation = {}, onUpdateAnnotation = {}, onDeleteAnnotation = {}
                        )
                    }
                    ReaderCompactControls(
                        state = state, metrics = metrics, chapterTitle = "Test chapter", chapterRemaining = "3 pages after this page",
                        bookProgress = 0.4f, chapterProgress = 0.2f, bookmarked = bookmarked, currentAccent = AppAccentTheme.ORANGE,
                        onBack = { exits.incrementAndGet() }, onTimer = { timerRequests.incrementAndGet() },
                        onBookmark = { bookmarked = !bookmarked; bookmarkChanges.incrementAndGet() }, onContents = {}, onAnnotations = {},
                        onExpand = { focus = it; dispatch(ReaderControlsEvent.EXPAND) }, modifier = Modifier.fillMaxSize()
                    )
                    if (state == ReaderControlsState.EXPANDED) ReaderControlsSheet(
                        settings = settings, onSettingsChange = { settings = it(settings) }, currentChapterTitle = "Test chapter",
                        onDismiss = { dispatch(ReaderControlsEvent.DISMISS_SHEET) }, initialFocus = focus
                    )
                }
            }
            }
        }
        rule.waitUntil(30_000) { position.get() >= 0 }
        rule.waitForIdle()
    }
    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }
    private fun reveal() { rule.onNodeWithTag("ReadingSurface").performTouchInput { click(center) } }

    @Test fun oneTapRevealsCompactAndAnOutsideTapHidesWithoutMovingThePage() {
        show()
        val before = position.get(); val writes = progressWrites.get()
        reveal()
        rule.onNodeWithContentDescription(tr("Tous les réglages")).assertIsDisplayed()
        rule.onNodeWithTag("ReaderKeyboardSurface").performTouchInput { click(Offset(2f, 2f)) }
        rule.onNodeWithContentDescription(tr("Tous les réglages")).assertDoesNotExist()
        rule.runOnIdle { assertEquals(before, position.get()); assertEquals(writes, progressWrites.get()) }
    }
    @Test fun toolbarButtonsAndEdgeTapCannotAccidentallyTurnPages() {
        show()
        val before = position.get(); val writes = progressWrites.get()
        rule.onNodeWithTag("ReadingSurface").performTouchInput { click(Offset(4f, height / 2f)) }
        rule.onNodeWithContentDescription(tr("Marquer le livre")).performClick()
        rule.onNodeWithContentDescription(tr("Retirer le marque-page du livre")).assertIsDisplayed()
        rule.onNodeWithContentDescription(tr("Chronomètre de lecture")).performClick()
        rule.runOnIdle {
            assertEquals(1, bookmarkChanges.get()); assertEquals(1, timerRequests.get())
            assertEquals(before, position.get()); assertEquals(writes, progressWrites.get())
        }
    }
    @Test fun menuOpensOnlyTheExistingSheetAndBackCollapsesThenHidesThenExits() {
        show(); reveal()
        rule.onNodeWithContentDescription(tr("Tous les réglages")).performClick()
        rule.onNodeWithText(tr("Réglages de lecture")).assertIsDisplayed()
        rule.onNodeWithTag("ReaderCompactTopBar").assertDoesNotExist()
        pressBack()
        rule.onNodeWithContentDescription(tr("Tous les réglages")).assertIsDisplayed()
        pressBack()
        rule.onNodeWithContentDescription(tr("Tous les réglages")).assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, exits.get()) }
        pressBack()
        rule.runOnIdle { assertEquals(1, exits.get()) }
    }
    @Test fun downwardSwipeDismissesTheSheetToCompact() {
        show(); reveal()
        rule.onNodeWithContentDescription(tr("Tous les réglages")).performClick()
        rule.onNodeWithContentDescription(tr("Réduire les commandes")).performTouchInput {
            swipe(start = center, end = center + Offset(0f, 700f), durationMillis = 300)
        }
        rule.onNodeWithContentDescription(tr("Tous les réglages")).assertIsDisplayed()
    }
    @Test fun typographyAndLayoutShortcutsOpenTheirSettingsAndRemainDismissible() {
        show(); reveal()
        rule.onNodeWithContentDescription(tr("Typographie")).performClick()
        rule.onNodeWithText(tr("Police")).assertIsDisplayed()
        pressBack()
        rule.onNodeWithContentDescription(tr("Mise en page")).performClick()
        rule.onNodeWithText(tr("Marges")).assertIsDisplayed()
        pressBack()
        rule.onNodeWithContentDescription(tr("Tous les réglages")).assertIsDisplayed()
    }
    private fun assertPageIsClearOfChrome() {
        reveal()
        val top = rule.onNodeWithTag("ReaderCompactTopBar").fetchSemanticsNode().boundsInRoot
        val bottom = rule.onNodeWithTag("ReaderCompactBottomControls").fetchSemanticsNode().boundsInRoot
        val text = rule.onAllNodesWithTag("ReaderPageText").fetchSemanticsNodes().filter { it.boundsInRoot.height > 0 }
        assertTrue(text.isNotEmpty())
        for (node in text) {
            assertTrue(node.boundsInRoot.top >= top.bottom)
            assertTrue(node.boundsInRoot.bottom <= bottom.top)
        }
        val all = rule.onAllNodesWithTag("ReaderPageText")
        for (index in all.fetchSemanticsNodes().indices) {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            all[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            assertTrue(layouts.none { it.hasVisualOverflow })
        }
    }
    @Test fun lightPageIsNotObscuredByFloatingControls() { show(); assertPageIsClearOfChrome() }
    @Test fun darkPageIsNotObscuredByFloatingControls() { show(ReaderThemePreset.MIDNIGHT); assertPageIsClearOfChrome() }
    @Test fun sepiaLargeTextInShortViewportIsNotObscuredByFloatingControls() {
        show(ReaderThemePreset.BEIGE, fontScale = 2f, short = true)
        assertPageIsClearOfChrome()
    }

    @Test fun enterRevealsControlsButEnterOnTheMenuDoesNotTurnThePageOrHideControls() {
        show()
        val before = position.get(); val writes = progressWrites.get()
        rule.onNodeWithTag("ReaderKeyboardSurface").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        rule.onNodeWithTag("ReaderKeyboardSurface").performKeyInput { pressKey(Key.Enter) }
        val menu = rule.onNodeWithContentDescription(tr("Tous les réglages"))
        menu.assertIsDisplayed().performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        menu.performKeyInput { pressKey(Key.Enter) }
        rule.onNodeWithText(tr("Réglages de lecture")).assertIsDisplayed()
        rule.runOnIdle { assertEquals(before, position.get()); assertEquals(writes, progressWrites.get()) }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(tr("Tous les réglages")).assertIsDisplayed()
    }

}
