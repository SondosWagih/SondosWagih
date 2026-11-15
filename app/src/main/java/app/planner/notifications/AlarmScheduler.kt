package app.planner.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.planner.MainActivity
import app.planner.domain.schedule.PlannedAlarm

/** Thin wrapper over AlarmManager. Never falls back to inexact alarms: without permission it refuses and says so. */
open class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    open fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Returns false if the system refused (exact-alarm permission revoked). */
    fun schedule(alarm: PlannedAlarm): Boolean = try {
        val pi = pendingIntent(alarm.requestCode, alarm)
        if (alarm.highPriority) {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(alarm.triggerAtMillis, openAppPendingIntent()), pi)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, pi)
        }
        true
    } catch (e: SecurityException) {
        false
    }

    fun cancel(requestCode: Int) {
        val pi = pendingIntent(requestCode, null)
        alarmManager.cancel(pi)
        pi.cancel()
    }

    fun hasPendingAlarm(requestCode: Int): Boolean =
        PendingIntent.getBroadcast(context, requestCode, baseIntent(), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null

    private fun baseIntent() =
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_ALARM)

    private fun pendingIntent(requestCode: Int, alarm: PlannedAlarm?): PendingIntent {
        val intent = baseIntent()
        if (alarm != null) {
            intent.putExtra(EXTRA_TYPE, alarm.ownerType.name)
            intent.putExtra(EXTRA_KIND, alarm.kind.name)
            intent.putExtra(EXTRA_ID, alarm.ownerId)
            intent.putExtra(EXTRA_OCCURRENCE, alarm.occurrenceKey)
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val ACTION_ALARM = "app.planner.action.ALARM"
        const val EXTRA_TYPE = "type"
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
        const val EXTRA_OCCURRENCE = "occurrence"
    }
}
