package app.planner.data.local.entity

/** Lecture and Section are independent event types. */
enum class StudyEventType { LECTURE, SECTION }

/** Physical and Online are independent attendance formats; both types may use either. */
enum class AttendanceFormat { PHYSICAL, ONLINE }

enum class ExamKind { MIDTERM, FINAL }

enum class Priority { LOW, MEDIUM, HIGH }

enum class NotificationOwnerType { EXAM, STUDY_EVENT, ASSIGNMENT, SESSION }

enum class NotificationKind { ADVANCE_48H, ADVANCE_10M, AT_TIME, OVERDUE, TIMER_END }

enum class NotificationStatus { SCHEDULED, FIRED, CANCELLED, BLOCKED_NO_PERMISSION }
