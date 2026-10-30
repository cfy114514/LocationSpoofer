package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng
import com.vincenthzr.locationspoofer.utils.GeoMath
import org.junit.Assert.assertEquals
import org.junit.Test

class RealRouteStatisticsTest {
    @Test fun `clock counts active segments only and reset clears them`() {
        val stats = RealRouteStatistics()
        stats.start(1000)
        assertEquals(5000L, stats.elapsed(6000))
        stats.pause(6000)
        assertEquals(5000L, stats.elapsed(12000))
        stats.start(12000)
        stats.start(14000)
        assertEquals(8000L, stats.elapsed(15000))
        stats.pause(15000)
        stats.reset()
        assertEquals(0L, stats.elapsed(20000))
        assertEquals("1:01:01", formatRecordingDuration(3661000))
    }

    @Test fun `distance follows stored points including a pause connection`() {
        val a = LatLng(30.0, 120.0)
        val b = GeoMath.destination(a, 0.0, 100.0)
        val c = GeoMath.destination(b, 90.0, 50.0)
        val points = listOf(a, b, c).map { RoutePoint(it.lat, it.lng) }
        assertEquals(150.0, realRouteDistanceMeters(points), 0.1)
        assertEquals(0.0, realRouteDistanceMeters(points.take(1)), 0.0)
    }
}
