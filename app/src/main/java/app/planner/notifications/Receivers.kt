package app.planner.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.planner.PlannerApp
import app.planner.data.local.entity.NotificationKind
import app.planner.data.local.entity.NotificationOwnerType
import kotlinx.coroutines.launch

/** Fires when a planned alarm is due (class, exam, overdue assignments, end of the study timer). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PlannerApp ?: return
        val type = intent.getStringExtra(AlarmScheduler.EXTRA_TYPE)
            ?.let { runCatching { NotificationOwnerType.valueOf(it) }.getOrNull() } ?: return
        val kind = intent.getStringExtra(AlarmScheduler.EXTRA_KIND)
            ?.let { runCatching { NotificationKind.valueOf(it) }.getOrNull() } ?: return
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, 0L)
        val occurrence = intent.getLongExtra(AlarmScheduler.EXTRA_OCCURRENCE, 0L)
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.container.alarmHandler.handle(type, id, kind, occurrence)
            } finally {
                pending.finish()
            }
        }
    }
}

/** The Pause / Resume / Stop buttons on the timer notification. */
class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PlannerApp ?: return
        val action = intent.action ?: return
        val pending = goAsync()
        app.appScope.launch {
            try {
                val timer = app.container.timerController
                when (action) {
                    ACTION_PAUSE -> timer.pause()
                    ACTION_RESUME -> timer.resume()
                    ACTION_STOP -> timer.stop()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "app.planner.action.TIMER_PAUSE"
        const val ACTION_RESUME = "app.planner.action.TIMER_RESUME"
        const val ACTION_STOP = "app.planner.action.TIMER_STOP"
    }
}

/**
 * Restores every alarm after a restart, an app update, a time or time-zone change, or when the exact-alarm
 * permission is granted or revoked.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PlannerApp ?: return
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.container.notifier.createChannels()
                app.container.timerController.restore()
                app.container.notificationScheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
