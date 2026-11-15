package app.planner

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun str(id: Int) = rule.activity.getString(id)

    @Test fun createASessionStartPauseResumeAndStopTheTimer() {
        val title = "Review ${System.nanoTime()}"
        rule.onNode(hasText(str(R.string.tab_planner)) and hasClickAction()).performClick()

        rule.onNodeWithContentDescription(str(R.string.add)).performClick()
        rule.onNodeWithText(str(R.string.field_title_optional)).performTextInput(title)
        rule.onNodeWithText(str(R.string.save)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.save)).fetchSemanticsNodes().isEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }

        rule.onAllNodesWithContentDescription(str(R.string.start)).onFirst().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("timer_panel").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.pause)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.resume)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.resume)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.pause)).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText(str(R.string.stop)).performClick()
        rule.onNodeWithText(str(R.string.stop_timer_confirm)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("timer_panel").fetchSemanticsNodes().isEmpty() }
    }
}
