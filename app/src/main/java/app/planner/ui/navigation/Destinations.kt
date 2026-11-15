package app.planner.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.ui.graphics.vector.ImageVector
import app.planner.R

object Routes { const val SETTINGS = "settings" }

/** [label] is the page title; [navLabel] is the (shorter) text under the icon in the bottom bar. */
enum class StudyDestination(
    val route: String,
    @StringRes val label: Int,
    @StringRes val navLabel: Int,
    val icon: ImageVector,
    val testTag: String,
) {
    SCHEDULE("schedule", R.string.tab_schedule, R.string.tab_schedule, Icons.Outlined.ViewWeek, "screen_schedule"),
    COURSES("courses", R.string.tab_courses, R.string.tab_courses, Icons.Outlined.School, "screen_courses"),
    EXAMS("exams", R.string.tab_exams, R.string.tab_exams, Icons.Outlined.Event, "screen_exams"),
    ASSIGNMENTS("assignments", R.string.tab_assignments, R.string.nav_assignments, Icons.Outlined.TaskAlt, "screen_assignments"),
    PLANNER("planner", R.string.tab_planner, R.string.tab_planner, Icons.Outlined.Timer, "screen_planner"),
}
