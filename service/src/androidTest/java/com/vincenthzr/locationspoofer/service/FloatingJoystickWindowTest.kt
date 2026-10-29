package com.vincenthzr.locationspoofer.service

import android.app.Activity
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.data.motion.MotionController
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class JoystickTestActivity : Activity()

@RunWith(AndroidJUnit4::class)
class FloatingJoystickWindowTest {
    private class MemorySink : MotionController.MotionSink {
        override suspend fun writeManual(lat: Double, lng: Double, bearing: Float, speedMs: Double, timestamp: Long) = Unit
        override suspend fun writeRoute(
            lat: Double, lng: Double, bearing: Float, preset: MotionController.SpeedPreset,
            points: List<RoutePoint>, startTimestamp: Long, distanceOffset: Double, stopAtDestination: Boolean
        ) = Unit
        override suspend fun saveRoute(name: String, points: List<RoutePoint>) = Unit
    }

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }

    private fun hasOverlay(packageName: String): Boolean {
        val windows = shell("dumpsys window windows")
        val blocks = windows.split(Regex("(?m)(?=^\\s*Window #\\d+ Window\\{)"))
        return blocks.any { block ->
            block.contains(packageName) &&
                Regex("(?:ty|type)=(?:APPLICATION_OVERLAY|2038)\\b|TYPE_APPLICATION_OVERLAY").containsMatchIn(block)
        }
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        assertTrue(message, condition())
    }

    @Test
    fun staticSessionCreatesAndRemovesRealOverlayWindow() {
        assertTrue("Test requires its own Koin context", GlobalContext.getOrNull() == null)
        val controller = MotionController(MemorySink(), realismParams = { 0 to 0 }, autoTick = false)
        startKoin { modules(module { single { controller } }) }
        var scenario: ActivityScenario<JoystickTestActivity>? = null
        var packageName = ""
        try {
            controller.onStaticStarted(30.0, 120.0)
            scenario = ActivityScenario.launch(JoystickTestActivity::class.java)
            scenario.onActivity { activity ->
                packageName = activity.packageName
                assertTrue("Grant overlay app-op to $packageName before running this test", Settings.canDrawOverlays(activity))
                activity.startService(Intent(activity, FloatingJoystickService::class.java))
            }
            awaitCondition("Service did not show its overlay") { FloatingJoystickService.isShowing }
            awaitCondition("No TYPE_APPLICATION_OVERLAY window owned by $packageName") { hasOverlay(packageName) }

            controller.onStopped()
            awaitCondition("Service did not stop") { !FloatingJoystickService.isShowing }
            awaitCondition("Overlay window was not removed") { !hasOverlay(packageName) }
            awaitCondition("Service record still exists") {
                !shell("dumpsys activity services $packageName").contains("FloatingJoystickService")
            }
        } finally {
            controller.onStopped()
            scenario?.onActivity { it.stopService(Intent(it, FloatingJoystickService::class.java)) }
            try {
                awaitCondition("Service did not stop during cleanup") { !FloatingJoystickService.isShowing }
            } finally {
                scenario?.close()
                stopKoin()
            }
        }
    }
}
