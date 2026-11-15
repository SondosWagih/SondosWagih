package app.planner.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.data.local.entity.CourseEntity

@Composable
fun CoursesScreen(viewModel: StudyViewModel, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CourseEntity?>(null) }
    var deleting by remember { mutableStateOf<CourseEntity?>(null) }

    ListScreen("screen_courses", onAdd = { creating = true }, modifier = modifier) {
        if (ui.courses.isEmpty()) item {
            EmptyState(Icons.Outlined.School, stringResource(R.string.empty_courses), stringResource(R.string.empty_hint))
        }
        items(ui.courses, key = { "course_${it.id}" }) { c ->
            val color = Color(c.colorArgb)
            ScreenCard(onClick = { editing = c }) {
                AvatarCircle(color, c.name.trim().take(1).uppercase())
                Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    c.code?.let { Pill(it, dot = color) }
                    c.instructor?.let { InfoLine(Icons.Outlined.Person, it) }
                }
            }
        }
    }

    if (creating) CourseForm(null, { creating = false }, viewModel::saveCourse)
    editing?.let { c ->
        CourseForm(c, { editing = null }, viewModel::saveCourse, onDelete = { editing = null; deleting = c })
    }
    deleting?.let { c ->
        DeleteDialog(R.string.delete_course_msg, onConfirm = { viewModel.deleteCourse(c.id) }, onDismiss = { deleting = null })
    }
}
