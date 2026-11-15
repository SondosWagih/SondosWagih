package app.planner.domain.schedule

import app.planner.data.local.entity.StudyEventEntity

/** One drawn piece of a class: which column of the week (0 = Saturday .. 6 = Friday) and which minutes. */
data class ClassSlice(
    val eventId: Long,
    val dayIndex: Int,
    val startMinute: Int,
    val endMinute: Int,
    /** The part after midnight of a class that started the evening before. */
    val continuation: Boolean,
)

object ScheduleSlices {
    /** A class that ends on the next day passes midnight. */
    fun passesMidnight(e: StudyEventEntity): Boolean = e.endDayOffset > 0

    /**
     * Everything to draw in the week that starts on [weekStart]. A class from 11 PM to 1 AM gives an evening piece
     * on its day and a morning piece on the next day; for a Friday class that morning piece belongs to the NEXT
     * week's Saturday column, so it is drawn in that week while the evening piece stays in this one.
     */
    fun forWeek(events: List<StudyEventEntity>, weekStart: Long): List<ClassSlice> {
        val out = mutableListOf<ClassSlice>()
        for (e in events) {
            val index = WeekMath.dayIndex(e.dayOfWeek)
            val thisWeek = WeekMath.occursInWeek(e.repeatEveryWeeks, e.anchorWeekStart, weekStart)
            if (!passesMidnight(e)) {
                if (thisWeek) out += ClassSlice(e.id, index, e.startMinute, e.endMinute, false)
                continue
            }
            if (thisWeek) out += ClassSlice(e.id, index, e.startMinute, 24 * 60, false)
            // Ending exactly at 12 AM leaves nothing to draw after midnight.
            if (e.endMinute > 0) {
                if (index < 6) {
                    if (thisWeek) out += ClassSlice(e.id, index + 1, 0, e.endMinute, true)
                } else if (WeekMath.occursInWeek(e.repeatEveryWeeks, e.anchorWeekStart, weekStart - 7)) {
                    out += ClassSlice(e.id, 0, 0, e.endMinute, true)
                }
            }
        }
        return out
    }
}
