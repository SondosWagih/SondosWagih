package app.planner

import app.planner.domain.schedule.WeekMath
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekMathTest {
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test fun weekStartsOnSaturday() {
        val sat = day(2026, 11, 14) // a Saturday
        assertEquals(sat, WeekMath.weekStartOf(sat))
        assertEquals(sat, WeekMath.weekStartOf(day(2026, 11, 15)))  // Sunday
        assertEquals(sat, WeekMath.weekStartOf(day(2026, 11, 17)))  // Tuesday
        assertEquals(sat, WeekMath.weekStartOf(day(2026, 11, 20)))  // Friday: last day of the week
        assertEquals(day(2026, 11, 21), WeekMath.weekStartOf(day(2026, 11, 21)))
    }

    @Test fun everyWeekAlwaysOccurs() {
        assertTrue(WeekMath.occursInWeek(1, 0, day(2026, 11, 14)))
        assertTrue(WeekMath.occursInWeek(1, 12345, day(2026, 11, 21)))
    }

    @Test fun everySecondWeekWorksBeforeAndAfterTheAnchor() {
        val anchor = day(2026, 11, 14)
        assertTrue(WeekMath.occursInWeek(2, anchor, anchor))
        assertFalse(WeekMath.occursInWeek(2, anchor, anchor + 7))
        assertTrue(WeekMath.occursInWeek(2, anchor, anchor + 14))
        assertFalse(WeekMath.occursInWeek(2, anchor, anchor - 7))
        assertTrue(WeekMath.occursInWeek(2, anchor, anchor - 14))
    }

    @Test fun timetableColumnsRunSaturdayToFriday() {
        // ISO: Mon = 1 ... Sat = 6, Sun = 7
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), listOf(6, 7, 1, 2, 3, 4, 5).map { WeekMath.dayIndex(it) })
    }
}
