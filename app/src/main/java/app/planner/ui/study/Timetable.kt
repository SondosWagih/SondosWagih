package app.planner.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TimeColumnWidth = 58.dp
private val RowHeight = 36.dp      // one half hour
private val HeaderHeight = 52.dp
private val MinDayWidth = 112.dp

/**
 * Timetable grid: a label every half hour down the side (8 AM to 8 PM by default), days across the top,
 * items as colour cards. Header and time column stay fixed while the grid scrolls; layout mirrors in RTL.
 */
@Composable
fun Timetable(
    days: List<TimetableDay>,
    items: List<TimetableItem>,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (startMinute, endMinute) = remember(items) { gridMinuteRange(items) }
    val rows = (endMinute - startMinute) / GRID_STEP
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val density = LocalDensity.current
    val initialMinute = remember(items) { initialScrollMinute(items) }
    // An overnight class makes the grid start at midnight; still open it at 8 AM.
    LaunchedEffect(startMinute, initialMinute) {
        if (startMinute < initialMinute) {
            val rowsAbove = (initialMinute - startMinute) / GRID_STEP
            vScroll.scrollTo((rowsAbove * with(density) { RowHeight.toPx() }).toInt())
        }
    }
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    BoxWithConstraints(modifier) {
        val dayWidth = maxOf((maxWidth - TimeColumnWidth) / maxOf(days.size, 1), MinDayWidth)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(HeaderHeight)) {
                Spacer(Modifier.width(TimeColumnWidth))
                Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
                    days.forEach { day ->
                        Column(
                            Modifier.width(dayWidth).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            val color = if (day.highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            Text(
                                day.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (day.highlight) FontWeight.Bold else FontWeight.Normal,
                                color = color,
                            )
                            day.sublabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = color) }
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll)) {
                Column(Modifier.width(TimeColumnWidth)) {
                    for (i in 0 until rows) {
                        Box(Modifier.fillMaxWidth().height(RowHeight).padding(top = 2.dp), contentAlignment = Alignment.TopCenter) {
                            Text(
                                formatMinute(startMinute + i * GRID_STEP),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
                    days.forEach { day ->
                        DayColumn(
                            placed = remember(items, day.key) { layoutLanes(items.filter { it.dayKey == day.key }) },
                            startMinute = startMinute,
                            rows = rows,
                            width = dayWidth,
                            highlight = day.highlight,
                            lineColor = lineColor,
                            onItemClick = onItemClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    placed: List<PlacedItem>,
    startMinute: Int,
    rows: Int,
    width: Dp,
    highlight: Boolean,
    lineColor: Color,
    onItemClick: (Long) -> Unit,
) {
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    Box(
        Modifier.width(width).height(RowHeight * rows)
            .background(if (highlight) tint else Color.Transparent)
            .drawBehind {
                val step = RowHeight.toPx()
                val stroke = 1.dp.toPx()
                for (i in 0..rows) {
                    val onTheHour = (startMinute + i * GRID_STEP) % 60 == 0
                    drawLine(
                        if (onTheHour) lineColor else lineColor.copy(alpha = 0.45f),
                        Offset(0f, i * step), Offset(size.width, i * step), stroke,
                    )
                }
                drawLine(lineColor, Offset(size.width, 0f), Offset(size.width, size.height), stroke)
            }
    ) {
        placed.forEach { p ->
            val laneWidth = width / p.lanes
            val top = RowHeight * ((p.item.startMinute - startMinute) / GRID_STEP.toFloat())
            val height = maxOf(RowHeight * ((p.item.endMinute - p.item.startMinute) / GRID_STEP.toFloat()), 32.dp)
            TimetableCard(
                item = p.item,
                onClick = { onItemClick(p.item.id) },
                modifier = Modifier.offset(x = laneWidth * p.lane, y = top).size(laneWidth, height).padding(1.5.dp),
            )
        }
    }
}

@Composable
private fun TimetableCard(item: TimetableItem, onClick: () -> Unit, modifier: Modifier) {
    val fg = if (item.color.luminance() > 0.5f) Color.Black else Color.White
    Surface(
        onClick = onClick,
        color = item.color,
        contentColor = fg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.testTag("timetable_card"),
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.lines.forEachIndexed { index, line ->
                if (index == 1 && item.icon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(item.icon, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(line, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(line, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
