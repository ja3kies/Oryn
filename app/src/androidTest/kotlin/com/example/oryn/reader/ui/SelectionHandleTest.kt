package com.example.oryn.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.oryn.reader.ui.screen.SelectionHandle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SelectionHandleTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun draggingHandleTracksHorizontalDistanceOnItsOriginalLine() {
        var reported = Offset.Zero
        rule.setContent {
            Box(Modifier.fillMaxSize().testTag("page")) {
                SelectionHandle(Offset(120f, 180f), 167f, Color.Black, { reported = it }, {})
            }
        }
        rule.onNodeWithTag("page").performTouchInput {
            swipe(Offset(120f, 185f), Offset(180f, 185f), durationMillis = 350)
        }
        rule.runOnIdle {
            assertEquals(180f, reported.x, 2f)
            assertEquals(167f, reported.y, 1f)
        }
    }
}
