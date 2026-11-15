package app.planner.domain.validation

import app.planner.domain.model.*

enum class ValidationError {
    NAME_REQUIRED, TITLE_REQUIRED, COURSE_REQUIRED, DAY_INVALID,
    TIME_INVALID, END_BEFORE_START, DURATION_INVALID, NOT_FOUND,
}

/** Pure functions: return null when valid, otherwise the first problem found. */
object StudyValidator {
    const val MAX_SESSION_SECONDS = 24 * 60 * 60

    private fun minuteOk(m: Int) = m in 0..1439

    fun course(d: CourseDraft): ValidationError? =
        if (d.name.isBlank()) ValidationError.NAME_REQUIRED else null

    /** At least one weekday must be chosen. */
    fun studyEvent(d: StudyEventDraft): ValidationError? = when {
        d.courseId == null -> ValidationError.COURSE_REQUIRED
        d.days.isEmpty() || d.days.any { it !in 1..7 } -> ValidationError.DAY_INVALID
        d.repeatEveryWeeks !in 1..2 -> ValidationError.DAY_INVALID
        !minuteOk(d.startMinute) || !minuteOk(d.endMinute) || d.endDayOffset !in 0..1 -> ValidationError.TIME_INVALID
        classMinutes(d.startMinute, d.endMinute, d.endDayOffset) <= 0 -> ValidationError.END_BEFORE_START
        else -> null
    }

    /**
     * Length of a class in minutes, or 0 when the end is not after the start. The end day is explicit:
     * Friday 11 PM -> the same day needs an end after 11 PM, -> the next day (Saturday) needs an end before 11 PM,
     * so "12 AM Friday" and "12 AM Saturday" can never be mixed up. A class is always shorter than 24 hours.
     */
    fun classMinutes(start: Int, end: Int, endDayOffset: Int = 0): Int {
        val minutes = endDayOffset * 24 * 60 + end - start
        return if (minutes in 1 until 24 * 60) minutes else 0
    }

    /** The title is optional (it defaults to "<exam type> <course>"). */
    fun exam(d: ExamDraft): ValidationError? = when {
        d.courseId == null -> ValidationError.COURSE_REQUIRED
        !minuteOk(d.startMinute) -> ValidationError.TIME_INVALID
        d.durationMinutes !in 1..1440 -> ValidationError.DURATION_INVALID
        else -> null
    }

    fun assignment(d: AssignmentDraft): ValidationError? = when {
        d.courseId == null -> ValidationError.COURSE_REQUIRED
        d.title.isBlank() -> ValidationError.TITLE_REQUIRED
        d.dueMinute != null && !minuteOk(d.dueMinute) -> ValidationError.TIME_INVALID
        else -> null
    }

    /** Course and title are optional; the length must be 1 second .. 24 hours. */
    fun session(d: SessionDraft): ValidationError? =
        if (d.durationSeconds !in 1..MAX_SESSION_SECONDS) ValidationError.DURATION_INVALID else null
}
