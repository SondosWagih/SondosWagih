package app.planner

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.planner.data.local.db.AppDatabase
import app.planner.data.local.entity.*
import app.planner.data.repository.RoomDeviceIdProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase
    private val now = 1_700_000_000_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }
    @After fun tearDown() = db.close()

    private fun course() = CourseEntity(name = "Math", colorArgb = 0xFF2196F3.toInt(), createdAt = now, updatedAt = now)

    @Test fun lectureAndSectionAreIndependentWithEitherAttendance() = runBlocking {
        val cid = db.courseDao().insert(course())
        for (t in StudyEventType.entries) for (a in AttendanceFormat.entries)
            db.studyEventDao().insert(StudyEventEntity(courseId = cid, type = t, attendance = a, dayOfWeek = 1,
                startMinute = 540, endMinute = 630, createdAt = now, updatedAt = now))
        assertEquals(4, db.studyEventDao().observeActive().first().size)
    }

    @Test fun foreignKeyRestrictsHardDeleteOfParent() = runBlocking {
        val cid = db.courseDao().insert(course())
        db.examDao().insert(ExamEntity(courseId = cid, kind = ExamKind.FINAL, title = "Final", dateEpochDay = 20000,
            startMinute = 600, durationMinutes = 120, createdAt = now, updatedAt = now))
        try { db.openHelper.writableDatabase.execSQL("DELETE FROM courses WHERE id = $cid"); fail("expected FK violation") }
        catch (_: SQLiteConstraintException) { /* expected */ }
    }

    @Test fun softDeleteHidesRowButKeepsIt() = runBlocking {
        val cid = db.courseDao().insert(course())
        db.courseDao().softDelete(cid, now + 1)
        assertTrue(db.courseDao().observeActive().first().isEmpty())
        assertNotNull(db.courseDao().get(cid))
    }

    @Test fun duplicateNotificationRecordIsReplacedNotDuplicated() = runBlocking {
        fun rec(code: Int) = NotificationRecordEntity(ownerType = NotificationOwnerType.EXAM, ownerId = 1,
            kind = NotificationKind.ADVANCE_48H, requestCode = code, triggerAtMillis = now,
            status = NotificationStatus.SCHEDULED, createdAt = now, updatedAt = now)
        db.notificationRecordDao().upsert(rec(111))
        db.notificationRecordDao().upsert(rec(222))
        assertEquals(1, db.notificationRecordDao().forOwner(NotificationOwnerType.EXAM, 1).size)
    }

    @Test fun deviceIdIsStable() = runBlocking {
        val p = RoomDeviceIdProvider(db.appSettingDao())
        assertEquals(p.getOrCreate(), p.getOrCreate())
    }
}
