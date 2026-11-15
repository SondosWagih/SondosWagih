package app.planner.data.repository

import androidx.room.withTransaction
import app.planner.data.local.db.AppDatabase
import app.planner.data.local.entity.*
import app.planner.domain.model.*
import app.planner.domain.schedule.WeekMath
import app.planner.domain.validation.StudyValidator
import app.planner.domain.validation.ValidationError
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for Study data. Every save validates first and returns null on success or
 * the first ValidationError. Deletes are soft; deleting a course hides its children in ONE transaction.
 * (Phase 7 hooks notification rescheduling into these same save/delete methods.)
 */
class StudyRepository(
    private val db: AppDatabase,
    /** Called after every change that can affect reminders (alarms are re-planned there). */
    private val onChanged: () -> Unit = {},
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    val courses: Flow<List<CourseEntity>> = db.courseDao().observeActive()
    val events: Flow<List<StudyEventEntity>> = db.studyEventDao().observeActive()
    val exams: Flow<List<ExamEntity>> = db.examDao().observeAll()
    val assignments: Flow<List<AssignmentEntity>> = db.assignmentDao().observeAll()
    val sessions: Flow<List<StudySessionEntity>> = db.studySessionDao().observeAll()

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private suspend fun courseExists(id: Long): Boolean {
        val c = db.courseDao().get(id)
        return c != null && c.deletedAt == null
    }

    suspend fun saveCourse(d: CourseDraft): ValidationError? {
        StudyValidator.course(d)?.let { return it }
        val t = now()
        val id = d.id
        if (id == null) {
            db.courseDao().insert(
                CourseEntity(name = d.name.trim(), code = d.code.clean(), instructor = d.instructor.clean(),
                    colorArgb = d.colorArgb, createdAt = t, updatedAt = t)
            )
        } else {
            val old = db.courseDao().get(id)?.takeIf { it.deletedAt == null } ?: return ValidationError.NOT_FOUND
            db.courseDao().update(old.copy(name = d.name.trim(), code = d.code.clean(),
                instructor = d.instructor.clean(), colorArgb = d.colorArgb, updatedAt = t))
        }
        onChanged()
        return null
    }

    /**
     * Creating: one row per selected day. Editing: the row being edited keeps its day if it is still selected
     * (otherwise it moves to the first selected day) and every other selected day gets a new copy.
     */
    suspend fun saveEvent(d: StudyEventDraft): ValidationError? {
        StudyValidator.studyEvent(d)?.let { return it }
        val courseId = d.courseId ?: return ValidationError.COURSE_REQUIRED
        if (!courseExists(courseId)) return ValidationError.COURSE_REQUIRED
        val days = d.days.toSortedSet().toList()
        val t = now()
        val repeat = d.repeatEveryWeeks
        val thisWeek = WeekMath.weekStartOf(WeekMath.epochDayOf(t, ZoneId.systemDefault()))
        val anchor = if (repeat > 1) thisWeek + (if (d.startsThisWeek) 0L else 7L) else 0L
        val id = d.id
        val old = if (id == null) null
        else (db.studyEventDao().get(id)?.takeIf { it.deletedAt == null } ?: return ValidationError.NOT_FOUND)

        fun row(day: Int) = StudyEventEntity(
            courseId = courseId, type = d.type, attendance = d.attendance, dayOfWeek = day,
            startMinute = d.startMinute, endMinute = d.endMinute, endDayOffset = d.endDayOffset,
            location = d.location.clean(), onlineLink = d.onlineLink.clean(), notes = d.notes.clean(),
            repeatEveryWeeks = repeat, anchorWeekStart = anchor, topic = d.topic.clean(),
            createdAt = t, updatedAt = t,
        )

        db.withTransaction {
            if (old == null) {
                days.forEach { db.studyEventDao().insert(row(it)) }
            } else {
                val keep = if (old.dayOfWeek in days) old.dayOfWeek else days.first()
                val fresh = row(keep)
                db.studyEventDao().update(old.copy(
                    courseId = fresh.courseId, type = fresh.type, attendance = fresh.attendance, dayOfWeek = keep,
                    startMinute = fresh.startMinute, endMinute = fresh.endMinute, endDayOffset = fresh.endDayOffset,
                    location = fresh.location,
                    onlineLink = fresh.onlineLink, notes = fresh.notes, repeatEveryWeeks = fresh.repeatEveryWeeks,
                    anchorWeekStart = fresh.anchorWeekStart, topic = fresh.topic, updatedAt = t,
                ))
                days.filter { it != keep }.forEach { db.studyEventDao().insert(row(it)) }
            }
        }
        onChanged()
        return null
    }

    suspend fun saveExam(d: ExamDraft): ValidationError? {
        StudyValidator.exam(d)?.let { return it }
        val courseId = d.courseId ?: return ValidationError.COURSE_REQUIRED
        if (!courseExists(courseId)) return ValidationError.COURSE_REQUIRED
        val t = now()
        val id = d.id
        if (id == null) {
            db.examDao().insert(
                ExamEntity(courseId = courseId, kind = d.kind, title = d.title.trim(), dateEpochDay = d.dateEpochDay,
                    startMinute = d.startMinute, durationMinutes = d.durationMinutes,
                    location = d.location.clean(), notes = d.notes.clean(), createdAt = t, updatedAt = t)
            )
        } else {
            val old = db.examDao().get(id)?.takeIf { it.deletedAt == null } ?: return ValidationError.NOT_FOUND
            db.examDao().update(old.copy(courseId = courseId, kind = d.kind, title = d.title.trim(),
                dateEpochDay = d.dateEpochDay, startMinute = d.startMinute, durationMinutes = d.durationMinutes,
                location = d.location.clean(), notes = d.notes.clean(), updatedAt = t))
        }
        onChanged()
        return null
    }

    suspend fun saveAssignment(d: AssignmentDraft): ValidationError? {
        StudyValidator.assignment(d)?.let { return it }
        val courseId = d.courseId ?: return ValidationError.COURSE_REQUIRED
        if (!courseExists(courseId)) return ValidationError.COURSE_REQUIRED
        val t = now()
        val id = d.id
        if (id == null) {
            db.assignmentDao().insert(
                AssignmentEntity(courseId = courseId, title = d.title.trim(), description = d.description.clean(),
                    dueEpochDay = d.dueEpochDay, dueMinute = d.dueMinute, priority = d.priority,
                    createdAt = t, updatedAt = t)
            )
        } else {
            val old = db.assignmentDao().get(id)?.takeIf { it.deletedAt == null } ?: return ValidationError.NOT_FOUND
            db.assignmentDao().update(old.copy(courseId = courseId, title = d.title.trim(),
                description = d.description.clean(), dueEpochDay = d.dueEpochDay, dueMinute = d.dueMinute,
                priority = d.priority, updatedAt = t))
        }
        onChanged()
        return null
    }

    suspend fun saveSession(d: SessionDraft): ValidationError? {
        StudyValidator.session(d)?.let { return it }
        d.courseId?.let { if (!courseExists(it)) return ValidationError.COURSE_REQUIRED }
        val t = now()
        val id = d.id
        val minutes = d.durationSeconds / 60
        val seconds = d.durationSeconds % 60
        if (id == null) {
            // dateEpochDay/startMinute are no longer user-facing; they just record when the session was created.
            val today = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            db.studySessionDao().insert(
                StudySessionEntity(courseId = d.courseId, title = d.title.trim(), dateEpochDay = today, startMinute = 0,
                    durationMinutes = minutes, extraSeconds = seconds, notes = d.notes.clean(), createdAt = t, updatedAt = t)
            )
        } else {
            val old = db.studySessionDao().get(id)?.takeIf { it.deletedAt == null } ?: return ValidationError.NOT_FOUND
            db.studySessionDao().update(old.copy(courseId = d.courseId, title = d.title.trim(),
                durationMinutes = minutes, extraSeconds = seconds, notes = d.notes.clean(), updatedAt = t))
        }
        onChanged()
        return null
    }

    suspend fun setAssignmentCompleted(id: Long, done: Boolean) {
        val t = now()
        db.assignmentDao().setCompleted(id, done, if (done) t else null, t)
        onChanged()
    }

    suspend fun setSessionCompleted(id: Long, done: Boolean) {
        db.studySessionDao().setCompleted(id, done, now())
    }

    suspend fun deleteEvent(id: Long) { db.studyEventDao().softDelete(id, now()); onChanged() }
    suspend fun deleteExam(id: Long) { db.examDao().softDelete(id, now()); onChanged() }
    suspend fun deleteAssignment(id: Long) { db.assignmentDao().softDelete(id, now()); onChanged() }
    suspend fun deleteSession(id: Long) { db.studySessionDao().softDelete(id, now()) }

    /** Title shown for a session (its own title, else its course name), or null if it no longer exists. */
    suspend fun sessionTitle(id: Long, fallback: String): String? {
        val s = db.studySessionDao().get(id)?.takeIf { it.deletedAt == null } ?: return null
        if (s.title.isNotBlank()) return s.title
        return s.courseId?.let { db.courseDao().get(it)?.name } ?: fallback
    }

    /** Atomic: either the course and everything under it is hidden, or nothing is. */
    suspend fun deleteCourse(id: Long) {
        db.withTransaction {
            val t = now()
            db.studyEventDao().softDeleteByCourse(id, t)
            db.examDao().softDeleteByCourse(id, t)
            db.assignmentDao().softDeleteByCourse(id, t)
            db.studySessionDao().softDeleteByCourse(id, t)
            db.courseDao().softDelete(id, t)
        }
        onChanged()
    }
}
