package app.planner

import app.planner.data.local.entity.AttendanceFormat
import app.planner.data.local.entity.StudyEventEntity
import app.planner.data.local.entity.StudyEventType
import app.planner.domain.schedule.ScheduleSlices
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What is drawn in a given week. The week runs Saturday (column 0) to Friday (column 6). */
class ScheduleSlicesTest {
    private val sat = LocalDate.of(2026, 11, 14).toEpochDay() // a Saturday

    private fun event(
        dow: Int, start: Int, end: Int, endDayOffset: Int = 0,
        repeat: Int = 1, anchor: Long = 0L, id: Long = 1,
    ) = StudyEventEntity(
        id = id, courseId = 1, type = StudyEventType.LECTURE, attendance = AttendanceFormat.PHYSICAL,
        dayOfWeek = dow, startMinute = start, endMinute = end, endDayOffset = endDayOffset,
        repeatEveryWeeks = repeat, anchorWeekStart = anchor, createdAt = 0, updatedAt = 0,
    )

    private fun cells(slices: List<app.planner.domain.schedule.ClassSlice>) =
        slices.map { it.dayIndex to it.continuation }.toSet()

    @Test fun anOrdinaryClassIsOnePiece() {
        val slices = ScheduleSlices.forWeek(listOf(event(dow = 2, start = 480, end = 570)), sat) // Tuesday
        assertEquals(1, slices.size)
        assertEquals(3, slices.single().dayIndex) // Sat, Sun, Mon, Tue
        assertEquals(480, slices.single().startMinute)
        assertEquals(570, slices.single().endMinute)
    }

    @Test fun anOvernightClassOnAnOrdinaryDayIsDrawnInTwoColumns() {
        // Tuesday 11 PM -> Wednesday 1 AM
        val slices = ScheduleSlices.forWeek(listOf(event(dow = 2, start = 23 * 60, end = 60, endDayOffset = 1)), sat).sortedBy { it.dayIndex }
        assertEquals(2, slices.size)
        assertEquals(3, slices[0].dayIndex); assertEquals(23 * 60, slices[0].startMinute); assertEquals(1440, slices[0].endMinute)
        assertEquals(4, slices[1].dayIndex); assertEquals(0, slices[1].startMinute); assertEquals(60, slices[1].endMinute)
        assertTrue(slices[1].continuation)
    }

    @Test fun aClassThatEndsExactlyAtMidnightHasNothingAfterMidnight() {
        // Tuesday 11 PM -> Wednesday 12 AM: only the evening piece (no zero-height card on Wednesday)
        val slices = ScheduleSlices.forWeek(listOf(event(dow = 2, start = 23 * 60, end = 0, endDayOffset = 1)), sat)
        assertEquals(setOf(3 to false), cells(slices))
        assertEquals(1440, slices.single().endMinute)
    }

    @Test fun twelveAmSaturdayAndTwelveAmFridayAreDifferentMoments() {
        // Class A starts at 12 AM on Friday: the very beginning of Friday (column 6, minute 0).
        val startsFridayMidnight = event(dow = 5, start = 0, end = 60, id = 1)
        // Class B ends at 12 AM Saturday: the very end of Friday (column 6, up to minute 1440), nothing on Saturday.
        val endsSaturdayMidnight = event(dow = 5, start = 22 * 60, end = 0, endDayOffset = 1, id = 2)
        val slices = ScheduleSlices.forWeek(listOf(startsFridayMidnight, endsSaturdayMidnight), sat)
        val a = slices.single { it.eventId == 1L }
        val b = slices.single { it.eventId == 2L }
        assertEquals(6, a.dayIndex); assertEquals(0, a.startMinute)
        assertEquals(6, b.dayIndex); assertEquals(1440, b.endMinute)
        assertTrue("class B leaves Saturday's column empty", slices.none { it.eventId == 2L && it.dayIndex == 0 })
    }

    @Test fun fridayElevenPmToSaturdayOneAmCrossesIntoTheNextWeek() {
        val friday = event(dow = 5, start = 23 * 60, end = 60, endDayOffset = 1) // weekly
        // this week: the evening piece in the Friday column (6) AND last Friday's morning piece in the Saturday column (0)
        assertEquals(setOf(6 to false, 0 to true), cells(ScheduleSlices.forWeek(listOf(friday), sat)))

        // an alternating Friday class that happens THIS week only: its morning piece shows next week, not this one
        val alternating = event(dow = 5, start = 23 * 60, end = 60, endDayOffset = 1, repeat = 2, anchor = sat)
        assertEquals(setOf(6 to false), cells(ScheduleSlices.forWeek(listOf(alternating), sat)))
        assertEquals(setOf(0 to true), cells(ScheduleSlices.forWeek(listOf(alternating), sat + 7)))
        assertTrue(ScheduleSlices.forWeek(listOf(alternating), sat + 14).any { it.dayIndex == 6 })
    }

    @Test fun alternatingClassesAppearOnlyInTheirWeeks() {
        val physical = event(dow = 6, start = 480, end = 570, repeat = 2, anchor = sat, id = 1)
        val online = event(dow = 2, start = 600, end = 690, repeat = 2, anchor = sat + 7, id = 2)
        assertEquals(listOf(1L), ScheduleSlices.forWeek(listOf(physical, online), sat).map { it.eventId })
        assertEquals(listOf(2L), ScheduleSlices.forWeek(listOf(physical, online), sat + 7).map { it.eventId })
        assertEquals(listOf(1L), ScheduleSlices.forWeek(listOf(physical, online), sat + 14).map { it.eventId })
    }
}
