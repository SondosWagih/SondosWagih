package app.planner

import app.planner.ui.study.DEFAULT_CLASS_END_MINUTE
import app.planner.ui.study.DEFAULT_CLASS_START_MINUTE
import app.planner.ui.study.currentWeekStart
import app.planner.ui.study.formatMinute
import app.planner.ui.study.todayEpochDay
import app.planner.ui.study.weekDaysInOrder
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WeekTest {
    private lateinit var saved: Locale
    @Before fun save() { saved = Locale.getDefault() }
    @After fun restore() = Locale.setDefault(saved)

    @Test fun weekRunsFromSaturdayToFriday() {
        // ISO numbers: Mon = 1 ... Sat = 6, Sun = 7
        assertEquals(listOf(6, 7, 1, 2, 3, 4, 5), weekDaysInOrder())
    }

    @Test fun currentWeekStartsOnASaturdayAndContainsToday() {
        val start = currentWeekStart()
        assertEquals(DayOfWeek.SATURDAY, LocalDate.ofEpochDay(start).dayOfWeek)
        assertEquals(DayOfWeek.FRIDAY, LocalDate.ofEpochDay(start + 6).dayOfWeek)
        assertTrue(todayEpochDay() in start..(start + 6))
    }

    @Test fun newClassesDefaultToEightAmUntilNineThirty() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("8 AM", formatMinute(DEFAULT_CLASS_START_MINUTE))
        assertEquals("9:30 AM", formatMinute(DEFAULT_CLASS_END_MINUTE))
    }
}
