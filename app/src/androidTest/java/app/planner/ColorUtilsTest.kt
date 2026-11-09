package app.planner

import app.planner.ui.study.ColorTheme
import app.planner.ui.study.DefaultCourseColor
import app.planner.ui.study.parseHexColor
import app.planner.ui.study.toHex
import org.junit.Assert.*
import org.junit.Test

class ColorUtilsTest {
    @Test fun parsesSixDigitCodes() {
        assertEquals(0xFF3F6FD8.toInt(), parseHexColor("3F6FD8"))
        assertEquals(0xFF3F6FD8.toInt(), parseHexColor("3f6fd8"))
    }

    @Test fun rejectsAnythingElse() {
        assertNull(parseHexColor("3F6FD"))
        assertNull(parseHexColor("3F6FD8A"))
        assertNull(parseHexColor("ZZZZZZ"))
        assertNull(parseHexColor(""))
    }

    @Test fun hexRoundTrips() {
        assertEquals("3F6FD8", toHex(0xFF3F6FD8.toInt()))
        assertEquals("000000", toHex(0xFF000000.toInt()))
        ColorTheme.entries.flatMap { it.colors }.forEach { assertEquals(it, parseHexColor(toHex(it))) }
    }

    @Test fun everyThemeHasMoreThanFourDistinctColors() {
        ColorTheme.entries.forEach { theme ->
            assertTrue("${theme.name} needs more than 4 colours", theme.colors.size > 4)
            assertEquals("${theme.name} has duplicate colours", theme.colors.size, theme.colors.toSet().size)
        }
    }

    @Test fun yourSevenPalettesAreIncludedWithTheirOriginalColours() {
        val expected = mapOf(
            ColorTheme.APRICOT to listOf("FBA17E", "F7D794", "FF9A00", "EE6200"),
            ColorTheme.DEEPSEA to listOf("071B2B", "167D8D", "B9E4E8", "EEEEEE"),
            ColorTheme.ORCHID to listOf("542177", "8E1EA2", "D552A3", "98E8DE"),
            ColorTheme.FOREST to listOf("1E3B2F", "7F8C6A", "C8B48A", "F5F0E6"),
            ColorTheme.MEADOW to listOf("F4BE38", "EDDA5E", "71951E", "587529"),
            ColorTheme.BERRY to listOf("421454", "802C62", "FF5B42", "FFD749"),
            ColorTheme.LATTE to listOf("FCF0DA", "AEAC78", "F2C46A", "4C4541"),
        )
        expected.forEach { (theme, hexes) ->
            val have = theme.colors.map { toHex(it) }
            hexes.forEach { assertTrue("${theme.name} is missing #$it", it in have) }
        }
    }

    @Test fun defaultCourseColorIsSafeToReadBeforeAnyThemeIsTouched() {
        // Regression: ColorTheme and the default colour once initialised each other in a cycle.
        assertEquals(0xFF1E88E5.toInt(), DefaultCourseColor)
        assertTrue(ColorTheme.entries.isNotEmpty())
    }

    @Test fun thereAreFourteenThemes() {
        assertEquals(14, ColorTheme.entries.size)
    }
}
