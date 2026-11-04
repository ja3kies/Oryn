package com.example.oryn.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.oryn.reader.data.importer.RichBook
import com.example.oryn.reader.data.local.preferences.ReaderSettings
import com.example.oryn.reader.ui.screen.ReflowReader
import com.example.oryn.reader.ui.theme.*
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Replaces the file behind an existing library id without recreating the reader Activity. */
@RunWith(AndroidJUnit4::class)
class ReaderDocumentReplacementTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun replacingSameBookIdCannotReuseStalePaginationOrPreparedText() {
        val document = mutableStateOf(RichBook("OLD_DOCUMENT a sufficiently long paragraph to paginate. ".repeat(350), emptyList(), emptyList()))
        val fontScale = mutableFloatStateOf(1f)
        val position = AtomicInteger(-1)
        rule.setContent {
            val originalDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(originalDensity.density, fontScale.floatValue)) {
                OrynTheme(darkTheme = false) {
                    val settings = ReaderSettings()
                    Box(Modifier.fillMaxWidth().height(420.dp)) {
                        ReflowReader(
                            bookId = 42, book = document.value, initialProgress = 0.6f,
                            settings = settings, fontFamily = FontFamily.Default,
                            linkColor = settings.themePreset.linkColor,
                            foregroundColor = settings.themePreset.textColor,
                            backgroundColor = settings.themePreset.pageColor,
                            horizontalPadding = 16.dp, onProgress = {}, onCenterTap = {},
                            onVisiblePosition = { position.set(it) }, annotations = emptyList(),
                            onAddAnnotation = {}, onUpdateAnnotation = {}, onDeleteAnnotation = {}
                        )
                    }
                }
            }
        }
        rule.waitUntil(20_000) { position.get() > 1000 }
        val replacement = RichBook("NEW_DOCUMENT unique replacement sentence. ".repeat(30), emptyList(), emptyList())
        rule.runOnIdle {
            position.set(-1)
            document.value = replacement
            fontScale.floatValue = 1.5f
        }
        rule.waitUntil(20_000) { position.get() in replacement.text.indices }
        rule.waitForIdle()
        rule.onAllNodesWithText("OLD_DOCUMENT", substring = true).assertCountEquals(0)
        assertTrue(rule.onAllNodesWithText("NEW_DOCUMENT", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("Visible offset must refer to the replacement", position.get() in replacement.text.indices)
    }
}
