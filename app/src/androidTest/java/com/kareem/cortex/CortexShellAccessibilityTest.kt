package com.kareem.cortex

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CortexShellAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<CortexShellActivity>()

    @Test
    fun primaryNavigationExposesFourDistinctSelectableDestinations() {
        composeRule.onNodeWithContentDescription("Now").assertIsSelected().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Work").assertHasClickAction().performClick()
        composeRule.onNodeWithContentDescription("Work").assertIsSelected()
        composeRule.onNodeWithContentDescription("Memory").assertHasClickAction().performClick()
        composeRule.onNodeWithContentDescription("Memory").assertIsSelected()
        composeRule.onNodeWithContentDescription("Capture").assertHasClickAction().performClick()
        composeRule.onNodeWithContentDescription("Capture").assertIsSelected()
    }

    @Test
    fun dockAndQuickVoiceExposeNamedActions() {
        composeRule.onAllNodesWithContentDescription("Open Cortex Dock")[0].assertHasClickAction().performClick()
        composeRule.onNodeWithText("Cortex Dock").assertIsDisplayed()
        composeRule.onNodeWithText("Ask Cortex").assertIsDisplayed()
    }

    @Test
    fun quickVoiceHasExplicitAccessibilityName() {
        composeRule.onNodeWithContentDescription("Voice capture").assertHasClickAction()
    }
}
