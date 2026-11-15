package app.planner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.planner.R
import app.planner.notifications.PermissionChecker
import app.planner.notifications.PermissionStatus
import app.planner.ui.navigation.Routes
import app.planner.ui.navigation.StudyDestination
import app.planner.ui.settings.NotificationsViewModel
import app.planner.ui.settings.SettingsScreen
import app.planner.ui.settings.SettingsViewModel
import app.planner.ui.study.AssignmentsScreen
import app.planner.ui.study.CoursesScreen
import app.planner.ui.study.ExamsScreen
import app.planner.ui.study.PlannerScreen
import app.planner.ui.study.ScheduleScreen
import app.planner.ui.study.StudyViewModel
import app.planner.ui.study.TimerViewModel
import app.planner.ui.study.sessionDisplayTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    settingsViewModel: SettingsViewModel,
    studyViewModel: StudyViewModel,
    timerViewModel: TimerViewModel,
    notificationsViewModel: NotificationsViewModel,
    pendingRoute: String? = null,
    onRouteHandled: () -> Unit = {},
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val inSettings = route == Routes.SETTINGS
    val title = StudyDestination.entries.firstOrNull { it.route == route }?.label
        ?: if (inSettings) R.string.settings else R.string.tab_schedule

    // Tapping a notification opens the matching page.
    LaunchedEffect(pendingRoute) {
        val target = pendingRoute ?: return@LaunchedEffect
        if (StudyDestination.entries.any { it.route == target } || target == Routes.SETTINGS) {
            nav.navigate(target) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        onRouteHandled()
    }

    // Reminders only work reliably while the app is closed if the phone allows it: say so, and offer the fix.
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val permissions = remember(tick) { PermissionChecker.read(context) }
    var bannerDismissed by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    if (inSettings) IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!inSettings) IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
                    }
                },
            )
        },
        bottomBar = {
            if (!inSettings) NavigationBar {
                StudyDestination.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = route == dest.route,
                        onClick = {
                            nav.navigate(dest.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.navLabel), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (!permissions.allGood && !bannerDismissed && !inSettings) {
                PermissionBanner(
                    status = permissions,
                    onFix = { nav.navigate(Routes.SETTINGS) },
                    onLater = { bannerDismissed = true },
                )
            }
            NavHost(nav, startDestination = StudyDestination.SCHEDULE.route, modifier = Modifier.weight(1f)) {
                composable(StudyDestination.SCHEDULE.route) { ScheduleScreen(studyViewModel) }
                composable(StudyDestination.COURSES.route) { CoursesScreen(studyViewModel) }
                composable(StudyDestination.EXAMS.route) { ExamsScreen(studyViewModel) }
                composable(StudyDestination.ASSIGNMENTS.route) { AssignmentsScreen(studyViewModel) }
                composable(StudyDestination.PLANNER.route) { PlannerScreen(studyViewModel, timerViewModel) }
                composable(Routes.SETTINGS) { SettingsScreen(settingsViewModel, notificationsViewModel) }
            }
        }
    }

    // A finished timer is announced on any screen (the notification with sound comes from the system as well).
    val finishedId by timerViewModel.finishedSessionId.collectAsStateWithLifecycle()
    val studyState by studyViewModel.state.collectAsStateWithLifecycle()
    finishedId?.let { id ->
        val session = studyState.sessions.firstOrNull { it.id == id }
        val courseName = studyState.courses.firstOrNull { it.id == session?.courseId }?.name
        val title2 = session?.let { sessionDisplayTitle(it, courseName) } ?: stringResource(R.string.session_default_title)
        AlertDialog(
            onDismissRequest = { timerViewModel.dismissFinished() },
            title = { Text(stringResource(R.string.timer_done_title)) },
            text = { Text(stringResource(R.string.timer_done_msg, title2)) },
            confirmButton = { TextButton(onClick = { timerViewModel.dismissFinished() }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun PermissionBanner(status: PermissionStatus, onFix: () -> Unit, onLater: () -> Unit) {
    val missing = mutableListOf<String>()
    if (!status.notifications) missing.add(stringResource(R.string.perm_notifications))
    if (!status.exactAlarms) missing.add(stringResource(R.string.perm_exact))
    if (!status.battery) missing.add(stringResource(R.string.perm_battery))
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.banner_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                stringResource(R.string.banner_missing, missing.joinToString(stringResource(R.string.list_separator))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onFix) { Text(stringResource(R.string.banner_fix)) }
                TextButton(onClick = onLater) { Text(stringResource(R.string.banner_later)) }
            }
        }
    }
}
