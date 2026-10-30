package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.AtomicFile
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.filters.SdkSuppress
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.data.state.recordingSimulationCoordinator
import com.vincenthzr.locationspoofer.utils.CoordinateUtils
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

    @Test fun rapidPauseAndRestartKeepsRecorderRunning() {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        assertTrue("Enable GPS on the test emulator before running", manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        val recorder = RealRouteRecorder.getInstance(context)
        try {
            instrumentation.runOnMainSync { recorder.clear() }
            await("test draft could not be cleared") { !recorder.draftPending && !recorder.storageError }
            ActivityScenario.launch(RecordingTestActivity::class.java).use {
                instrumentation.runOnMainSync { assertTrue("initial start rejected: ${recorder.error}", recorder.start()) }
                await("initial recorder did not start") { recorder.recording && serviceRunning() }
                repeat(3) { attempt ->
                    // Detach live callbacks and wait for any already queued draft write so
                    // only the service stop/start ordering is exercised. Both calls happen
                    // in one main-thread turn, before onDestroy can run on that thread.
                    await("recorder not ready for rapid restart") {
                        if (!recorder.recording || !serviceRunning()) false
                        else {
                            manager.removeUpdates(recorder)
                            if (recorder.draftPending || recorder.storageError) false
                            else {
                                recorder.pause()
                                assertTrue("rapid restart ${attempt + 1} rejected: ${recorder.error}", recorder.start())
                                true
                            }
                        }
                    }
                    await("rapid restart ${attempt + 1} did not remain recording") { recorder.recording && serviceRunning() }
                }
            }
        } finally {
            instrumentation.runOnMainSync { recorder.clear() }
            await("test draft cleanup failed") { !recorder.draftPending && !recorder.storageError && !serviceRunning() }
        }
    }

    @Test @SdkSuppress(minSdkVersion = 31)
    fun syntheticCallbacksFilterConvertPersistAndResumeAfterGap() {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        assertTrue("Enable GPS on the test emulator before running", manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        val recorder = RealRouteRecorder.getInstance(context)
        // Each Location below is synthetic and passed directly to the production callback.
        // Nothing is installed as a system test provider or injected into GPS.
        fun sample(lat: Double, lng: Double, ageMs: Long = 0, mock: Boolean = false) = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = lat
            longitude = lng
            accuracy = 5f
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - ageMs * 1_000_000L
            setMock(mock)
        }
        fun startWithoutFurtherSystemFixes() {
            instrumentation.runOnMainSync { assertTrue("start rejected: ${recorder.error}", recorder.start()) }
            await("recorder did not start") {
                if (!recorder.recording) false else { manager.removeUpdates(recorder); true }
            }
        }

        try {
            instrumentation.runOnMainSync { recorder.clear() }
            await("test draft could not be cleared") { !recorder.draftPending && !recorder.storageError }
            ActivityScenario.launch(RecordingTestActivity::class.java).use {
                startWithoutFurtherSystemFixes()
                var baseline = 0
                var firstLat = 0.0
                var secondLat = 0.0
                var longitude = 0.0
                instrumentation.runOnMainSync {
                    // If the emulator delivered a genuine fix before subscription removal,
                    // continue near it instead of introducing an artificial intercontinental jump.
                    baseline = recorder.points.size
                    val base = recorder.points.lastOrNull()?.let { point ->
                        CoordinateUtils.gcj02ToWgs84(point.lat, point.lng)
                    } ?: CoordinateUtils.LatLng(39.908823, 116.397470)
                    val direction = if (base.lat < 89.0) 1.0 else -1.0
                    firstLat = base.lat + direction * 0.0001
                    secondLat = base.lat + direction * 0.0002
                    longitude = base.lng
                    recorder.onLocationChanged(sample(firstLat, longitude))
                    recorder.onLocationChanged(sample(secondLat, longitude))
                    assertEquals(baseline + 2, recorder.points.size)
                    val expected = CoordinateUtils.wgs84ToGcj02(secondLat, longitude)
                    assertEquals(expected.lat, recorder.points.last().lat, 1e-9)
                    assertEquals(expected.lng, recorder.points.last().lng, 1e-9)

                    recorder.onLocationChanged(sample(secondLat + direction * 0.0001, longitude, mock = true))
                    recorder.onLocationChanged(sample(secondLat + direction * 0.0001, longitude, ageMs = 30_000))
                    val farLongitude = if (longitude < 178.0) longitude + 1.0 else longitude - 1.0
                    recorder.onLocationChanged(sample(secondLat, farLongitude))
                    assertEquals("mock, stale and implausible points must be rejected", baseline + 2, recorder.points.size)
                }
                await("accepted points were not saved") { !recorder.draftPending && !recorder.storageError }
                val persisted = RealRouteDraft(context).load()
                assertEquals(baseline + 2, persisted.points.size)
                assertEquals(CoordinateUtils.wgs84ToGcj02(secondLat, longitude).lat, persisted.points.last().lat, 1e-9)

                instrumentation.runOnMainSync {
                    recorder.onProviderDisabled(LocationManager.GPS_PROVIDER)
                    assertFalse(recorder.recording)
                    assertEquals("gps", recorder.error)
                    assertTrue("disabled GPS must retain the last point as a gap", recorder.hasGap)
                    recorder.onLocationChanged(sample(firstLat, longitude))
                    assertEquals("paused recorder must ignore points", baseline + 2, recorder.points.size)
                }
                await("provider-disabled anchor was not saved") { !recorder.draftPending && !recorder.storageError && !serviceRunning() }
                assertEquals(secondLat to longitude, RealRouteDraft(context).load().gapAnchor)
                startWithoutFurtherSystemFixes()
                instrumentation.runOnMainSync {
                    val direction = if (secondLat < 89.0) 1.0 else -1.0
                    recorder.onLocationChanged(sample(secondLat + direction * 0.0006, longitude))
                    assertFalse("resume far from disabled GPS anchor must pause", recorder.recording)
                    assertEquals(baseline + 2, recorder.points.size)
                }
                await("service did not stop after distant GPS resume") { !serviceRunning() }
                startWithoutFurtherSystemFixes()
                instrumentation.runOnMainSync {
                    recorder.onLocationChanged(sample(secondLat, longitude))
                    assertFalse(recorder.hasGap)
                    assertEquals(baseline + 3, recorder.points.size)
                    recorder.pause()
                }
                await("resumed draft was not saved") { !recorder.draftPending && !recorder.storageError && !serviceRunning() }
                val recovered = RealRouteDraft(context).load()
                assertEquals(baseline + 3, recovered.points.size)
                assertNull(recovered.gapAnchor)
            }
        } finally {
            instrumentation.runOnMainSync { recorder.clear() }
            await("test draft cleanup failed") { !recorder.draftPending && !recorder.storageError && !serviceRunning() }
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
