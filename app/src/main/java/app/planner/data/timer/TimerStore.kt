package app.planner.data.timer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

val Context.timerDataStore: DataStore<Preferences> by preferencesDataStore(name = "timer")

enum class TimerStatus { IDLE, RUNNING, PAUSED }

/** Persisted timer state. While RUNNING only the absolute end time is stored, so no ticking is needed to survive restarts. */
data class TimerSnapshot(
    val sessionId: Long? = null,
    val status: TimerStatus = TimerStatus.IDLE,
    val totalMs: Long = 0,
    val endAtMs: Long = 0,
    val remainingMs: Long = 0,
) {
    fun remaining(nowMs: Long): Long = when (status) {
        TimerStatus.RUNNING -> (endAtMs - nowMs).coerceIn(0L, totalMs)
        TimerStatus.PAUSED -> remainingMs
        TimerStatus.IDLE -> 0L
    }
}

class TimerStore(
    private val store: DataStore<Preferences>,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    val snapshot: Flow<TimerSnapshot> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSnapshot() }

    suspend fun start(sessionId: Long, totalMs: Long) {
        store.edit { p ->
            p[SESSION] = sessionId
            p[STATUS] = TimerStatus.RUNNING.name
            p[TOTAL] = totalMs
            p[END_AT] = now() + totalMs
            p[REMAINING] = totalMs
        }
    }

    suspend fun pause() {
        store.edit { p ->
            val s = p.toSnapshot()
            if (s.status == TimerStatus.RUNNING) {
                p[REMAINING] = s.remaining(now())
                p[STATUS] = TimerStatus.PAUSED.name
            }
        }
    }

    suspend fun resume() {
        store.edit { p ->
            val s = p.toSnapshot()
            if (s.status == TimerStatus.PAUSED) {
                p[END_AT] = now() + s.remainingMs
                p[STATUS] = TimerStatus.RUNNING.name
            }
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun Preferences.toSnapshot(): TimerSnapshot {
        val id = this[SESSION] ?: return TimerSnapshot()
        val status = runCatching { TimerStatus.valueOf(this[STATUS] ?: "") }.getOrDefault(TimerStatus.IDLE)
        if (status == TimerStatus.IDLE) return TimerSnapshot()
        return TimerSnapshot(id, status, this[TOTAL] ?: 0L, this[END_AT] ?: 0L, this[REMAINING] ?: 0L)
    }

    private companion object {
        val SESSION = longPreferencesKey("session_id")
        val STATUS = stringPreferencesKey("status")
        val TOTAL = longPreferencesKey("total_ms")
        val END_AT = longPreferencesKey("end_at_ms")
        val REMAINING = longPreferencesKey("remaining_ms")
    }
}
