package app.planner.notifications

import app.planner.data.local.db.AppDatabase
import app.planner.data.local.entity.NotificationRecordEntity
import app.planner.data.local.entity.NotificationStatus
import app.planner.data.timer.TimerStore
import app.planner.domain.schedule.AlarmPlanner
import app.planner.domain.schedule.WeekMath
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the real alarms in line with the data. [rescheduleAll] is idempotent and safe to call any time:
 * after an edit or deletion, at boot, when the time or time zone changes, when permissions change, and after a
 * reminder fires (to plan the next occurrence). Duplicates are impossible because every alarm has a stable
 * request code; obsolete alarms are cancelled.
 */
class NotificationScheduler(
    private val db: AppDatabase,
    private val timerStore: TimerStore,
    private val alarms: AlarmScheduler,
    private val notifier: Notifier,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun requestReschedule() {
        scope.launch { rescheduleAll() }
    }

    suspend fun rescheduleAll() {
        mutex.withLock {
            val nowMs = now()
            val z = zone()
            val incomplete = db.assignmentDao().getIncomplete()
            val plan = AlarmPlanner.plan(
                events = db.studyEventDao().getActive(),
                exams = db.examDao().getActive(),
                incompleteAssignments = incomplete,
                timer = timerStore.snapshot.first(),
                nowMillis = nowMs,
                zone = z,
            )
            val dao = db.notificationRecordDao()
            val wanted = plan.map { it.requestCode }.toSet()

            // 1) cancel whatever is no longer wanted (edited, deleted, completed, ...)
            for (record in dao.allActive()) {
                if (record.requestCode !in wanted) {
                    alarms.cancel(record.requestCode)
                    dao.setStatus(record.id, NotificationStatus.CANCELLED, nowMs)
                }
            }

            // 2) (re)schedule what is wanted. Without exact-alarm permission we do NOT fall back to inexact
            //    alarms: the record says BLOCKED and the app shows how to fix it.
            val canExact = alarms.canScheduleExact()
            for (p in plan) {
                val ok = canExact && alarms.schedule(p)
                dao.upsert(
                    NotificationRecordEntity(
                        ownerType = p.ownerType, ownerId = p.ownerId, kind = p.kind, occurrenceKey = p.occurrenceKey,
                        requestCode = p.requestCode, triggerAtMillis = p.triggerAtMillis,
                        status = if (ok) NotificationStatus.SCHEDULED else NotificationStatus.BLOCKED_NO_PERMISSION,
                        createdAt = nowMs, updatedAt = nowMs,
                    )
                )
            }

            // 3) an "unfinished assignments" notification that is no longer true must disappear
            val today = WeekMath.epochDayOf(nowMs, z)
            if (incomplete.none { it.dueEpochDay < today }) notifier.cancelOverdue()
        }
    }
}
