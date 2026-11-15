package app.planner

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.planner.data.local.db.AppDatabase
import app.planner.data.local.entity.*
import app.planner.data.repository.StudyRepository
import app.planner.domain.model.*
import app.planner.domain.schedule.WeekMath
import java.time.ZoneId
import app.planner.domain.validation.ValidationError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: StudyRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repo = StudyRepository(db) { 1_700_000_000_000L }
    }
    @After fun tearDown() = db.close()

    private suspend fun newCourse(name: String = "Math"): Long {
        assertNull(repo.saveCourse(CourseDraft(name = name, colorArgb = 0xFF3F6FD8.toInt())))
        return repo.courses.first().first { it.name == name }.id
    }

    private fun event(
        courseId: Long, type: StudyEventType = StudyEventType.LECTURE,
        attendance: AttendanceFormat = AttendanceFormat.PHYSICAL,
        days: Set<Int> = setOf(2), start: Int = 540, end: Int = 630,
    ) = StudyEventDraft(courseId = courseId, type = type, attendance = attendance, days = days, startMinute = start, endMinute = end)

    @Test fun invalidEventIsRejectedAndNothingIsStored() = runBlocking {
        val cid = newCourse()
        assertEquals(ValidationError.END_BEFORE_START, repo.saveEvent(event(cid, start = 600, end = 540)))
        assertEquals(ValidationError.DAY_INVALID, repo.saveEvent(event(cid, days = emptySet())))
        assertTrue(repo.events.first().isEmpty())
    }

    @Test fun lectureAndSectionAcceptBothAttendanceFormats() = runBlocking {
        val cid = newCourse()
        for (t in StudyEventType.entries) for (a in AttendanceFormat.entries) assertNull(repo.saveEvent(event(cid, t, a)))
        assertEquals(4, repo.events.first().size)
    }

    @Test fun selectingSeveralDaysCreatesOneRowPerDay() = runBlocking {
        val cid = newCourse()
        assertNull(repo.saveEvent(event(cid, days = setOf(1, 3, 5))))
        val rows = repo.events.first()
        assertEquals(listOf(1, 3, 5), rows.map { it.dayOfWeek }.sorted())
        assertTrue(rows.all { it.courseId == cid && it.startMinute == 540 && it.endMinute == 630 })
    }

    @Test fun editingKeepsTheRowAndAddsCopiesForNewDays() = runBlocking {
        val cid = newCourse()
        repo.saveEvent(event(cid, days = setOf(2)))
        val saved = repo.events.first().single()
        assertNull(repo.saveEvent(event(cid, type = StudyEventType.SECTION, days = setOf(2, 4)).copy(id = saved.id)))
        val rows = repo.events.first()
        assertEquals(2, rows.size)
        assertEquals(StudyEventType.SECTION, rows.first { it.id == saved.id }.type)
        assertEquals(2, rows.first { it.id == saved.id }.dayOfWeek)
        assertEquals(setOf(2, 4), rows.map { it.dayOfWeek }.toSet())
    }

    @Test fun deselectingTheOriginalDayMovesTheRow() = runBlocking {
        val cid = newCourse()
        repo.saveEvent(event(cid, days = setOf(2)))
        val saved = repo.events.first().single()
        assertNull(repo.saveEvent(event(cid, days = setOf(4)).copy(id = saved.id)))
        val after = repo.events.first().single()
        assertEquals(saved.id, after.id)
        assertEquals(4, after.dayOfWeek)
    }

    @Test fun eventForMissingCourseIsRejected() = runBlocking {
        assertEquals(ValidationError.COURSE_REQUIRED, repo.saveEvent(event(999)))
    }

    @Test fun examCanBeSavedWithoutATitle() = runBlocking {
        val cid = newCourse()
        assertNull(repo.saveExam(ExamDraft(courseId = cid, kind = ExamKind.MIDTERM, dateEpochDay = 20000, startMinute = 600, durationMinutes = 90)))
        assertEquals("", repo.exams.first().single().title)
    }

    @Test fun sessionLengthIsStoredAsMinutesPlusSeconds() = runBlocking {
        assertNull(repo.saveSession(SessionDraft(durationSeconds = 3725))) // 1h 2m 5s, no course, no title
        val s = repo.sessions.first().single()
        assertEquals(62, s.durationMinutes)
        assertEquals(5, s.extraSeconds)
        assertEquals(3725, s.totalSeconds)
    }

    @Test fun deletingACourseHidesAllItsChildren() = runBlocking {
        val cid = newCourse()
        repo.saveEvent(event(cid))
        repo.saveExam(ExamDraft(courseId = cid, kind = ExamKind.MIDTERM, dateEpochDay = 20000, startMinute = 600, durationMinutes = 90))
        repo.saveAssignment(AssignmentDraft(courseId = cid, title = "HW1", dueEpochDay = 20001))
        repo.saveSession(SessionDraft(courseId = cid, durationSeconds = 3600))
        repo.deleteCourse(cid)
        assertTrue(repo.courses.first().isEmpty())
        assertTrue(repo.events.first().isEmpty())
        assertTrue(repo.exams.first().isEmpty())
        assertTrue(repo.assignments.first().isEmpty())
        assertTrue(repo.sessions.first().isEmpty())
    }

    @Test fun assignmentCompletionCanBeToggled() = runBlocking {
        val cid = newCourse()
        repo.saveAssignment(AssignmentDraft(courseId = cid, title = "HW1", dueEpochDay = 20001))
        val id = repo.assignments.first().single().id
        repo.setAssignmentCompleted(id, true)
        assertTrue(repo.assignments.first().single().completed)
        assertNotNull(repo.assignments.first().single().completedAt)
        repo.setAssignmentCompleted(id, false)
        assertFalse(repo.assignments.first().single().completed)
        assertNull(repo.assignments.first().single().completedAt)
    }

    private val fixedNow = 1_700_000_000_000L
    private fun thisWeek() = WeekMath.weekStartOf(WeekMath.epochDayOf(fixedNow, ZoneId.systemDefault()))

    @Test fun alternatingClassStartingThisWeekOccursEverySecondWeek() = runBlocking {
        val cid = newCourse()
        assertNull(repo.saveEvent(event(cid).copy(repeatEveryWeeks = 2, startsThisWeek = true, topic = "Electric")))
        val e = repo.events.first().single()
        assertEquals("Electric", e.topic)
        assertEquals(thisWeek(), e.anchorWeekStart)
        assertTrue(WeekMath.occursInWeek(e.repeatEveryWeeks, e.anchorWeekStart, thisWeek()))
        assertFalse(WeekMath.occursInWeek(e.repeatEveryWeeks, e.anchorWeekStart, thisWeek() + 7))
        assertTrue(WeekMath.occursInWeek(e.repeatEveryWeeks, e.anchorWeekStart, thisWeek() + 14))
    }

    @Test fun alternatingClassStartingNextWeekSkipsThisWeek() = runBlocking {
        val cid = newCourse()
        assertNull(repo.saveEvent(event(cid).copy(repeatEveryWeeks = 2, startsThisWeek = false)))
        val e = repo.events.first().single()
        assertFalse(WeekMath.occursInWeek(e.repeatEveryWeeks, e.anchorWeekStart, thisWeek()))
        assertTrue(WeekMath.occursInWeek(e.repeatEveryWeeks, e.anchorWeekStart, thisWeek() + 7))
        assertFalse(WeekMath.occursInWeek(e.repeatEveryWeeks, e.anchorWeekStart, thisWeek() + 14))
        assertTrue(WeekMath.occursInWeek(e.repeatEveryWeeks, e.anchorWeekStart, thisWeek() + 21))
    }

    @Test fun physicalWeekAndOnlineWeekAreTwoClassesThatNeverOverlapInTime() = runBlocking {
        val cid = newCourse("Techniques")
        // physical on Saturday this week, online on Tuesday next week, then they alternate
        repo.saveEvent(event(cid, type = StudyEventType.SECTION, attendance = AttendanceFormat.PHYSICAL, days = setOf(6))
            .copy(repeatEveryWeeks = 2, startsThisWeek = true))
        repo.saveEvent(event(cid, type = StudyEventType.SECTION, attendance = AttendanceFormat.ONLINE, days = setOf(2))
            .copy(repeatEveryWeeks = 2, startsThisWeek = false))
        val rows = repo.events.first()
        val physical = rows.first { it.attendance == AttendanceFormat.PHYSICAL }
        val online = rows.first { it.attendance == AttendanceFormat.ONLINE }
        for (week in 0..7) {
            val ws = thisWeek() + 7L * week
            val p = WeekMath.occursInWeek(physical.repeatEveryWeeks, physical.anchorWeekStart, ws)
            val o = WeekMath.occursInWeek(online.repeatEveryWeeks, online.anchorWeekStart, ws)
            assertTrue("week $week: exactly one of them", p != o)
        }
    }

    @Test fun everyChangeAsksForTheRemindersToBeReplanned() = runBlocking {
        var calls = 0
        val watched = StudyRepository(db, onChanged = { calls++ }, now = { fixedNow })
        assertNull(watched.saveCourse(CourseDraft(name = "Physics", colorArgb = 1)))
        val cid = watched.courses.first().single().id
        val afterCourse = calls
        assertNull(watched.saveEvent(event(cid)))
        assertTrue(calls > afterCourse)
        val afterEvent = calls
        watched.deleteEvent(watched.events.first().single().id)
        assertTrue(calls > afterEvent)
        val afterDelete = calls
        assertEquals(ValidationError.COURSE_REQUIRED, watched.saveEvent(event(999)))
        assertEquals("invalid input must not trigger a re-plan", afterDelete, calls)
    }

    @Test fun aClassFromElevenPmToOneAmIsAccepted() = runBlocking {
        val cid = newCourse()
        // Friday (ISO 5) 11 PM -> 1 AM
        assertNull(repo.saveEvent(event(cid, days = setOf(5), start = 23 * 60, end = 60).copy(endDayOffset = 1)))
        val e = repo.events.first().single()
        assertEquals(23 * 60, e.startMinute)
        assertEquals(60, e.endMinute)
        assertEquals("it ends on Saturday, the next day", 1, e.endDayOffset)
        // without saying the class ends the next day, 1 AM is just a mistake
        assertEquals(ValidationError.END_BEFORE_START, repo.saveEvent(event(cid, days = setOf(5), start = 23 * 60, end = 60)))
    }
}
