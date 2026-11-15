package app.planner

import android.app.Application
import app.planner.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PlannerApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Work that must outlive any screen (receivers use it too). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        appScope.launch {
            // Make sure the local device identifier exists from the very first launch.
            container.deviceIdProvider.getOrCreate()
            // Bring every alarm and the timer notification back in line with the data.
            container.timerController.restore()
            container.notificationScheduler.rescheduleAll()
        }
    }
}
