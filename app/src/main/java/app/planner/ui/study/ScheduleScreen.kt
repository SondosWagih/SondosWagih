package app.planner.ui.study

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.data.local.entity.AttendanceFormat
import app.planner.data.local.entity.StudyEventEntity
import app.planner.domain.schedule.ScheduleSlices
import java.time.LocalDate

private sealed interface ScheduleDialog {
    data class Edit(val item: StudyEventEntity?) : ScheduleDialog
}

@Composable
fun ScheduleScreen(viewModel: StudyViewModel, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var weekStart by rememberSaveable { mutableStateOf(currentWeekStart()) }
    var dialog by remember { mutableStateOf<ScheduleDialog?>(null) }
    var deleting by remember { mutableStateOf<StudyEventEntity?>(null) }
    val courseById = remember(ui.courses) { ui.courses.associateBy { it.id } }

    if (ui.courses.isEmpty()) {
        CenteredMessage("screen_schedule", stringResource(R.string.add_course_first))
    } else {
        val today = todayEpochDay()
        val days = (0..6).map { i ->
            val date = LocalDate.ofEpochDay(weekStart + i)
            TimetableDay(
                key = i,
                label = shortDayName(date.dayOfWeek.value),
                sublabel = date.dayOfMonth.toString(),
                highlight = date.toEpochDay() == today,
            )
        }
        // Weekly classes show every week; alternating ones only in their weeks; one that passes midnight is drawn
        // in two columns (evening piece + morning piece).
        val eventById = ui.events.associateBy { it.id }
        val items = ScheduleSlices.forWeek(ui.events, weekStart).mapNotNull { slice ->
            val e = eventById[slice.eventId] ?: return@mapNotNull null
            val course = courseById[e.courseId]
            TimetableItem(
                id = e.id,
                dayKey = slice.dayIndex,
                startMinute = slice.startMinute,
                endMinute = slice.endMinute,
                title = course?.name ?: "—",
                lines = listOfNotNull(
                    formatClassSpan(e.dayOfWeek, e.startMinute, e.endMinute, e.endDayOffset),
                    "${stringResource(e.type.label())} • ${stringResource(e.attendance.label())}",
                    e.topic,
                    e.location,
                ),
                color = course?.let { Color(it.colorArgb) } ?: Color.Gray,
                icon = if (e.attendance == AttendanceFormat.ONLINE) Icons.Outlined.Videocam else Icons.Outlined.Place,
            )
        }
        Box(modifier.fillMaxSize().testTag("screen_schedule")) {
            Column(Modifier.fillMaxSize()) {
                WeekNavigator(
                    weekStart = weekStart,
                    onPrevious = { weekStart -= 7 },
                    onNext = { weekStart += 7 },
                    onToday = { weekStart = currentWeekStart() },
                )
                Timetable(
                    days = days,
                    items = items,
                    onItemClick = { id -> ui.events.firstOrNull { it.id == id }?.let { dialog = ScheduleDialog.Edit(it) } },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            AddButton({ dialog = ScheduleDialog.Edit(null) }, Modifier.align(Alignment.BottomEnd))
        }
    }

    when (val d = dialog) {
        null -> Unit
        is ScheduleDialog.Edit -> EventForm(
            initial = d.item,
            courses = ui.courses,
            onDismiss = { dialog = null },
            onSave = viewModel::saveEvent,
            onDelete = d.item?.let { e -> { dialog = null; deleting = e } },
        )
    }
    deleting?.let { e ->
        DeleteDialog(R.string.delete_item_msg, onConfirm = { viewModel.deleteEvent(e.id) }, onDismiss = { deleting = null })
    }
}
