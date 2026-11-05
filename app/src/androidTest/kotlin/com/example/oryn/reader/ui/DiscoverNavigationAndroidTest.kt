package com.example.oryn.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.ui.components.FloatingActionNavBar
import com.example.oryn.reader.ui.components.HomeSection
import com.example.oryn.reader.ui.components.OrynNav
import com.example.oryn.reader.ui.theme.OrynTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Compiled in CI; real navigation and touch behavior still need a device run. */
@RunWith(AndroidJUnit4::class)
class DiscoverNavigationAndroidTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun discoverRoutesIndependentlyAndKeepsLibraryAndImport() {
        val route = mutableStateOf("home")
        val imports = AtomicInteger(0)
        rule.setContent {
            OrynTheme(darkTheme = false) {
                FloatingActionNavBar(
                    selectedRoute = route.value,
                    onRouteSelected = { route.value = it },
                    onAddClick = { imports.incrementAndGet() }
                )
            }
        }
        rule.onNodeWithContentDescription(tr("Découvrir")).assertExists().performClick()
        rule.runOnIdle { assertEquals("discover", route.value) }

        rule.onNodeWithContentDescription(tr("Bibliothèque")).assertExists().performClick()
        rule.runOnIdle {
            assertEquals("home", route.value)
            assertEquals(HomeSection.LIBRARY, OrynNav.section)
        }

        rule.onNodeWithContentDescription(tr("Ajouter un document")).assertExists().performClick()
        rule.runOnIdle { assertEquals(1, imports.get()) }
    }
}
