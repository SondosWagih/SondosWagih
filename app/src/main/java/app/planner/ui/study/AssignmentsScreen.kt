package app.planner.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.data.local.entity.AssignmentEntity
import app.planner.data.local.entity.CourseEntity
import app.planner.data.local.entity.Priority

private fun priorityColor(p: Priority) = when (p) {
    Priority.HIGH -> Color(0xFFE53935)
    Priority.MEDIUM -> Color(0xFFFFB300)
    Priority.LOW -> Color(0xFF43A047)
}

@Composable
fun AssignmentsScreen(viewModel: StudyViewModel, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AssignmentEntity?>(null) }
    var deleting by remember { mutableStateOf<AssignmentEntity?>(null) }
    val courseById = remember(ui.courses) { ui.courses.associateBy { it.id } }
    val today = todayEpochDay()
    val pending = ui.assignments.filter { !it.completed }
    val done = ui.assignments.filter { it.completed }

    if (ui.courses.isEmpty()) {
        CenteredMessage("screen_assignments", stringResource(R.string.add_course_first))
    } else {
        ListScreen("screen_assignments", onAdd = { creating = true }, modifier = modifier) {
            if (ui.assignments.isEmpty()) item {
                EmptyState(Icons.Outlined.TaskAlt, stringResource(R.string.empty_assignments), stringResource(R.string.empty_hint))
            }
            if (pending.isNotEmpty()) {
                item(key = "h_todo") { SectionHeader("${stringResource(R.string.assignments_todo)} (${pending.size})") }
                items(pending, key = { "assignment_${it.id}" }) { a ->
                    AssignmentCard(a, courseById[a.courseId], overdue = a.dueEpochDay < today,
                        onClick = { editing = a }, onToggle = { viewModel.setAssignmentCompleted(a.id, it) })
                }
            }
            if (done.isNotEmpty()) {
                item(key = "h_done") { SectionHeader("${stringResource(R.string.assignments_done)} (${done.size})") }
                items(done, key = { "assignment_${it.id}" }) { a ->
                    AssignmentCard(a, courseById[a.courseId], overdue = false,
                        onClick = { editing = a }, onToggle = { viewModel.setAssignmentCompleted(a.id, it) })
                }
            }
        }
    }

    if (creating) AssignmentForm(null, ui.courses, { creating = false }, viewModel::saveAssignment)
    editing?.let { a ->
        AssignmentForm(a, ui.courses, { editing = null }, viewModel::saveAssignment, onDelete = { editing = null; deleting = a })
    }
    deleting?.let { a ->
        DeleteDialog(R.string.delete_item_msg, onConfirm = { viewModel.deleteAssignment(a.id) }, onDismiss = { deleting = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssignmentCard(
    a: AssignmentEntity,
    course: CourseEntity?,
    overdue: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ScreenCard(onClick = onClick) {
        Checkbox(checked = a.completed, onCheckedChange = onToggle)
        Column(Modifier.weight(1f).padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                a.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (a.completed) TextDecoration.LineThrough else null,
                color = if (a.completed) muted else MaterialTheme.colorScheme.onSurface,
            )
            InfoLine(
                Icons.Outlined.CalendarToday,
                formatDate(a.dueEpochDay) + (a.dueMinute?.let { " • ${formatMinute(it)}" } ?: ""),
                color = if (overdue) MaterialTheme.colorScheme.error else muted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                course?.let { Pill(it.name, dot = Color(it.colorArgb)) }
                Pill(stringResource(a.priority.label()), dot = priorityColor(a.priority))
                if (overdue) Pill(
                    stringResource(R.string.overdue),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
