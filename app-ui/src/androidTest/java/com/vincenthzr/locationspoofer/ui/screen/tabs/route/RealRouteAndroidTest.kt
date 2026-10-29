package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.location.LocationManager
import android.os.SystemClock
import android.util.AtomicFile
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.data.state.recordingSimulationCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

class RecordingTestActivity : Activity()

@RunWith(AndroidJUnit4::class)
class RealRouteAndroidTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
    )

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var scratch: File

    @Before fun setup() {
        scratch = File(context.cacheDir, "real-route-instrumentation-${SystemClock.elapsedRealtimeNanos()}")
        check(scratch.mkdirs())
    }

    @After fun cleanup() {
        instrumentation.runOnMainSync { RealRouteRecorder.getInstance(context).pause() }
        scratch.deleteRecursively()
    }

    @Test fun atomicDraftSurvivesInterruptedWriteAndRejectsCorruption() {
        val isolated = object : ContextWrapper(context) { override fun getFilesDir(): File = scratch }
        val draft = RealRouteDraft(isolated)
        val original = RealRouteDraftData(listOf(RoutePoint(31.2, 121.4), RoutePoint(31.21, 121.41)), 31.21 to 121.41)
        draft.save(original)
        assertEquals(original, RealRouteDraft(isolated).load())

        val atomic = AtomicFile(File(scratch, "real-route-draft-v1"))
        val interrupted = atomic.startWrite()
        interrupted.write("partial".toByteArray())
        interrupted.close() // Simulate interruption before finishWrite, without failWrite.
        assertEquals(original, RealRouteDraft(isolated).load())

        atomic.baseFile.writeText("real-route-v2\ngap,none\nNaN,121\n")
        try {
            draft.load()
            fail("Corrupt draft must not silently become an empty route")
        } catch (_: IllegalArgumentException) { }
        draft.save(RealRouteDraftData(emptyList()))
        assertEquals(emptyList<RoutePoint>(), draft.load().points)
    }

    @Test fun foregroundRecordingCanPauseAndResume() {
        val location = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        assertTrue("Enable GPS on the test emulator before running", location.isProviderEnabled(LocationManager.GPS_PROVIDER))
        val recorder = RealRouteRecorder.getInstance(context)
        ActivityScenario.launch(RecordingTestActivity::class.java).use {
            instrumentation.runOnMainSync {
                assertTrue("start rejected: ${recorder.error}", recorder.start())
            }
            await("service did not start recording") { recorder.recording && serviceRunning() }
            SystemClock.sleep(250)
            instrumentation.runOnMainSync { recorder.pause() }
            await("service did not stop after pause") { !recorder.recording && !serviceRunning() }
            var pausedElapsed = 0L
            instrumentation.runOnMainSync { pausedElapsed = recorder.statistics.elapsed(SystemClock.elapsedRealtime()) }
            assertTrue("active recording time was not counted", pausedElapsed >= 200)
            SystemClock.sleep(250)
            instrumentation.runOnMainSync {
                assertEquals("paused time must not accumulate", pausedElapsed, recorder.statistics.elapsed(SystemClock.elapsedRealtime()))
            }
            instrumentation.runOnMainSync {
                assertTrue("resume rejected: ${recorder.error}", recorder.start())
            }
            await("service did not resume recording") { recorder.recording && serviceRunning() }
            SystemClock.sleep(250)
            runBlocking { recordingSimulationCoordinator.beforeSimulation() }
            await("coordinator did not stop recording") { !recorder.recording && !serviceRunning() }
            instrumentation.runOnMainSync {
                assertTrue("resumed time was not counted", recorder.statistics.elapsed(SystemClock.elapsedRealtime()) >= pausedElapsed + 200)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun serviceRunning(): Boolean = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .getRunningServices(Int.MAX_VALUE).any { it.service.className == RealRouteRecordingService::class.java.name }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail(message)
    }
}
