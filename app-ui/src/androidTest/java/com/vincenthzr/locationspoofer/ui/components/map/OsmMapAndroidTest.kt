package com.vincenthzr.locationspoofer.ui.components.map

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vincenthzr.locationspoofer.ui.components.AppMapController
import com.vincenthzr.locationspoofer.ui.components.MarkerType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Hosts the production Compose adapter; tile downloads are disabled before its first draw. */
class OsmTestActivity : ComponentActivity() {
    val ready = CountDownLatch(1)
    lateinit var controller: AppMapController
    lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OsmMapViewContainer(this, this, Modifier.fillMaxSize(), onMapReady = {
                controller = it
                // The adapter intentionally exposes only AppMapController; inspect the actual
                // MapView to disable network and check its WGS-84 center and overlays.
                val field = OsmMapController::class.java.getDeclaredField("map")
                field.isAccessible = true
                map = field.get(it) as MapView
                map.setUseDataConnection(false)
                ready.countDown()
            }, onControllerCreated = {})
        }
    }
}

@RunWith(AndroidJUnit4::class)
class OsmMapAndroidTest {
    @Test fun cameraOverlaysAndLifecycleUseRealAdapterWithoutNetwork() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scenario = ActivityScenario.launch(OsmTestActivity::class.java)
        lateinit var activity: OsmTestActivity
        try {
            scenario.onActivity { activity = it }
            assertTrue("OSM map was not created", activity.ready.await(10, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val wgsLat = 39.908823
                val wgsLng = 116.397470
                val gcj = wgs84ToGcj02(wgsLat, wgsLng)
                assertTrue("test coordinate must exercise China conversion", kotlin.math.abs(gcj.second - wgsLng) > 0.001)
                it.controller.moveCamera(gcj.first, gcj.second, 18f)
                assertEquals(wgsLat, it.map.mapCenter.latitude, 0.0001)
                assertEquals(wgsLng, it.map.mapCenter.longitude, 0.0001)
                assertEquals(gcj.first, it.controller.cameraTargetLat!!, 0.0001)
                assertEquals(gcj.second, it.controller.cameraTargetLng!!, 0.0001)

                it.controller.clear()
                val marker = it.controller.addMarker(gcj.first, gcj.second, "A", MarkerType.GREEN)
                it.controller.addPolyline(listOf(gcj, gcj.first + 0.001 to gcj.second + 0.001), Color.BLUE, 4f)
                assertEquals(2, it.map.overlays.size)
                val mapMarker = it.map.overlays[0] as Marker
                assertTrue(it.map.overlays[1] is Polyline)
                assertEquals(wgsLat, mapMarker.position.latitude, 0.0001)
                assertEquals(wgsLng, mapMarker.position.longitude, 0.0001)
                marker.setPosition(gcj.first + 0.001, gcj.second + 0.001)
                assertTrue(mapMarker.position.latitude > wgsLat)
                it.controller.clear()
                assertEquals(0, it.map.overlays.size)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { it.controller.moveCamera(39.91, 116.40, 17f) }
        } finally {
            scenario.close()
        }
        instrumentation.waitForIdleSync()
        assertFalse("destroyed map must leave the window", activity.map.isAttachedToWindow)
    }
}
