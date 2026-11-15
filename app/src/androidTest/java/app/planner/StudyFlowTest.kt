package app.planner

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun str(id: Int) = rule.activity.getString(id)

    private fun openTab(label: Int) {
        rule.onNode(hasText(str(label)) and hasClickAction()).performClick()
    }

    private fun waitForDialogToClose() {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.save)).fetchSemanticsNodes().isEmpty() }
    }

    private fun addCourse(name: String) {
        openTab(R.string.tab_courses)
        rule.onNodeWithContentDescription(str(R.string.add)).performClick()
        rule.onNodeWithText(str(R.string.field_name)).performTextInput(name)
        rule.onNodeWithText(str(R.string.save)).performClick()
        waitForDialogToClose()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun dayName(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.getDefault())

    @Test fun oneClassOnTwoDaysShowsTwoCardsAndADaysCanBeDeselected() {
        val name = "Course ${System.nanoTime()}"
        addCourse(name)

        openTab(R.string.tab_schedule)
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription(str(R.string.add)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(str(R.string.add)).performClick()
        rule.onNodeWithText(name).performClick()                                   // pick the course

        // No day is preselected, so saving now must fail with a message and keep the dialog open.
        rule.onNodeWithText(str(R.string.save)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.error_day_invalid)).fetchSemanticsNodes().isNotEmpty() }

        // Pick Monday by mistake, remove it, then pick Sunday and Tuesday.
        rule.onNodeWithText(dayName(DayOfWeek.MONDAY)).performScrollTo().performClick()
        rule.onNodeWithText(dayName(DayOfWeek.MONDAY)).performClick()
        rule.onNodeWithText(dayName(DayOfWeek.SUNDAY)).performScrollTo().performClick()
        rule.onNodeWithText(dayName(DayOfWeek.TUESDAY)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.save)).performClick()
        waitForDialogToClose()

        rule.waitUntil(5_000) { rule.onAllNodesWithText(name).fetchSemanticsNodes().size == 2 }
        rule.onAllNodesWithTag("timetable_card").assertCountEquals(2)
    }

    @Test fun alternatingClassStartingNextWeekAppearsOnlyInNextWeeksTimetable() {
        val name = "Course ${System.nanoTime()}"
        addCourse(name)

        openTab(R.string.tab_schedule)
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription(str(R.string.add)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(str(R.string.add)).performClick()
        rule.onNodeWithText(name).performClick()
        rule.onNodeWithText(str(R.string.pattern_next_week)).performScrollTo().performClick()
        rule.onNodeWithText(dayName(DayOfWeek.MONDAY)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.save)).performClick()
        waitForDialogToClose()

        // not in this week ...
        rule.onNodeWithContentDescription(str(R.string.next_week)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(name).fetchSemanticsNodes().size == 1 }     // ... but next week
        rule.onNodeWithContentDescription(str(R.string.previous_week)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(name).fetchSemanticsNodes().isEmpty() }
    }
}
