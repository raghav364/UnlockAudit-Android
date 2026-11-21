package com.raghava.unlockaudit

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Requires Android device/emulator; visual and accessibility review still needed. */
@RunWith(AndroidJUnit4::class)
class NavigationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homeHistorySettingsCanBeNavigated() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Your activity, organized.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Browse your activity").assertExists()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Privacy and preferences").assertExists()
    }
}
