package app.planner.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.planner.data.local.entity.StudySessionEntity
import app.planner.data.timer.TimerSnapshot
import app.planner.data.timer.TimerStatus
import app.planner.data.timer.TimerStore
import app.planner.notifications.TimerController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** remainingSeconds is rounded up, so the UI state only changes once per second. */
data class TimerUiState(
    val snapshot: TimerSnapshot = TimerSnapshot(),
    val remainingSeconds: Long = 0,
    val expired: Boolean = false,
) {
    val active: Boolean get() = snapshot.status != TimerStatus.IDLE
    val running: Boolean get() = snapshot.status == TimerStatus.RUNNING
    val totalSeconds: Long get() = snapshot.totalMs / 1000
}

class TimerViewModel(store: TimerStore, private val controller: TimerController) : ViewModel() {
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(250)
        }
    }

    val state: StateFlow<TimerUiState> = combine(store.snapshot, ticker) { snap, now ->
        val raw = snap.remaining(now)
        TimerUiState(snap, (raw + 999) / 1000, expired = snap.status == TimerStatus.RUNNING && raw <= 0)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TimerUiState())

    private val _finishedSessionId = MutableStateFlow<Long?>(null)
    /** Set when a timer reaches zero while the app is open; the UI shows "session complete" until dismissed. */
    val finishedSessionId: StateFlow<Long?> = _finishedSessionId

    private var finishing = false

    init {
        viewModelScope.launch {
            state.collect { s -> if (s.expired) finish() }
        }
    }

    private fun finish() {
        if (finishing) return
        finishing = true
        viewModelScope.launch {
            try {
                controller.completeIfExpired()?.let { _finishedSessionId.value = it }
            } finally {
                finishing = false
            }
        }
    }

    // Everything goes through the controller so the notification and the end alarm always match.
    fun start(session: StudySessionEntity) { viewModelScope.launch { controller.start(session) } }
    fun pause() { viewModelScope.launch { controller.pause() } }
    fun resume() { viewModelScope.launch { controller.resume() } }
    fun stop() { viewModelScope.launch { controller.stop() } }
    fun dismissFinished() { _finishedSessionId.value = null }

    companion object {
        fun factory(store: TimerStore, controller: TimerController) =
            viewModelFactory { initializer { TimerViewModel(store, controller) } }
    }
}
