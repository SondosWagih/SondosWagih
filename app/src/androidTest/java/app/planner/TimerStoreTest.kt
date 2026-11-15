package app.planner

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.planner.data.timer.TimerSnapshot
import app.planner.data.timer.TimerStatus
import app.planner.data.timer.TimerStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerStoreTest {
    @Test fun snapshotMathForEachStatus() {
        val running = TimerSnapshot(1, TimerStatus.RUNNING, totalMs = 60_000, endAtMs = 100_000)
        assertEquals(40_000L, running.remaining(60_000))
        assertEquals(0L, running.remaining(150_000))      // never negative
        assertEquals(60_000L, running.remaining(0))       // clock moved back: capped at the total
        assertEquals(12_000L, TimerSnapshot(1, TimerStatus.PAUSED, 60_000, 0, 12_000).remaining(999_999))
        assertEquals(0L, TimerSnapshot().remaining(5))
    }

    @Test fun startPauseResumeStopFollowTheClock() = runBlocking {
        val file = File.createTempFile("timer_test", ".preferences_pb").also { it.delete() }
        var clock = 1_000L
        val store = TimerStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) { file }) { clock }

        assertEquals(TimerStatus.IDLE, store.snapshot.first().status)

        store.start(sessionId = 7, totalMs = 60_000)
        var s = store.snapshot.first()
        assertEquals(TimerStatus.RUNNING, s.status)
        assertEquals(7L, s.sessionId)
        assertEquals(60_000L, s.remaining(clock))

        clock += 10_000
        assertEquals(50_000L, store.snapshot.first().remaining(clock))

        store.pause()
        clock += 30_000                                   // time passes while paused
        s = store.snapshot.first()
        assertEquals(TimerStatus.PAUSED, s.status)
        assertEquals(50_000L, s.remaining(clock))

        store.resume()
        clock += 5_000
        s = store.snapshot.first()
        assertEquals(TimerStatus.RUNNING, s.status)
        assertEquals(45_000L, s.remaining(clock))

        clock += 100_000
        assertEquals(0L, store.snapshot.first().remaining(clock))

        store.clear()
        assertEquals(TimerStatus.IDLE, store.snapshot.first().status)
    }
}
