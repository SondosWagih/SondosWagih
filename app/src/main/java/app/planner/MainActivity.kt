package app.planner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.planner.domain.model.ThemeMode
import app.planner.ui.AppRoot
import app.planner.ui.settings.NotificationsViewModel
import app.planner.ui.settings.SettingsViewModel
import app.planner.ui.study.StudyViewModel
import app.planner.ui.study.TimerViewModel
import app.planner.ui.theme.AppTheme

class MainActivity : AppCompatActivity() {
    /** Page requested by a tapped notification. */
    private val route = mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the Settings page shows the result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PlannerApp).container
        route.value = intent?.getStringExtra(EXTRA_ROUTE)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container.settingsRepository))
            val studyVm: StudyViewModel = viewModel(factory = StudyViewModel.factory(container.studyRepository))
            val timerVm: TimerViewModel = viewModel(factory = TimerViewModel.factory(container.timerStore, container.timerController))
            val notificationsVm: NotificationsViewModel = viewModel(factory = NotificationsViewModel.factory(container))
            val mode by settingsVm.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AppTheme(dark) {
                AppRoot(
                    settingsViewModel = settingsVm,
                    studyViewModel = studyVm,
                    timerViewModel = timerVm,
                    notificationsViewModel = notificationsVm,
                    pendingRoute = route.value,
                    onRouteHandled = { route.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route.value = intent.getStringExtra(EXTRA_ROUTE)
    }

    override fun onStart() {
        super.onStart()
        // Coming back from a permission screen (or just opening the app) re-checks every alarm.
        (application as PlannerApp).container.notificationScheduler.requestReschedule()
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
