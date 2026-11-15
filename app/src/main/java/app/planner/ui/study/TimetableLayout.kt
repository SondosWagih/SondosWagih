package app.planner.ui.study

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** One column of the timetable (a weekday, or a dated day of a given week). */
data class TimetableDay(val key: Int, val label: String, val sublabel: String? = null, val highlight: Boolean = false)

/** A card on the timetable. Times are minutes from midnight. */
data class TimetableItem(
    val id: Long,
    val dayKey: Int,
    val startMinute: Int,
    val endMinute: Int,
    val title: String,
    val lines: List<String>,
    val color: Color,
    val icon: ImageVector? = null,
)

/** An item placed in `lane` of `lanes` equal-width lanes inside its day column. */
data class PlacedItem(val item: TimetableItem, val lane: Int, val lanes: Int)

const val GRID_DEFAULT_START = 8 * 60   // 8 AM
const val GRID_DEFAULT_END = 20 * 60    // 8 PM
const val GRID_STEP = 30                // one row per half hour

/**
 * Overlapping items are put side by side in lanes so cards never hide each other;
 * items that do not overlap keep the full column width.
 */
fun layoutLanes(items: List<TimetableItem>): List<PlacedItem> {
    val sorted = items.sortedWith(compareBy({ it.startMinute }, { it.endMinute }))
    val result = mutableListOf<PlacedItem>()
    var cluster = mutableListOf<Pair<TimetableItem, Int>>()
    val laneEnds = mutableListOf<Int>()
    var clusterEnd = -1

    fun flush() {
        val lanes = laneEnds.size
        cluster.forEach { (item, lane) -> result += PlacedItem(item, lane, lanes) }
        cluster = mutableListOf()
        laneEnds.clear()
        clusterEnd = -1
    }

    for (item in sorted) {
        if (cluster.isNotEmpty() && item.startMinute >= clusterEnd) flush()
        var lane = laneEnds.indexOfFirst { it <= item.startMinute }
        if (lane == -1) {
            laneEnds.add(item.endMinute)
            lane = laneEnds.lastIndex
        } else {
            laneEnds[lane] = item.endMinute
        }
        cluster.add(item to lane)
        clusterEnd = maxOf(clusterEnd, item.endMinute)
    }
    flush()
    return result
}

/** Visible range in minutes: 8 AM to 8 PM by default, widened in half-hour steps to fit every item. */
fun gridMinuteRange(items: List<TimetableItem>): Pair<Int, Int> {
    val first = minOf(GRID_DEFAULT_START, (items.minOfOrNull { it.startMinute } ?: GRID_DEFAULT_START) / GRID_STEP * GRID_STEP)
    val lastNeeded = ((items.maxOfOrNull { it.endMinute } ?: GRID_DEFAULT_END) + GRID_STEP - 1) / GRID_STEP * GRID_STEP
    return first to minOf(1440, maxOf(GRID_DEFAULT_END, lastNeeded))
}

/** Where the grid should be scrolled to when it opens: 8 AM, or an earlier class starting between 5 and 8 AM. */
fun initialScrollMinute(items: List<TimetableItem>): Int {
    val early = items.map { it.startMinute }.filter { it in 5 * 60 until GRID_DEFAULT_START }.minOrNull()
    return (early ?: GRID_DEFAULT_START) / GRID_STEP * GRID_STEP
}
