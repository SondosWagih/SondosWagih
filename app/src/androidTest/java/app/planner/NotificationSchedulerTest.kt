package app.planner

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.planner.data.local.db.AppDatabase
import app.planner.data.local.entity.*
import app.planner.data.repository.StudyRepository
import app.planner.data.timer.TimerStore
import app.planner.domain.model.*
import app.planner.notifications.AlarmHandler
import app.planner.notifications.AlarmScheduler
import app.planner.notifications.NotificationScheduler
import app.planner.notifications.Notifier
import app.planner.notifications.TimerController
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real AlarmManager and real notifications on the emulator (the data lives in an in-memory database). */
@RunWith(AndroidJUnit4::class)
class NotificationSchedulerTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: StudyRepository
    private lateinit var alarms: AlarmScheduler
    private lateinit var notifier: Notifier
    private lateinit var timerStore: TimerStore
    private lateinit var scheduler: NotificationScheduler

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = StudyRepository(db)
        val file = File.createTempFile("sched_test", ".preferences_pb").also { it.delete() }
        timerStore = TimerStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) { file })
        alarms = AlarmScheduler(context)
        notifier = Notifier(context)
        notifier.createChannels()
        scheduler = NotificationScheduler(db, timerStore, alarms, notifier)
    }

    @After fun tearDown() {
        runBlocking { db.notificationRecordDao().allActive().forEach { alarms.cancel(it.requestCode) } }
        NotificationManagerCompat.from(context).cancelAll()
        db.close()
    }

    private suspend fun newCourse(): Long {
        repo.saveCourse(CourseDraft(name = "Math", colorArgb = 0xFF3F6FD8.toInt()))
        return repo.courses.first().single().id
    }

    private fun active(id: Int) =
        context.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull { it.id == id }

    private fun waitFor(timeoutMs: Long = 3_000, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    @Test fun alarmsAreScheduledOnceAndCancelledWhenTheDataIsDeleted() = runBlocking {
        assumeTrue(alarms.canScheduleExact())
        val cid = newCourse()
        repo.saveExam(ExamDraft(courseId = cid, kind = ExamKind.FINAL, dateEpochDay = LocalDate.now().plusDays(10).toEpochDay(), startMinute = 600, durationMinutes = 90))
        repo.saveEvent(StudyEventDraft(courseId = cid, type = StudyEventType.LECTURE, attendance = AttendanceFormat.PHYSICAL, days = setOf(1, 3), startMinute = 480, endMinute = 570))

        scheduler.rescheduleAll()
        val dao = db.notificationRecordDao()
        var active = dao.allActive()
        assertEquals("exam: 48h, 10 min and at the time", 3, active.count { it.ownerType == NotificationOwnerType.EXAM })
        assertEquals("one alarm per class", 2, active.count { it.ownerType == NotificationOwnerType.STUDY_EVENT })
        assertTrue(active.all { it.status == NotificationStatus.SCHEDULED })
        assertTrue("every record has a real alarm behind it", active.all { alarms.hasPendingAlarm(it.requestCode) })

        scheduler.rescheduleAll() // again: must not duplicate anything
        assertEquals(5, dao.allActive().size)

        repo.deleteExam(repo.exams.first().single().id)
        repo.deleteEvent(repo.events.first().first().id)
        scheduler.rescheduleAll()
        active = dao.allActive()
        assertEquals(1, active.size)
        val cancelled = dao.all().filter { it.status == NotificationStatus.CANCELLED }
        assertEquals(4, cancelled.size)
        assertTrue("cancelled alarms are really gone", cancelled.none { alarms.hasPendingAlarm(it.requestCode) })
    }

    @Test fun withoutExactAlarmPermissionNothingIsScheduledAndTheStatusSaysSo() = runBlocking {
        val cid = newCourse()
        repo.saveExam(ExamDraft(courseId = cid, kind = ExamKind.FINAL, dateEpochDay = LocalDate.now().plusDays(10).toEpochDay(), startMinute = 600, durationMinutes = 90))
        val noPermission = object : AlarmScheduler(context) {
            override fun canScheduleExact() = false
        }
        NotificationScheduler(db, timerStore, noPermission, notifier).rescheduleAll()

        val records = db.notificationRecordDao().allActive()
        assertEquals(3, records.size)
        assertTrue("no silent downgrade to inexact alarms", records.all { it.status == NotificationStatus.BLOCKED_NO_PERMISSION })
        assertTrue(records.none { noPermission.hasPendingAlarm(it.requestCode) })
    }

    @Test fun classNotificationSaysYouHaveALectureNow() = runBlocking {
        assumeTrue(notifier.canPost())
        val cid = newCourse()
        repo.saveEvent(StudyEventDraft(courseId = cid, type = StudyEventType.LECTURE, attendance = AttendanceFormat.ONLINE, days = setOf(3), startMinute = 480, endMinute = 570, topic = "Electric"))
        notifier.showClassNow(repo.events.first().single(), db.courseDao().get(cid), 4242)

        assertTrue(waitFor { active(4242) != null })
        val extras = active(4242)!!.notification.extras
        assertEquals(context.getString(R.string.notif_lecture_now), extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        val text = extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text.contains("Math") && text.contains("Electric"))
    }

    @Test fun overdueSummaryAppearsForUnfinishedAssignmentsAndStopsOnceTheyAreTicked() = runBlocking {
        assumeTrue(notifier.canPost())
        val cid = newCourse()
        repo.saveAssignment(AssignmentDraft(courseId = cid, title = "Report", dueEpochDay = LocalDate.now().minusDays(1).toEpochDay()))
        val handler = AlarmHandler(db, notifier, scheduler, TimerController(context, timerStore, repo, notifier, scheduler))

        handler.showOverdueNow()
        assertTrue(waitFor { active(Notifier.ID_OVERDUE) != null })

        scheduler.rescheduleAll()
        assertEquals("reminders are planned while it is unfinished", 1, db.notificationRecordDao().allActive().count { it.kind == NotificationKind.OVERDUE })

        repo.setAssignmentCompleted(repo.assignments.first().single().id, true)
        scheduler.rescheduleAll()
        assertEquals("ticking it off stops the reminders", 0, db.notificationRecordDao().allActive().count { it.kind == NotificationKind.OVERDUE })
        assertTrue("and removes the notification", waitFor { active(Notifier.ID_OVERDUE) == null })
    }
}
