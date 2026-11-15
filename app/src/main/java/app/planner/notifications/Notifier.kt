package app.planner.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.planner.MainActivity
import app.planner.R
import app.planner.data.local.entity.AssignmentEntity
import app.planner.data.local.entity.AttendanceFormat
import app.planner.data.local.entity.CourseEntity
import app.planner.data.local.entity.ExamEntity
import app.planner.data.local.entity.ExamKind
import app.planner.data.local.entity.NotificationKind
import app.planner.data.local.entity.StudyEventEntity
import app.planner.data.local.entity.StudyEventType
import app.planner.ui.study.formatClassSpan
import app.planner.ui.study.formatClock
import app.planner.ui.study.formatMinute

/** Resources in the language chosen inside the app (a receiver has no activity to do this for it). */
internal fun localizedContext(base: Context): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    val first = if (locales.isEmpty) null else locales[0]
    if (first == null) return base
    val config = Configuration(base.resources.configuration)
    config.setLocale(first)
    return base.createConfigurationContext(config)
}

/**
 * Builds and posts every notification. All sounds follow the phone's notification sound unless the user
 * changes a channel in the system settings (which is respected: channels are never recreated or overridden).
 */
class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val c = localizedContext(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_CURRENT, c.getString(R.string.channel_current), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = c.getString(R.string.channel_current_desc) },
                NotificationChannel(CH_ADVANCE, c.getString(R.string.channel_advance), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = c.getString(R.string.channel_advance_desc) },
                NotificationChannel(CH_OVERDUE, c.getString(R.string.channel_overdue), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = c.getString(R.string.channel_overdue_desc) },
                NotificationChannel(CH_TIMER, c.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = c.getString(R.string.channel_timer_desc); setShowBadge(false) },
            )
        )
    }

    fun canPost(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        if (canPost()) manager.notify(id, notification)
    }

    private fun builder(c: Context, channel: String) =
        NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    private fun openApp(route: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (route != null) putExtra(MainActivity.EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(context, route.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun timerAction(action: String): PendingIntent {
        val intent = Intent(context, TimerActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // ---------------- classes ----------------

    fun showClassNow(event: StudyEventEntity, course: CourseEntity?, notificationId: Int) {
        val c = localizedContext(context)
        val locale = c.resources.configuration.locales[0]
        val title = c.getString(if (event.type == StudyEventType.LECTURE) R.string.notif_lecture_now else R.string.notif_section_now)
        val attendance = c.getString(if (event.attendance == AttendanceFormat.ONLINE) R.string.attendance_online else R.string.attendance_physical)
        val body = listOfNotNull(course?.name, attendance, event.topic, event.location ?: event.onlineLink).joinToString(" • ")
        val time = formatClassSpan(event.dayOfWeek, event.startMinute, event.endMinute, event.endDayOffset, locale)
        val n = builder(c, CH_CURRENT)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n$time"))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp("schedule"))
            .apply { course?.let { setColor(it.colorArgb) } }
            .build()
        post(notificationId, n)
    }

    // ---------------- exams ----------------

    fun showExam(exam: ExamEntity, course: CourseEntity?, kind: NotificationKind, notificationId: Int) {
        val c = localizedContext(context)
        val locale = c.resources.configuration.locales[0]
        val title = c.getString(
            when (kind) {
                NotificationKind.AT_TIME -> R.string.notif_exam_now
                NotificationKind.ADVANCE_10M -> R.string.notif_exam_in_10m
                else -> R.string.notif_exam_in_2d
            }
        )
        val kindLabel = c.getString(if (exam.kind == ExamKind.MIDTERM) R.string.exam_midterm else R.string.exam_final)
        val examTitle = exam.title.ifBlank { "$kindLabel ${course?.name ?: ""}".trim() }
        val body = listOfNotNull(examTitle, formatMinute(exam.startMinute, locale), exam.location).joinToString(" • ")
        val n = builder(c, if (kind == NotificationKind.AT_TIME) CH_CURRENT else CH_ADVANCE)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp("exams"))
            .apply { course?.let { setColor(it.colorArgb) } }
            .build()
        post(notificationId, n)
    }

    // ---------------- assignments ----------------

    fun showOverdue(items: List<Pair<AssignmentEntity, CourseEntity?>>) {
        if (items.isEmpty()) return
        val c = localizedContext(context)
        val style = NotificationCompat.InboxStyle()
        items.take(6).forEach { (a, course) -> style.addLine("• ${a.title}" + (course?.let { " — ${it.name}" } ?: "")) }
        if (items.size > 6) style.addLine("+${items.size - 6}")
        val n = builder(c, CH_OVERDUE)
            .setContentTitle(c.getString(R.string.notif_overdue_title, items.size))
            .setContentText(c.getString(R.string.notif_overdue_text))
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp("assignments"))
            .build()
        post(ID_OVERDUE, n)
    }

    fun cancelOverdue() = manager.cancel(ID_OVERDUE)

    // ---------------- study timer ----------------

    fun showTimerRunning(title: String, endAtMs: Long) {
        val c = localizedContext(context)
        val n = builder(c, CH_TIMER)
            .setContentTitle(title)
            .setContentText(c.getString(R.string.timer_running))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setWhen(endAtMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true) // the system counts down by itself, even if the app is closed
            .setContentIntent(openApp("planner"))
            .addAction(0, c.getString(R.string.pause), timerAction(TimerActionReceiver.ACTION_PAUSE))
            .addAction(0, c.getString(R.string.stop), timerAction(TimerActionReceiver.ACTION_STOP))
            .build()
        post(ID_TIMER, n)
    }

    fun showTimerPaused(title: String, remainingMs: Long) {
        val c = localizedContext(context)
        val n = builder(c, CH_TIMER)
            .setContentTitle(title)
            .setContentText(c.getString(R.string.timer_paused, formatClock((remainingMs + 999) / 1000)))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .setContentIntent(openApp("planner"))
            .addAction(0, c.getString(R.string.resume), timerAction(TimerActionReceiver.ACTION_RESUME))
            .addAction(0, c.getString(R.string.stop), timerAction(TimerActionReceiver.ACTION_STOP))
            .build()
        post(ID_TIMER, n)
    }

    fun cancelTimer() = manager.cancel(ID_TIMER)
    fun cancelTimerDone() = manager.cancel(ID_TIMER_DONE)

    fun showTimerDone(title: String) {
        val c = localizedContext(context)
        val n = builder(c, CH_CURRENT)
            .setContentTitle(c.getString(R.string.timer_done_title))
            .setContentText(c.getString(R.string.timer_done_msg, title))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp("planner"))
            .build()
        post(ID_TIMER_DONE, n)
    }

    // ---------------- test ----------------

    fun showTest() {
        val c = localizedContext(context)
        val n = builder(c, CH_CURRENT)
            .setContentTitle(c.getString(R.string.notif_test_title))
            .setContentText(c.getString(R.string.notif_test_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        post(ID_TEST, n)
    }

    companion object {
        const val CH_CURRENT = "current"
        const val CH_ADVANCE = "advance"
        const val CH_OVERDUE = "overdue"
        const val CH_TIMER = "timer"

        const val ID_TIMER = 1
        const val ID_TIMER_DONE = 2
        const val ID_OVERDUE = 3
        const val ID_TEST = 4
    }
}
