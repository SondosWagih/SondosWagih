package app.planner.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.notifications.Notifier
import app.planner.notifications.PermissionChecker
import app.planner.ui.study.formatMinute
import app.planner.ui.study.shortDayName
import java.time.Instant
import java.time.ZoneId

/**
 * Everything the phone must allow for reminders to arrive while the app is closed, with a button that opens the
 * exact system screen for each one, plus the scheduling status and a test notification.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationSettingsSection(viewModel: NotificationsViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    // Re-read the permissions every time the user comes back from a system settings screen.
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val status = remember(tick) { PermissionChecker.read(context) }
    val diag by viewModel.diagnostics.collectAsStateWithLifecycle()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.notifications_section),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        PermissionRow(R.string.perm_notifications, null, status.notifications) { PermissionChecker.openNotificationSettings(context) }
        PermissionRow(R.string.perm_exact, R.string.perm_exact_hint, status.exactAlarms) { PermissionChecker.openExactAlarmSettings(context) }
        PermissionRow(R.string.perm_battery, R.string.perm_battery_hint, status.battery) { PermissionChecker.openBatterySettings(context) }

        Text(stringResource(R.string.sound_settings), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.sound_settings_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChannelButton(R.string.channel_current, Notifier.CH_CURRENT)
            ChannelButton(R.string.channel_advance, Notifier.CH_ADVANCE)
            ChannelButton(R.string.channel_overdue, Notifier.CH_OVERDUE)
            ChannelButton(R.string.channel_timer, Notifier.CH_TIMER)
        }

        Text(stringResource(R.string.diag_title), style = MaterialTheme.typography.titleSmall)
        val summary = when {
            diag.scheduled == 0 && diag.blocked == 0 -> stringResource(R.string.diag_none)
            else -> buildString {
                append(stringResource(R.string.diag_count, diag.scheduled))
                diag.nextTriggerMillis?.let { append(" • ").append(stringResource(R.string.diag_next, formatTrigger(it))) }
            }
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium)
        if (diag.blocked > 0) {
            Text(
                stringResource(R.string.diag_blocked, diag.blocked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        val lastDue = diag.lastDueMillis
        val lastArrived = diag.lastArrivedMillis
        if (lastDue != null && lastArrived != null) {
            Text(
                stringResource(R.string.diag_last, formatTrigger(lastDue), formatTrigger(lastArrived)),
                style = MaterialTheme.typography.bodyMedium,
            )
            val lateMinutes = (lastArrived - lastDue) / 60_000
            if (lateMinutes >= 1) {
                Text(
                    stringResource(R.string.diag_late, lateMinutes.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.rescheduleNow() }) { Text(stringResource(R.string.diag_reschedule)) }
            Button(onClick = { viewModel.sendTest() }) { Text(stringResource(R.string.diag_test)) }
        }
    }
}

@Composable
private fun ChannelButton(@StringRes label: Int, channelId: String) {
    val context = LocalContext.current
    OutlinedButton(onClick = { PermissionChecker.openChannelSettings(context, channelId) }) { Text(stringResource(label)) }
}

@Composable
private fun PermissionRow(@StringRes title: Int, @StringRes hint: Int?, granted: Boolean, onAllow: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (granted) R.string.perm_allowed else R.string.perm_not_allowed),
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            if (hint != null && !granted) {
                Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!granted) Button(onClick = onAllow) { Text(stringResource(R.string.perm_allow)) }
    }
}

private fun formatTrigger(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return "${shortDayName(t.dayOfWeek.value)} ${t.dayOfMonth} • ${formatMinute(t.hour * 60 + t.minute)}"
}
