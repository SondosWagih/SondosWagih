package app.planner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.planner.data.local.dao.AppSettingDao
import app.planner.data.local.dao.NotificationRecordDao
import app.planner.data.local.entity.NotificationStatus
import app.planner.di.AppContainer
import app.planner.notifications.AlarmHandler
import app.planner.notifications.NotificationScheduler
import app.planner.notifications.Notifier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What is planned right now (the "notification diagnostics" shown in Settings). */
data class NotificationDiagnostics(
    val scheduled: Int = 0,
    val blocked: Int = 0,
    val nextTriggerMillis: Long? = null,
    /** The last alert that fired: when it was due and when it really arrived. */
    val lastDueMillis: Long? = null,
    val lastArrivedMillis: Long? = null,
)

class NotificationsViewModel(
    dao: NotificationRecordDao,
    settings: AppSettingDao,
    private val scheduler: NotificationScheduler,
    private val notifier: Notifier,
) : ViewModel() {
    val diagnostics: StateFlow<NotificationDiagnostics> = combine(
        dao.observeActive(),
        settings.observe(AlarmHandler.KEY_LAST_DUE),
        settings.observe(AlarmHandler.KEY_LAST_ARRIVED),
    ) { list, due, arrived ->
        val scheduled = list.filter { it.status == NotificationStatus.SCHEDULED }
        NotificationDiagnostics(
            scheduled = scheduled.size,
            blocked = list.count { it.status == NotificationStatus.BLOCKED_NO_PERMISSION },
            nextTriggerMillis = scheduled.minOfOrNull { it.triggerAtMillis },
            lastDueMillis = due?.toLongOrNull(),
            lastArrivedMillis = arrived?.toLongOrNull(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationDiagnostics())

    fun rescheduleNow() { viewModelScope.launch { scheduler.rescheduleAll() } }
    fun sendTest() { notifier.showTest() }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                NotificationsViewModel(
                    container.database.notificationRecordDao(),
                    container.database.appSettingDao(),
                    container.notificationScheduler,
                    container.notifier,
                )
            }
        }
    }
}
