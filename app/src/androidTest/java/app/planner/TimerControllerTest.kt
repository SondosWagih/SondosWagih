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
import app.planner.data.local.entity.NotificationKind
import app.planner.data.local.entity.NotificationOwnerType
import app.planner.data.repository.StudyRepository
import app.planner.data.timer.TimerStore
import app.planner.domain.model.SessionDraft
import app.planner.domain.schedule.AlarmPlanner
import app.planner.notifications.AlarmScheduler
import app.planner.notifications.NotificationScheduler
import app.planner.notifications.Notifier
import app.planner.notifications.TimerController
import java.io.File
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

@RunWith(AndroidJUnit4::class)
class TimerControllerTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: StudyRepository
    private lateinit var alarms: AlarmScheduler
    private lateinit var notifier: Notifier
    private lateinit var store: TimerStore
    private lateinit var scheduler: NotificationScheduler
    private lateinit var controller: TimerController
    private var clock = 0L

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clock = System.currentTimeMillis()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = StudyRepository(db)
        val file = File.createTempFile("timer_ctrl_test", ".preferences_pb").also { it.delete() }
        store = TimerStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) { file }) { clock }
        alarms = AlarmScheduler(context)
        notifier = Notifier(context)
        notifier.createChannels()
        scheduler = NotificationScheduler(db, store, alarms, notifier, now = { clock })
        controller = TimerController(context, store, repo, notifier, scheduler, now = { clock })
    }

    @After fun tearDown() {
        runBlocking {
            store.clear()
            scheduler.rescheduleAll() // withdraws the end alarm
        }
        NotificationManagerCompat.from(context).cancelAll()
        db.close()
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

    @Test fun theTimerLivesInTheNotificationShadeAndEndsWithAnAlarmEvenWithoutTheApp() = runBlocking {
        assumeTrue(notifier.canPost())
        assumeTrue(alarms.canScheduleExact())
        repo.saveSession(SessionDraft(title = "Revise", durationSeconds = 60))
        val session = repo.sessions.first().single()
        val endCode = AlarmPlanner.requestCode(NotificationOwnerType.SESSION, session.id, NotificationKind.TIMER_END)

        controller.start(session)
        assertTrue(waitFor { active(Notifier.ID_TIMER) != null })
        val n = active(Notifier.ID_TIMER)!!.notification
        assertEquals("Revise", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue("the system draws the countdown by itself", n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue("it cannot be swiped away while running", n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue("an alarm will end the session even if the app is closed", alarms.hasPendingAlarm(endCode))

        controller.pause()
        assertNotNull("still shown while paused", active(Notifier.ID_TIMER))
        assertFalse("no alarm while paused", alarms.hasPendingAlarm(endCode))

        controller.resume()
        assertTrue(alarms.hasPendingAlarm(endCode))

        controller.stop()
        assertTrue(waitFor { active(Notifier.ID_TIMER) == null })
        assertFalse(alarms.hasPendingAlarm(endCode))
    }

    @Test fun whenTimeIsUpTheSessionIsMarkedDoneAndAnnounced() = runBlocking {
        assumeTrue(notifier.canPost())
        repo.saveSession(SessionDraft(durationSeconds = 30))
        val session = repo.sessions.first().single()

        controller.start(session)
        assertNull("not finished yet", controller.completeIfExpired())

        clock += 31_000
        assertEquals(session.id, controller.completeIfExpired())
        assertTrue(repo.sessions.first().single().completed)
        assertTrue(waitFor { active(Notifier.ID_TIMER_DONE) != null })
        assertTrue(waitFor { active(Notifier.ID_TIMER) == null })
        assertNull("it only completes once", controller.completeIfExpired())
    }
}
