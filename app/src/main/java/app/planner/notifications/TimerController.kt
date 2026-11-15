package app.planner.notifications

import android.content.Context
import app.planner.R
import app.planner.data.local.entity.StudySessionEntity
import app.planner.data.local.entity.totalSeconds
import app.planner.data.repository.StudyRepository
import app.planner.data.timer.TimerStatus
import app.planner.data.timer.TimerStore
import kotlinx.coroutines.flow.first

/**
 * The study timer, from three places at once: the app, the notification buttons and the alarm that fires when
 * time is up. The countdown itself lives in the system (end time + chronometer notification), so it keeps going
 * after the app is closed.
 */
class TimerController(
    private val context: Context,
    private val store: TimerStore,
    private val repo: StudyRepository,
    private val notifier: Notifier,
    private val scheduler: NotificationScheduler,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private suspend fun titleOf(sessionId: Long?): String {
        val fallback = localizedContext(context).getString(R.string.session_default_title)
        return sessionId?.let { repo.sessionTitle(it, fallback) } ?: fallback
    }

    suspend fun start(session: StudySessionEntity) {
        if (store.snapshot.first().status != TimerStatus.IDLE) return
        notifier.cancelTimerDone()
        store.start(session.id, session.totalSeconds * 1000L)
        sync()
    }

    suspend fun pause() { store.pause(); sync() }
    suspend fun resume() { store.resume(); sync() }
    suspend fun stop() { store.clear(); sync() }

    /** Makes the ongoing notification and the end alarm match the stored state. */
    suspend fun sync() {
        val s = store.snapshot.first()
        when (s.status) {
            TimerStatus.IDLE -> notifier.cancelTimer()
            TimerStatus.RUNNING -> notifier.showTimerRunning(titleOf(s.sessionId), s.endAtMs)
            TimerStatus.PAUSED -> notifier.showTimerPaused(titleOf(s.sessionId), s.remainingMs)
        }
        scheduler.rescheduleAll()
    }

    /** If the countdown has reached zero: mark the session done, announce it. Returns the session id, or null. */
    suspend fun completeIfExpired(toleranceMs: Long = 0): Long? {
        val s = store.snapshot.first()
        val id = s.sessionId
        if (s.status != TimerStatus.RUNNING || id == null || s.remaining(now()) > toleranceMs) return null
        val title = titleOf(id)
        repo.setSessionCompleted(id, true)
        store.clear()
        notifier.cancelTimer()
        notifier.showTimerDone(title)
        scheduler.rescheduleAll()
        return id
    }

    /** App start / boot: finish a timer that ended meanwhile, or bring back its notification and alarm. */
    suspend fun restore() {
        if (completeIfExpired() == null) sync()
    }
}
