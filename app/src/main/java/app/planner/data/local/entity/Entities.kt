package app.planner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Time model
 * ----------
 * Event times are "floating" wall-clock values: epochDay (LocalDate.toEpochDay) + minuteOfDay (0..1439).
 * They are resolved to an instant using the device's CURRENT zone at scheduling time, so a timezone
 * change keeps "09:00" meaning 09:00 and only alarms need rescheduling (Phase 9).
 * Audit timestamps (createdAt/updatedAt/deletedAt/completedAt) are UTC epoch millis.
 *
 * Deletion model
 * --------------
 * User-facing rows use soft delete (deletedAt != null) and foreign keys use RESTRICT, so a
 * parent can never silently destroy children ("no accidental data deletion").
 */

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String? = null,
    val instructor: String? = null,
    val colorArgb: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "study_events",
    foreignKeys = [ForeignKey(CourseEntity::class, ["id"], ["courseId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("courseId")],
)
data class StudyEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val type: StudyEventType,
    val attendance: AttendanceFormat,
    /** ISO day of week: 1 = Monday .. 7 = Sunday (java.time.DayOfWeek.value). */
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val location: String? = null,
    val onlineLink: String? = null,
    val notes: String? = null,
    /** 0 = the class ends on the same day it starts, 1 = it ends the next day (11 PM -> 12:30 AM). */
    @ColumnInfo(defaultValue = "0") val endDayOffset: Int = 0,
    /** 1 = every week, 2 = every second week (an alternating schedule). */
    @ColumnInfo(defaultValue = "1") val repeatEveryWeeks: Int = 1,
    /** A Saturday that starts a week in which this class takes place (only used when repeatEveryWeeks > 1). */
    @ColumnInfo(defaultValue = "0") val anchorWeekStart: Long = 0,
    /** Optional short label, e.g. "Electric" / "Magnetic". */
    val topic: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "study_sessions",
    foreignKeys = [ForeignKey(CourseEntity::class, ["id"], ["courseId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("courseId"), Index("dateEpochDay")],
)
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long?,
    val title: String,
    val dateEpochDay: Long,
    val startMinute: Int,
    /** Whole minutes of the session. Total length = durationMinutes * 60 + extraSeconds. */
    val durationMinutes: Int,
    /** 0..59, added in schema v2 so a session can be set in hours/minutes/seconds. */
    @ColumnInfo(defaultValue = "0") val extraSeconds: Int = 0,
    val notes: String? = null,
    val completed: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "exams",
    foreignKeys = [ForeignKey(CourseEntity::class, ["id"], ["courseId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("courseId"), Index("dateEpochDay")],
)
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val kind: ExamKind,
    val title: String,
    val dateEpochDay: Long,
    val startMinute: Int,
    val durationMinutes: Int,
    val location: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "assignments",
    foreignKeys = [ForeignKey(CourseEntity::class, ["id"], ["courseId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("courseId"), Index("dueEpochDay")],
)
data class AssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val title: String,
    val description: String? = null,
    val dueEpochDay: Long,
    val dueMinute: Int? = null,
    val priority: Priority = Priority.MEDIUM,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Bookkeeping for every alarm the app schedules (never a source of event data; the calendar does
 * not read it). The unique index prevents duplicate reminders; requestCode is the stable
 * PendingIntent / notification id.
 */
@Entity(
    tableName = "notification_records",
    indices = [
        Index(value = ["ownerType", "ownerId", "kind", "occurrenceKey"], unique = true),
        Index(value = ["requestCode"], unique = true),
        Index("status"),
    ],
)
data class NotificationRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerType: NotificationOwnerType,
    val ownerId: Long,
    val kind: NotificationKind,
    /** epochDay of the occurrence for recurring events; 0 for one-off owners. */
    val occurrenceKey: Long = 0,
    val requestCode: Int,
    val triggerAtMillis: Long,
    val status: NotificationStatus,
    val firedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Key-value application settings (includes the local device identifier under key "device_id"). */
@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long,
)

/** Total length of a study session in seconds. */
val StudySessionEntity.totalSeconds: Int get() = durationMinutes * 60 + extraSeconds
