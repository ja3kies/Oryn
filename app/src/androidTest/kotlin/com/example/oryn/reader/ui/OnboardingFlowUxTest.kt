package com.example.oryn.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.oryn.reader.i18n.tr
import com.example.oryn.reader.ui.screen.OnboardingScreen
import com.example.oryn.reader.ui.theme.OrynTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** CI compiles the test; physical-device execution is an independent gate. */
@RunWith(AndroidJUnit4::class)
class OnboardingFlowUxTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun primaryCtaRemainsVisibleWithLargeTextThroughAllFourPages() {
        val finished = AtomicInteger()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                OrynTheme(darkTheme = false) {
                    OnboardingScreen(onFinish = { finished.incrementAndGet() })
                }
            }
        }
        repeat(3) {
            rule.onNodeWithText(tr("Suivant")).assertIsDisplayed().performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithText(tr("Commencer")).assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, finished.get()) }
    }
}
