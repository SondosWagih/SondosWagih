package app.planner

import app.planner.ui.study.formatClock
import app.planner.ui.study.formatMinute
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TimeFormatTest {
    private lateinit var saved: Locale
    @Before fun save() { saved = Locale.getDefault() }
    @After fun restore() = Locale.setDefault(saved)

    @Test fun englishUsesAmPmWithoutLeadingZeros() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("8 AM", formatMinute(8 * 60))
        assertEquals("8:30 AM", formatMinute(8 * 60 + 30))
        assertEquals("12 PM", formatMinute(12 * 60))
        assertEquals("12:30 PM", formatMinute(12 * 60 + 30))
        assertEquals("5 PM", formatMinute(17 * 60))
        assertEquals("12 AM", formatMinute(0))
        assertEquals("8 PM", formatMinute(20 * 60))
    }

    @Test fun arabicUsesSadAndMeem() {
        Locale.setDefault(Locale("ar"))
        assertEquals("8 ص", formatMinute(8 * 60))
        assertEquals("5 م", formatMinute(17 * 60))
        assertEquals("5:30 م", formatMinute(17 * 60 + 30))
        assertEquals("12 م", formatMinute(12 * 60))
    }

    @Test fun clockTextForCountdowns() {
        assertEquals("00:09", formatClock(9))
        assertEquals("25:00", formatClock(25 * 60))
        assertEquals("1:05:09", formatClock(3600 + 5 * 60 + 9))
        assertEquals("00:00", formatClock(-3))
    }
}
