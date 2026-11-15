package app.planner.domain.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Date helpers. The week runs Saturday -> Friday. */
object WeekMath {
    private const val SATURDAY = 6 // ISO: Monday = 1 ... Sunday = 7

    /** The Saturday that starts the week containing [epochDay]. */
    fun weekStartOf(epochDay: Long): Long {
        val dow = LocalDate.ofEpochDay(epochDay).dayOfWeek.value
        return epochDay - Math.floorMod(dow - SATURDAY, 7)
    }

    /** 0 = Saturday ... 6 = Friday: the column of an ISO weekday in the timetable. */
    fun dayIndex(isoDay: Int): Int = Math.floorMod(isoDay - SATURDAY, 7)

    /** Does a class with this repeat rule take place in the week that starts on [weekStart]? */
    fun occursInWeek(repeatEveryWeeks: Int, anchorWeekStart: Long, weekStart: Long): Boolean {
        if (repeatEveryWeeks <= 1) return true
        val weeks = Math.floorDiv(weekStart - anchorWeekStart, 7L)
        return Math.floorMod(weeks, repeatEveryWeeks.toLong()) == 0L
    }

    fun epochDayOf(millis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    /** Wall-clock instant of [minute] (minutes after midnight) on [epochDay]. */
    fun millisAt(epochDay: Long, minute: Int, zone: ZoneId): Long =
        LocalDate.ofEpochDay(epochDay).atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()
}
