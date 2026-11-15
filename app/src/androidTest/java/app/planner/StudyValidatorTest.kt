package app.planner

import app.planner.data.local.entity.*
import app.planner.domain.model.*
import app.planner.domain.validation.StudyValidator
import app.planner.domain.validation.ValidationError.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyValidatorTest {
    private fun event(course: Long? = 1, days: Set<Int> = setOf(1), start: Int = 540, end: Int = 630) =
        StudyEventDraft(courseId = course, type = StudyEventType.LECTURE, attendance = AttendanceFormat.ONLINE,
            days = days, startMinute = start, endMinute = end)

    @Test fun courseNeedsAName() {
        assertEquals(NAME_REQUIRED, StudyValidator.course(CourseDraft(name = "  ", colorArgb = 0)))
        assertNull(StudyValidator.course(CourseDraft(name = "Math", colorArgb = 0)))
    }

    @Test fun eventNeedsAtLeastOneValidDay() {
        assertNull(StudyValidator.studyEvent(event(days = setOf(1, 3, 5))))
        assertEquals(DAY_INVALID, StudyValidator.studyEvent(event(days = emptySet())))
        assertEquals(DAY_INVALID, StudyValidator.studyEvent(event(days = setOf(2, 8))))
    }

    @Test fun eventTimeRules() {
        assertEquals(COURSE_REQUIRED, StudyValidator.studyEvent(event(course = null)))
        assertEquals(TIME_INVALID, StudyValidator.studyEvent(event(end = 1440)))
        assertEquals(END_BEFORE_START, StudyValidator.studyEvent(event(start = 600, end = 600)))
    }

    @Test fun examTitleIsOptional() {
        fun exam(title: String = "", duration: Int = 120) =
            ExamDraft(courseId = 1, kind = ExamKind.FINAL, title = title, dateEpochDay = 20000, startMinute = 600, durationMinutes = duration)
        assertNull(StudyValidator.exam(exam()))
        assertNull(StudyValidator.exam(exam(title = "Practical")))
        assertEquals(DURATION_INVALID, StudyValidator.exam(exam(duration = 0)))
    }

    @Test fun assignmentTimeIsOptionalButValidated() {
        fun a(minute: Int?) = AssignmentDraft(courseId = 1, title = "HW", dueEpochDay = 20000, dueMinute = minute)
        assertNull(StudyValidator.assignment(a(null)))
        assertEquals(TIME_INVALID, StudyValidator.assignment(a(1440)))
    }

    @Test fun sessionLengthMustBeBetweenOneSecondAndTwentyFourHours() {
        fun s(sec: Int) = SessionDraft(durationSeconds = sec)
        assertEquals(DURATION_INVALID, StudyValidator.session(s(0)))
        assertNull(StudyValidator.session(s(1)))
        assertNull(StudyValidator.session(s(86_400)))
        assertEquals(DURATION_INVALID, StudyValidator.session(s(86_401)))
    }

    private fun overnight(start: Int, end: Int, offset: Int) =
        event(start = start, end = end).copy(endDayOffset = offset)

    @Test fun aClassThatEndsTheNextDayNeedsAnEndBeforeItsStart() {
        // Friday 11 PM -> Saturday 12:30 AM, 12 AM and 1 AM
        assertNull(StudyValidator.studyEvent(overnight(23 * 60, 30, 1)))
        assertNull(StudyValidator.studyEvent(overnight(23 * 60, 0, 1)))   // ends exactly at midnight
        assertNull(StudyValidator.studyEvent(overnight(23 * 60, 60, 1)))
        // "next day" with an end after the start would last more than 24 hours
        assertEquals(END_BEFORE_START, StudyValidator.studyEvent(overnight(8 * 60, 9 * 60, 1)))
        assertEquals(END_BEFORE_START, StudyValidator.studyEvent(overnight(23 * 60, 23 * 60, 1)))
    }

    @Test fun theSameDayStillNeedsAnEndAfterTheStart() {
        // 11 PM -> 1 AM is only valid when the end day is the next day: no guessing
        assertEquals(END_BEFORE_START, StudyValidator.studyEvent(overnight(23 * 60, 60, 0)))
        assertEquals(END_BEFORE_START, StudyValidator.studyEvent(overnight(10 * 60, 9 * 60, 0)))
    }

    @Test fun onlyTheSameOrTheNextDayExist() {
        assertEquals(TIME_INVALID, StudyValidator.studyEvent(overnight(23 * 60, 60, 2)))
        assertEquals(TIME_INVALID, StudyValidator.studyEvent(overnight(23 * 60, 60, -1)))
    }

    @Test fun classLengthAcrossMidnight() {
        assertEquals(120, StudyValidator.classMinutes(23 * 60, 60, 1))
        assertEquals(60, StudyValidator.classMinutes(23 * 60, 0, 1))
        assertEquals(90, StudyValidator.classMinutes(8 * 60, 9 * 60 + 30, 0))
        assertEquals(0, StudyValidator.classMinutes(600, 540, 0))
    }
}
