package app.planner.domain.schedule

import app.planner.data.local.entity.AssignmentEntity
import app.planner.data.local.entity.ExamEntity
import app.planner.data.local.entity.NotificationKind
import app.planner.data.local.entity.NotificationOwnerType
import app.planner.data.local.entity.StudyEventEntity
import app.planner.data.timer.TimerSnapshot
import app.planner.data.timer.TimerStatus
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

data class PlannedAlarm(
    val ownerType: NotificationOwnerType,
    val ownerId: Long,
    val kind: NotificationKind,
    /** The day this alarm is for (recurring things), 0 otherwise. */
    val occurrenceKey: Long,
    val triggerAtMillis: Long,
    /** Highest-priority alarm (setAlarmClock) instead of setExactAndAllowWhileIdle: the exam itself and the timer end. */
    val highPriority: Boolean,
) {
    val requestCode: Int get() = AlarmPlanner.requestCode(ownerType, ownerId, kind)
}

/**
 * Decides WHICH alarms must exist right now. It is pure (no Android), so it is easy to test, and it is
 * recomputed after every change, at boot, and when the time or time zone changes.
 */
object AlarmPlanner {
    const val EXAM_LONG_ADVANCE_MS = 48L * 60 * 60 * 1000
    const val EXAM_SHORT_ADVANCE_MS = 10L * 60 * 1000

    /** Stable and unique per (type, owner, kind): 3 bits type, 4 bits kind, 24 bits id. */
    fun requestCode(type: NotificationOwnerType, ownerId: Long, kind: NotificationKind): Int =
        (type.ordinal shl 28) or (kind.ordinal shl 24) or (ownerId.toInt() and 0xFFFFFF)

    /** The next time (after [fromMillis]) this class starts, with its date. Looks up to three weeks ahead. */
    fun nextClassOccurrence(e: StudyEventEntity, fromMillis: Long, zone: ZoneId): Pair<Long, Long>? {
        val today = WeekMath.epochDayOf(fromMillis, zone)
        for (offset in 0L..21L) {
            val day = today + offset
            if (LocalDate.ofEpochDay(day).dayOfWeek.value != e.dayOfWeek) continue
            if (!WeekMath.occursInWeek(e.repeatEveryWeeks, e.anchorWeekStart, WeekMath.weekStartOf(day))) continue
            val trigger = WeekMath.millisAt(day, e.startMinute, zone)
            if (trigger > fromMillis) return trigger to day
        }
        return null
    }

    /**
     * Two reminder times for one day, different every day but always the same for the same day
     * (so recomputing never moves an alarm): one in the late morning, one in the evening.
     */
    fun overdueSlots(epochDay: Long): List<Int> {
        val random = Random(epochDay * 7919L + 17L)
        val morning = 10 * 60 + random.nextInt(0, 211)   // 10:00 - 13:30
        val evening = 17 * 60 + random.nextInt(0, 241)   // 17:00 - 21:00
        return listOf(morning, evening)
    }

    /** The next overdue-reminder time after [fromMillis], never before [firstOverdueDay]. */
    fun nextOverdueTrigger(fromMillis: Long, firstOverdueDay: Long, zone: ZoneId): Pair<Long, Long> {
        val start = maxOf(firstOverdueDay, WeekMath.epochDayOf(fromMillis, zone))
        for (offset in 0L..3L) {
            val day = start + offset
            for (minute in overdueSlots(day)) {
                val t = WeekMath.millisAt(day, minute, zone)
                if (t > fromMillis) return t to day
            }
        }
        val day = start + 4
        return WeekMath.millisAt(day, overdueSlots(day).first(), zone) to day
    }

    fun plan(
        events: List<StudyEventEntity>,
        exams: List<ExamEntity>,
        incompleteAssignments: List<AssignmentEntity>,
        timer: TimerSnapshot,
        nowMillis: Long,
        zone: ZoneId,
    ): List<PlannedAlarm> {
        val out = mutableListOf<PlannedAlarm>()

        // Classes: one alarm per class, for its next occurrence only (re-planned after it fires).
        for (e in events) {
            nextClassOccurrence(e, nowMillis, zone)?.let { (trigger, day) ->
                out += PlannedAlarm(NotificationOwnerType.STUDY_EVENT, e.id, NotificationKind.AT_TIME, day, trigger, false)
            }
        }

        // Exams: 48 hours before, 10 minutes before, and at the exam time (highest priority).
        for (x in exams) {
            val at = WeekMath.millisAt(x.dateEpochDay, x.startMinute, zone)
            val times = listOf(
                NotificationKind.AT_TIME to at,
                NotificationKind.ADVANCE_10M to at - EXAM_SHORT_ADVANCE_MS,
                NotificationKind.ADVANCE_48H to at - EXAM_LONG_ADVANCE_MS,
            )
            for ((kind, trigger) in times) {
                if (trigger > nowMillis) {
                    out += PlannedAlarm(NotificationOwnerType.EXAM, x.id, kind, 0, trigger, kind == NotificationKind.AT_TIME)
                }
            }
        }

        // Assignments: from the day after the earliest due day, remind twice a day until everything is ticked.
        if (incompleteAssignments.isNotEmpty()) {
            val firstOverdueDay = incompleteAssignments.minOf { it.dueEpochDay } + 1
            val (trigger, day) = nextOverdueTrigger(nowMillis, firstOverdueDay, zone)
            out += PlannedAlarm(NotificationOwnerType.ASSIGNMENT, 0, NotificationKind.OVERDUE, day, trigger, false)
        }

        // A running study timer must end with an alarm even if the app is closed.
        val sessionId = timer.sessionId
        if (timer.status == TimerStatus.RUNNING && sessionId != null) {
            out += PlannedAlarm(NotificationOwnerType.SESSION, sessionId, NotificationKind.TIMER_END, 0, timer.endAtMs, true)
        }
        return out
    }
}
