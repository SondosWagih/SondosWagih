package app.planner.ui.study

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.data.local.entity.ExamEntity
import java.time.LocalDate

private sealed interface ExamDialog {
    data class Edit(val item: ExamEntity?) : ExamDialog
}

@Composable
fun ExamsScreen(viewModel: StudyViewModel, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var weekStart by rememberSaveable { mutableStateOf(currentWeekStart()) }
    var dialog by remember { mutableStateOf<ExamDialog?>(null) }
    var deleting by remember { mutableStateOf<ExamEntity?>(null) }
    val courseById = remember(ui.courses) { ui.courses.associateBy { it.id } }

    if (ui.courses.isEmpty()) {
        CenteredMessage("screen_exams", stringResource(R.string.add_course_first))
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
        val items = ui.exams.filter { it.dateEpochDay in weekStart..(weekStart + 6) }.map { x ->
            val course = courseById[x.courseId]
            val end = minOf(x.startMinute + x.durationMinutes, 1440)
            TimetableItem(
                id = x.id,
                dayKey = (x.dateEpochDay - weekStart).toInt(),
                startMinute = x.startMinute,
                endMinute = end,
                title = examDisplayTitle(x, course?.name),
                lines = listOfNotNull(
                    "${formatMinute(x.startMinute)} – ${formatMinute(end)}",
                    if (x.title.isNotBlank()) course?.name else null,
                    x.location,
                ),
                color = course?.let { Color(it.colorArgb) } ?: Color.Gray,
            )
        }
        Box(modifier.fillMaxSize().testTag("screen_exams")) {
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
                    onItemClick = { id -> ui.exams.firstOrNull { it.id == id }?.let { dialog = ExamDialog.Edit(it) } },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            AddButton({ dialog = ExamDialog.Edit(null) }, Modifier.align(Alignment.BottomEnd))
        }
    }

    when (val d = dialog) {
        null -> Unit
        is ExamDialog.Edit -> ExamForm(
            initial = d.item,
            courses = ui.courses,
            onDismiss = { dialog = null },
            onSave = viewModel::saveExam,
            onDelete = d.item?.let { x -> { dialog = null; deleting = x } },
        )
    }
    deleting?.let { x ->
        DeleteDialog(R.string.delete_item_msg, onConfirm = { viewModel.deleteExam(x.id) }, onDismiss = { deleting = null })
    }
}
