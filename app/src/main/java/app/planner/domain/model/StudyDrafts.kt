package app.planner.domain.model

import app.planner.data.local.entity.AttendanceFormat
import app.planner.data.local.entity.ExamKind
import app.planner.data.local.entity.Priority
import app.planner.data.local.entity.StudyEventType

/** Form input models. id == null means "create", otherwise "edit that row". */
data class CourseDraft(
    val id: Long? = null, val name: String, val code: String? = null,
    val instructor: String? = null, val colorArgb: Int,
)

/** `days` are ISO weekdays (1 = Monday .. 7 = Sunday). One row is stored per selected day. */
data class StudyEventDraft(
    val id: Long? = null, val courseId: Long?, val type: StudyEventType, val attendance: AttendanceFormat,
    val days: Set<Int>, val startMinute: Int, val endMinute: Int,
    val location: String? = null, val onlineLink: String? = null, val notes: String? = null,
    /** 0 = ends the day it starts, 1 = ends the next day (e.g. Friday 11 PM -> Saturday 12:30 AM). */
    val endDayOffset: Int = 0,
    /** 1 = every week, 2 = alternating (every second week). */
    val repeatEveryWeeks: Int = 1,
    /** For alternating classes: does the first occurrence fall in the current week (true) or the next one (false)? */
    val startsThisWeek: Boolean = true,
    val topic: String? = null,
)

/** A blank title means "use <exam type> <course name>" (computed when shown). */
data class ExamDraft(
    val id: Long? = null, val courseId: Long?, val kind: ExamKind, val title: String = "",
    val dateEpochDay: Long, val startMinute: Int, val durationMinutes: Int,
    val location: String? = null, val notes: String? = null,
)

data class AssignmentDraft(
    val id: Long? = null, val courseId: Long?, val title: String, val description: String? = null,
    val dueEpochDay: Long, val dueMinute: Int? = null, val priority: Priority = Priority.MEDIUM,
)

/** A timer-style study session: course and title are optional, the length is in seconds. */
data class SessionDraft(
    val id: Long? = null, val courseId: Long? = null, val title: String = "",
    val durationSeconds: Int, val notes: String? = null,
)
