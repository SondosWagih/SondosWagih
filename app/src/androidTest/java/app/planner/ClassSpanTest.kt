package app.planner

import app.planner.ui.study.formatClassSpan
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassSpanTest {
    @Test fun anOrdinaryClassShowsOnlyTimes() {
        assertEquals("8 AM – 9:30 AM", formatClassSpan(2, 8 * 60, 9 * 60 + 30, 0, Locale.ENGLISH))
    }

    @Test fun aClassThatEndsTheNextDayNamesBothDays() {
        // ISO 5 = Friday
        assertEquals("Fri 11 PM – Sat 12 AM", formatClassSpan(5, 23 * 60, 0, 1, Locale.ENGLISH))
        assertEquals("Fri 11 PM – Sat 12:30 AM", formatClassSpan(5, 23 * 60, 30, 1, Locale.ENGLISH))
        // Sunday (7) -> Monday (1) wraps around the ISO week
        assertEquals("Sun 10 PM – Mon 1 AM", formatClassSpan(7, 22 * 60, 60, 1, Locale.ENGLISH))
    }

    @Test fun withTheDayAlwaysNamesTheStartDay() {
        assertEquals("Friday 8 AM – 9:30 AM", formatClassSpan(5, 8 * 60, 9 * 60 + 30, 0, Locale.ENGLISH, withDay = true))
        assertEquals("Friday 11 PM – Saturday 12 AM", formatClassSpan(5, 23 * 60, 0, 1, Locale.ENGLISH, withDay = true))
    }

    @Test fun arabicUsesArabicDayNamesAndSadMeem() {
        val ar = Locale("ar")
        val text = formatClassSpan(5, 23 * 60, 0, 1, ar)
        assertEquals(true, text.contains("11 م") && text.contains("12 ص"))
    }
}
