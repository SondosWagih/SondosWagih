package app.planner.di

import android.content.Context
import app.planner.data.local.db.AppDatabase
import app.planner.data.repository.RoomDeviceIdProvider
import app.planner.data.repository.StudyRepository
import app.planner.data.settings.SettingsRepository
import app.planner.data.settings.settingsDataStore
import app.planner.data.timer.TimerStore
import app.planner.data.timer.timerDataStore
import app.planner.domain.repository.DeviceIdProvider
import app.planner.notifications.AlarmHandler
import app.planner.notifications.AlarmScheduler
import app.planner.notifications.NotificationScheduler
import app.planner.notifications.Notifier
import app.planner.notifications.TimerController

/** Manual dependency container (no DI framework: lean build, fully offline). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.build(appContext)
    val timerStore = TimerStore(appContext.timerDataStore)

    val notifier = Notifier(appContext)
    val alarms = AlarmScheduler(appContext)
    val notificationScheduler = NotificationScheduler(database, timerStore, alarms, notifier)

    /** Every saved change re-plans the reminders. */
    val studyRepository = StudyRepository(database, onChanged = { notificationScheduler.requestReschedule() })

    val timerController = TimerController(appContext, timerStore, studyRepository, notifier, notificationScheduler)
    val alarmHandler = AlarmHandler(database, notifier, notificationScheduler, timerController)

    val settingsRepository = SettingsRepository(appContext.settingsDataStore)
    val deviceIdProvider: DeviceIdProvider = RoomDeviceIdProvider(database.appSettingDao())
}
