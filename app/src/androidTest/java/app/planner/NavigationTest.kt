package app.planner

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.planner.ui.navigation.StudyDestination
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun allFiveStudyDestinationsAreReachable() {
        assertEquals(5, StudyDestination.entries.size)
        StudyDestination.entries.forEach { dest ->
            rule.onNode(hasText(rule.activity.getString(dest.navLabel)) and hasClickAction()).performClick()
            rule.onNodeWithTag(dest.testTag).assertIsDisplayed()
        }
    }

    @Test fun settingsOpensAndBackReturns() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.settings)).performClick()
        rule.onNodeWithTag("screen_settings").assertIsDisplayed()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.back)).performClick()
        rule.onNodeWithTag(StudyDestination.SCHEDULE.testTag).assertIsDisplayed()
    }
}
