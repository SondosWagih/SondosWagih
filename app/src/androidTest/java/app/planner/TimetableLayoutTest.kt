package app.planner

import app.planner.ui.study.initialScrollMinute
import androidx.compose.ui.graphics.Color
import app.planner.ui.study.TimetableItem
import app.planner.ui.study.gridMinuteRange
import app.planner.ui.study.layoutLanes
import org.junit.Assert.*
import org.junit.Test

class TimetableLayoutTest {
    private fun item(id: Long, start: Int, end: Int) =
        TimetableItem(id, dayKey = 1, startMinute = start, endMinute = end, title = "t$id", lines = emptyList(), color = Color.Red)

    @Test fun backToBackItemsKeepFullWidth() {
        val placed = layoutLanes(listOf(item(1, 540, 600), item(2, 600, 660)))
        assertTrue(placed.all { it.lanes == 1 && it.lane == 0 })
    }

    @Test fun overlappingItemsSitSideBySideAndNeverShareALane() {
        val placed = layoutLanes(listOf(item(1, 540, 660), item(2, 600, 720)))
        assertTrue(placed.all { it.lanes == 2 })
        assertEquals(setOf(0, 1), placed.map { it.lane }.toSet())
    }

    @Test fun threeWayOverlapUsesThreeLanes() {
        val placed = layoutLanes(listOf(item(1, 540, 700), item(2, 560, 700), item(3, 580, 700)))
        assertTrue(placed.all { it.lanes == 3 })
        assertEquals(setOf(0, 1, 2), placed.map { it.lane }.toSet())
    }

    @Test fun laneIsReusedOnceAnItemEnds() {
        val placed = layoutLanes(listOf(item(1, 540, 600), item(2, 570, 660), item(3, 600, 630))).associateBy { it.item.id }
        assertEquals(0, placed.getValue(1).lane)
        assertEquals(1, placed.getValue(2).lane)
        assertEquals(0, placed.getValue(3).lane)
        assertTrue(placed.values.all { it.lanes == 2 })
    }

    @Test fun separateClustersAreIndependent() {
        val placed = layoutLanes(listOf(item(1, 540, 660), item(2, 600, 700), item(3, 900, 960))).associateBy { it.item.id }
        assertEquals(2, placed.getValue(1).lanes)
        assertEquals(1, placed.getValue(3).lanes)
    }

    @Test fun gridIsEightAmToEightPmAndGrowsByHalfHours() {
        assertEquals(480 to 1200, gridMinuteRange(emptyList()))
        assertEquals(480 to 1200, gridMinuteRange(listOf(item(1, 600, 660))))
        assertEquals(450 to 1230, gridMinuteRange(listOf(item(1, 455, 500), item(2, 1200, 1215)))) // 7:35 AM, 8:15 PM
        assertEquals(360 to 1440, gridMinuteRange(listOf(item(1, 360, 420), item(2, 1380, 1440))))
    }

    @Test fun gridOpensAtEightAmEvenWhenAnOvernightClassStretchesItToMidnight() {
        val overnightMorningPiece = item(1, 0, 60)
        assertEquals(0 to 1200, gridMinuteRange(listOf(overnightMorningPiece)))
        assertEquals(480, initialScrollMinute(listOf(overnightMorningPiece)))
        assertEquals(480, initialScrollMinute(emptyList()))
        // a class that starts at 7:10 AM should not be hidden above the first visible row
        assertEquals(420, initialScrollMinute(listOf(item(2, 430, 500))))
    }
}
