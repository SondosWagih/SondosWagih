package app.planner

import app.planner.data.local.entity.*
import app.planner.data.timer.TimerSnapshot
import app.planner.data.timer.TimerStatus
import app.planner.domain.schedule.AlarmPlanner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** Which alarms must exist? Checked with fixed dates (Saturday 14 November 2026) in UTC. */
class AlarmPlannerTest {
    private val utc = ZoneId.of("UTC")
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(utc).toInstant().toEpochMilli()
    private val sat = day(2026, 11, 14)
    private val idle = TimerSnapshot()

    private fun event(id: Long, dow: Int, start: Int = 480, repeat: Int = 1, anchor: Long = 0L) = StudyEventEntity(
        id = id, courseId = 1, type = StudyEventType.SECTION, attendance = AttendanceFormat.PHYSICAL,
        dayOfWeek = dow, startMinute = start, endMinute = start + 90,
        repeatEveryWeeks = repeat, anchorWeekStart = anchor, createdAt = 0, updatedAt = 0,
    )

    private fun exam(id: Long, d: Int, minute: Int) = ExamEntity(
        id = id, courseId = 1, kind = ExamKind.FINAL, title = "", dateEpochDay = day(2026, 11, d),
        startMinute = minute, durationMinutes = 90, createdAt = 0, updatedAt = 0,
    )

    private fun assignment(id: Long, due: Long) =
        AssignmentEntity(id = id, courseId = 1, title = "HW", dueEpochDay = due, createdAt = 0, updatedAt = 0)

    // ---------------- classes ----------------

    @Test fun weeklyClassFiresTodayIfItHasNotStartedOtherwiseNextWeek() {
        val e = event(1, dow = 6) // Saturday 8:00
        val before = AlarmPlanner.nextClassOccurrence(e, at(2026, 11, 14, 6), utc)!!
        assertEquals(at(2026, 11, 14, 8), before.first)
        assertEquals(sat, before.second)
        assertEquals(at(2026, 11, 21, 8), AlarmPlanner.nextClassOccurrence(e, at(2026, 11, 14, 9), utc)!!.first)
    }

    @Test fun physicalWeekAndOnlineWeekAlternate() {
        // "Techniques": physical on Saturday this week, online on Tuesday next week, then they alternate.
        val physical = event(1, dow = 6, repeat = 2, anchor = sat)
        val online = event(2, dow = 2, repeat = 2, anchor = sat + 7)
        val now = at(2026, 11, 14, 6)
        assertEquals(at(2026, 11, 14, 8), AlarmPlanner.nextClassOccurrence(physical, now, utc)!!.first)
        // the online class is NOT this Tuesday (17th) but the Tuesday of next week (24th)
        assertEquals(at(2026, 11, 24, 8), AlarmPlanner.nextClassOccurrence(online, now, utc)!!.first)
        // after the physical class, the next one is two weeks later (28th), not next Saturday (21st)
        assertEquals(at(2026, 11, 28, 8), AlarmPlanner.nextClassOccurrence(physical, at(2026, 11, 14, 9), utc)!!.first)
        // and the online class comes back two weeks after the 24th
        assertEquals(at(2026, 12, 8, 8), AlarmPlanner.nextClassOccurrence(online, at(2026, 11, 24, 9), utc)!!.first)
    }

    // ---------------- exams ----------------

    @Test fun anExamGetsThreeAlarmsAndPastOnesAreSkipped() {
        val x = exam(1, d = 20, minute = 9 * 60)
        val all = AlarmPlanner.plan(emptyList(), listOf(x), emptyList(), idle, at(2026, 11, 14, 6), utc)
        assertEquals(3, all.size)
        assertEquals(at(2026, 11, 18, 9), all.first { it.kind == NotificationKind.ADVANCE_48H }.triggerAtMillis)
        assertEquals(at(2026, 11, 20, 8, 50), all.first { it.kind == NotificationKind.ADVANCE_10M }.triggerAtMillis)
        val atTime = all.first { it.kind == NotificationKind.AT_TIME }
        assertEquals(at(2026, 11, 20, 9), atTime.triggerAtMillis)
        assertTrue("the exam itself uses the highest-priority alarm", atTime.highPriority)

        val late = AlarmPlanner.plan(emptyList(), listOf(x), emptyList(), idle, at(2026, 11, 19, 12), utc)
        assertEquals(setOf(NotificationKind.ADVANCE_10M, NotificationKind.AT_TIME), late.map { it.kind }.toSet())
        assertTrue(AlarmPlanner.plan(emptyList(), listOf(x), emptyList(), idle, at(2026, 11, 21, 0), utc).isEmpty())
    }

    // ---------------- assignments ----------------

    @Test fun overdueReminderIsPlannedForAnUnfinishedAssignment() {
        val now = at(2026, 11, 14, 6)
        val p = AlarmPlanner.plan(emptyList(), emptyList(), listOf(assignment(1, day(2026, 11, 10))), idle, now, utc).single()
        assertEquals(NotificationOwnerType.ASSIGNMENT, p.ownerType)
        assertEquals(NotificationKind.OVERDUE, p.kind)
        assertTrue(p.triggerAtMillis > now)
    }

    @Test fun nothingIsPlannedUntilTheDueDayHasEnded() {
        val now = at(2026, 11, 14, 6)
        val p = AlarmPlanner.plan(emptyList(), emptyList(), listOf(assignment(1, day(2026, 11, 20))), idle, now, utc).single()
        assertTrue("first reminder is on the 21st or later", p.triggerAtMillis >= at(2026, 11, 21, 0))
    }

    @Test fun reminderMovesToTomorrowWhenTodaysSlotsHavePassed() {
        val (_, d) = AlarmPlanner.nextOverdueTrigger(at(2026, 11, 14, 23, 30), day(2026, 11, 11), utc)
        assertEquals(sat + 1, d)
    }

    @Test fun twoReminderSlotsADayAtDifferentTimes() {
        val slots = AlarmPlanner.overdueSlots(sat)
        assertEquals(2, slots.size)
        assertTrue(slots[0] in 10 * 60..13 * 60 + 30)
        assertTrue(slots[1] in 17 * 60..21 * 60)
        assertTrue("hours apart", slots[1] - slots[0] >= 180)
        assertEquals("the same day always gives the same times", slots, AlarmPlanner.overdueSlots(sat))
        assertTrue("different days give different times", (0L..20L).map { AlarmPlanner.overdueSlots(sat + it) }.toSet().size > 1)
    }

    @Test fun noReminderWhenEverythingIsTickedOff() {
        assertTrue(AlarmPlanner.plan(emptyList(), emptyList(), emptyList(), idle, at(2026, 11, 14, 6), utc).isEmpty())
    }

    // ---------------- timer ----------------

    @Test fun onlyARunningTimerGetsAnEndAlarm() {
        val now = at(2026, 11, 14, 9)
        val end = at(2026, 11, 14, 10)
        val running = TimerSnapshot(sessionId = 5, status = TimerStatus.RUNNING, totalMs = 3_600_000, endAtMs = end)
        val p = AlarmPlanner.plan(emptyList(), emptyList(), emptyList(), running, now, utc).single()
        assertEquals(NotificationOwnerType.SESSION, p.ownerType)
        assertEquals(NotificationKind.TIMER_END, p.kind)
        assertEquals(end, p.triggerAtMillis)
        assertTrue(p.highPriority)

        val paused = running.copy(status = TimerStatus.PAUSED, remainingMs = 1_000_000)
        assertTrue(AlarmPlanner.plan(emptyList(), emptyList(), emptyList(), paused, now, utc).isEmpty())
        assertTrue(AlarmPlanner.plan(emptyList(), emptyList(), emptyList(), idle, now, utc).isEmpty())
    }

    // ---------------- identity ----------------

    @Test fun requestCodesAreUniqueAndStable() {
        val running = TimerSnapshot(sessionId = 1, status = TimerStatus.RUNNING, totalMs = 1000, endAtMs = at(2026, 11, 14, 10))
        fun plan() = AlarmPlanner.plan(
            events = listOf(event(1, 6), event(2, 2), event(3, 3)),
            exams = listOf(exam(1, 20, 540), exam(2, 22, 600)),
            incompleteAssignments = listOf(assignment(1, day(2026, 11, 10))),
            timer = running, nowMillis = at(2026, 11, 14, 6), zone = utc,
        )
        val codes = plan().map { it.requestCode }
        assertEquals("no two alarms may share a request code", codes.size, codes.toSet().size)
        assertEquals("the same data must give the same codes", codes, plan().map { it.requestCode })
    }

    @Test fun onlyTheExamItselfAndTheTimerUseTheAlarmClock() {
        val running = TimerSnapshot(sessionId = 5, status = TimerStatus.RUNNING, totalMs = 60_000, endAtMs = at(2026, 11, 14, 10))
        val plan = AlarmPlanner.plan(
            events = listOf(event(1, 6)),
            exams = listOf(exam(1, 20, 540)),
            incompleteAssignments = listOf(assignment(1, day(2026, 11, 10))),
            timer = running, nowMillis = at(2026, 11, 14, 6), zone = utc,
        )
        assertEquals(1 + 3 + 1 + 1, plan.size)
        fun high(type: NotificationOwnerType, kind: NotificationKind) = plan.first { it.ownerType == type && it.kind == kind }.highPriority
        assertTrue(high(NotificationOwnerType.EXAM, NotificationKind.AT_TIME))
        assertTrue(high(NotificationOwnerType.SESSION, NotificationKind.TIMER_END))
        assertFalse(high(NotificationOwnerType.EXAM, NotificationKind.ADVANCE_10M))
        assertFalse(high(NotificationOwnerType.STUDY_EVENT, NotificationKind.AT_TIME))
        assertFalse(high(NotificationOwnerType.ASSIGNMENT, NotificationKind.OVERDUE))
    }

    @Test fun aClassThatStartsAtElevenPmOnFridayRemindsAtElevenPmOnFriday() {
        // Friday 20 November 2026, 23:00 (ISO weekday 5); it ends at 1 AM on Saturday, but the reminder is at the start.
        val e = event(1, dow = 5, start = 23 * 60).copy(endMinute = 60, endDayOffset = 1)
        val (trigger, d) = AlarmPlanner.nextClassOccurrence(e, at(2026, 11, 14, 6), utc)!!
        assertEquals(at(2026, 11, 20, 23), trigger)
        assertEquals(day(2026, 11, 20), d)
    }
}
