package app.planner.ui.study

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.planner.R
import app.planner.data.local.entity.CourseEntity
import app.planner.domain.validation.ValidationError

private const val MILLIS_PER_DAY = 86_400_000L

@Composable
fun FormDialog(
    title: String,
    error: ValidationError?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
                if (error != null) Text(stringResource(error.message()), color = MaterialTheme.colorScheme.error)
                if (onDelete != null) TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun TextInput(
    label: String,
    value: String,
    placeholder: String? = null,
    singleLine: Boolean = true,
    numeric: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(optionLabel(o)) })
            }
        }
    }
}

/** Several options can be selected; tapping a selected one removes it again. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MultiChoiceFlow(
    label: String,
    hint: String?,
    options: List<T>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    optionLabel: (T) -> String,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(optionLabel(o)) })
            }
        }
    }
}

/** Course picker. allowNone adds a "None" option (used by study sessions). */
@Composable
fun CourseChoice(courses: List<CourseEntity>, selected: Long?, allowNone: Boolean, onSelect: (Long?) -> Unit) {
    val ids: List<Long?> = courses.map { it.id }
    val options: List<Long?> = if (allowNone) listOf<Long?>(null) + ids else ids
    ChoiceRow(stringResource(R.string.form_course), options, selected, onSelect) { id ->
        if (id == null) stringResource(R.string.none) else courses.firstOrNull { it.id == id }?.name ?: "—"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, minute: Int?, onPick: (Int) -> Unit, onClear: (() -> Unit)? = null) {
    var show by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { show = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text("$label: ${minute?.let { formatMinute(it) } ?: "—"}")
        }
        if (onClear != null && minute != null) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
        }
    }
    if (show) {
        val initial = minute ?: 540
        // 12-hour clock with AM/PM; state.hour is still 0..23.
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = { onPick(state.hour * 60 + state.minute); show = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, epochDay: Long, onPick: (Long) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("$label: ${formatDate(epochDay)}")
    }
    if (show) {
        val state = rememberDatePickerState(initialSelectedDateMillis = epochDay * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Math.floorDiv(it, MILLIS_PER_DAY)) }
                    show = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }
}

/** Hours | minutes | seconds side by side (in RTL, hours sit on the right). */
@Composable
fun DurationFields(
    hours: String, minutes: String, seconds: String,
    onHours: (String) -> Unit, onMinutes: (String) -> Unit, onSeconds: (String) -> Unit,
) {
    Column {
        Text(stringResource(R.string.field_duration_hms), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberBox(stringResource(R.string.unit_hours), hours, onHours, Modifier.weight(1f))
            NumberBox(stringResource(R.string.unit_minutes), minutes, onMinutes, Modifier.weight(1f))
            NumberBox(stringResource(R.string.unit_seconds), seconds, onSeconds, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NumberBox(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(2)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
