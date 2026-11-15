package app.planner.ui.study

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import app.planner.R
import app.planner.data.local.entity.*
import app.planner.domain.model.*
import app.planner.domain.schedule.WeekMath
import app.planner.domain.validation.ValidationError

typealias SaveHandler<D> = (D, (ValidationError?) -> Unit) -> Unit

@Composable
fun CourseForm(initial: CourseEntity?, onDismiss: () -> Unit, onSave: SaveHandler<CourseDraft>, onDelete: (() -> Unit)? = null) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var code by remember { mutableStateOf(initial?.code ?: "") }
    var instructor by remember { mutableStateOf(initial?.instructor ?: "") }
    var color by remember { mutableIntStateOf(initial?.colorArgb ?: DefaultCourseColor) }
    var error by remember { mutableStateOf<ValidationError?>(null) }
    FormDialog(stringResource(R.string.form_course), error, onDismiss, onSave = {
        onSave(CourseDraft(initial?.id, name, code, instructor, color)) { e -> if (e == null) onDismiss() else error = e }
    }, onDelete = onDelete) {
        TextInput(stringResource(R.string.field_name), name) { name = it }
        TextInput(stringResource(R.string.field_code), code, placeholder = stringResource(R.string.field_code_hint)) { code = it }
        TextInput(stringResource(R.string.field_instructor), instructor) { instructor = it }
        ColorPicker(stringResource(R.string.field_color), color) { color = it }
    }
}

private enum class RepeatPattern { EVERY_WEEK, THIS_WEEK, NEXT_WEEK }

@StringRes
private fun RepeatPattern.label() = when (this) {
    RepeatPattern.EVERY_WEEK -> R.string.pattern_every_week
    RepeatPattern.THIS_WEEK -> R.string.pattern_this_week
    RepeatPattern.NEXT_WEEK -> R.string.pattern_next_week
}

@Composable
fun EventForm(
    initial: StudyEventEntity?,
    courses: List<CourseEntity>,
    onDismiss: () -> Unit,
    onSave: SaveHandler<StudyEventDraft>,
    onDelete: (() -> Unit)? = null,
) {
    val initialPattern = when {
        initial == null || initial.repeatEveryWeeks <= 1 -> RepeatPattern.EVERY_WEEK
        WeekMath.occursInWeek(initial.repeatEveryWeeks, initial.anchorWeekStart, currentWeekStart()) -> RepeatPattern.THIS_WEEK
        else -> RepeatPattern.NEXT_WEEK
    }
    var courseId by remember { mutableStateOf(initial?.courseId ?: courses.firstOrNull()?.id) }
    var type by remember { mutableStateOf(initial?.type ?: StudyEventType.LECTURE) }
    var attendance by remember { mutableStateOf(initial?.attendance ?: AttendanceFormat.PHYSICAL) }
    var pattern by remember { mutableStateOf(initialPattern) }
    var days by remember { mutableStateOf(initial?.let { setOf(it.dayOfWeek) } ?: emptySet<Int>()) }
    var start by remember { mutableIntStateOf(initial?.startMinute ?: DEFAULT_CLASS_START_MINUTE) }
    var end by remember { mutableIntStateOf(initial?.endMinute ?: DEFAULT_CLASS_END_MINUTE) }
    var endDayOffset by remember { mutableIntStateOf(initial?.endDayOffset ?: 0) }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var link by remember { mutableStateOf(initial?.onlineLink ?: "") }
    var topic by remember { mutableStateOf(initial?.topic ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var error by remember { mutableStateOf<ValidationError?>(null) }
    FormDialog(stringResource(R.string.form_class), error, onDismiss, onSave = {
        onSave(
            StudyEventDraft(
                id = initial?.id, courseId = courseId, type = type, attendance = attendance, days = days,
                startMinute = start, endMinute = end, endDayOffset = endDayOffset, location = location, onlineLink = link, notes = notes,
                repeatEveryWeeks = if (pattern == RepeatPattern.EVERY_WEEK) 1 else 2,
                startsThisWeek = pattern != RepeatPattern.NEXT_WEEK,
                topic = topic,
            )
        ) { e -> if (e == null) onDismiss() else error = e }
    }, onDelete = onDelete) {
        CourseChoice(courses, courseId, allowNone = false) { courseId = it }
        ChoiceRow(stringResource(R.string.field_type), StudyEventType.entries.toList(), type, { type = it }) { stringResource(it.label()) }
        ChoiceRow(stringResource(R.string.field_attendance), AttendanceFormat.entries.toList(), attendance, { attendance = it }) { stringResource(it.label()) }
        ChoiceRow(stringResource(R.string.field_repeat), RepeatPattern.entries.toList(), pattern, { pattern = it }) { stringResource(it.label()) }
        if (pattern != RepeatPattern.EVERY_WEEK) {
            Text(
                stringResource(R.string.pattern_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MultiChoiceFlow(
            label = stringResource(R.string.field_days),
            hint = stringResource(R.string.days_hint),
            options = weekDaysInOrder(),
            selected = days,
            onToggle = { d -> days = if (d in days) days - d else days + d },
            optionLabel = { dayName(it) },
        )
        TimeField(stringResource(R.string.field_start), start, { start = it; endDayOffset = if (end <= start) 1 else 0 })
        TimeField(stringResource(R.string.field_end), end, { end = it; endDayOffset = if (end <= start) 1 else 0 })
        ChoiceRow(stringResource(R.string.field_end_day), listOf(0, 1), endDayOffset, { endDayOffset = it }) {
            stringResource(if (it == 0) R.string.end_same_day else R.string.end_next_day)
        }
        // Spell out exactly when each selected day starts and ends, with day names.
        val orderedDays = weekDaysInOrder().filter { it in days }
        if (orderedDays.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                orderedDays.forEach { d ->
                    Text(
                        formatClassSpan(d, start, end, endDayOffset, withDay = true),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // 12 AM belongs to the day that STARTS at that moment (Saturday 12 AM is right after Friday night).
                if (start < 60) {
                    val first = orderedDays.first()
                    Text(
                        stringResource(R.string.midnight_hint, dayName(first), dayName((first + 5) % 7 + 1)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextInput(stringResource(R.string.field_location), location) { location = it }
        TextInput(stringResource(R.string.field_link), link) { link = it }
        TextInput(stringResource(R.string.field_topic), topic, placeholder = stringResource(R.string.field_topic_hint)) { topic = it }
        TextInput(stringResource(R.string.field_notes), notes, singleLine = false) { notes = it }
    }
}

@Composable
fun ExamForm(
    initial: ExamEntity?,
    courses: List<CourseEntity>,
    onDismiss: () -> Unit,
    onSave: SaveHandler<ExamDraft>,
    onDelete: (() -> Unit)? = null,
) {
    var courseId by remember { mutableStateOf(initial?.courseId ?: courses.firstOrNull()?.id) }
    var kind by remember { mutableStateOf(initial?.kind ?: ExamKind.MIDTERM) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember { mutableLongStateOf(initial?.dateEpochDay ?: todayEpochDay()) }
    var start by remember { mutableIntStateOf(initial?.startMinute ?: 540) }
    var duration by remember { mutableStateOf((initial?.durationMinutes ?: 120).toString()) }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var error by remember { mutableStateOf<ValidationError?>(null) }
    val autoTitle = "${stringResource(kind.label())} ${courses.firstOrNull { it.id == courseId }?.name ?: ""}".trim()
    FormDialog(stringResource(R.string.form_exam), error, onDismiss, onSave = {
        onSave(ExamDraft(initial?.id, courseId, kind, title, date, start, duration.trim().toIntOrNull() ?: 0, location, notes)) { e ->
            if (e == null) onDismiss() else error = e
        }
    }, onDelete = onDelete) {
        CourseChoice(courses, courseId, allowNone = false) { courseId = it }
        ChoiceRow(stringResource(R.string.field_exam_kind), ExamKind.entries.toList(), kind, { kind = it }) { stringResource(it.label()) }
        TextInput(stringResource(R.string.field_title_optional), title, placeholder = autoTitle) { title = it }
        DateField(stringResource(R.string.field_date), date) { date = it }
        TimeField(stringResource(R.string.field_start), start, { start = it })
        TextInput(stringResource(R.string.field_duration), duration, numeric = true) { duration = it }
        TextInput(stringResource(R.string.field_location), location) { location = it }
        TextInput(stringResource(R.string.field_notes), notes, singleLine = false) { notes = it }
    }
}

@Composable
fun AssignmentForm(
    initial: AssignmentEntity?,
    courses: List<CourseEntity>,
    onDismiss: () -> Unit,
    onSave: SaveHandler<AssignmentDraft>,
    onDelete: (() -> Unit)? = null,
) {
    var courseId by remember { mutableStateOf(initial?.courseId ?: courses.firstOrNull()?.id) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var due by remember { mutableLongStateOf(initial?.dueEpochDay ?: todayEpochDay()) }
    var dueMinute by remember { mutableStateOf(initial?.dueMinute) }
    var priority by remember { mutableStateOf(initial?.priority ?: Priority.MEDIUM) }
    var error by remember { mutableStateOf<ValidationError?>(null) }
    FormDialog(stringResource(R.string.form_assignment), error, onDismiss, onSave = {
        onSave(AssignmentDraft(initial?.id, courseId, title, description, due, dueMinute, priority)) { e ->
            if (e == null) onDismiss() else error = e
        }
    }, onDelete = onDelete) {
        CourseChoice(courses, courseId, allowNone = false) { courseId = it }
        TextInput(stringResource(R.string.field_title), title) { title = it }
        TextInput(stringResource(R.string.field_description), description, singleLine = false) { description = it }
        DateField(stringResource(R.string.field_date), due) { due = it }
        TimeField(stringResource(R.string.field_time), dueMinute, { dueMinute = it }, onClear = { dueMinute = null })
        ChoiceRow(stringResource(R.string.field_priority), Priority.entries.toList(), priority, { priority = it }) { stringResource(it.label()) }
    }
}

@Composable
fun SessionForm(
    initial: StudySessionEntity?,
    courses: List<CourseEntity>,
    onDismiss: () -> Unit,
    onSave: SaveHandler<SessionDraft>,
    onDelete: (() -> Unit)? = null,
) {
    val total = initial?.totalSeconds ?: (25 * 60)
    var courseId by remember { mutableStateOf(initial?.courseId) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var hours by remember { mutableStateOf((total / 3600).toString()) }
    var minutes by remember { mutableStateOf((total % 3600 / 60).toString()) }
    var seconds by remember { mutableStateOf((total % 60).toString()) }
    var error by remember { mutableStateOf<ValidationError?>(null) }
    val autoTitle = courses.firstOrNull { it.id == courseId }?.name ?: stringResource(R.string.session_default_title)
    FormDialog(stringResource(R.string.form_session), error, onDismiss, onSave = {
        val secs = (hours.toIntOrNull() ?: 0) * 3600 + (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
        onSave(SessionDraft(initial?.id, courseId, title, secs)) { e -> if (e == null) onDismiss() else error = e }
    }, onDelete = onDelete) {
        CourseChoice(courses, courseId, allowNone = true) { courseId = it }
        TextInput(stringResource(R.string.field_title_optional), title, placeholder = autoTitle) { title = it }
        DurationFields(hours, minutes, seconds, { hours = it }, { minutes = it }, { seconds = it })
    }
}
