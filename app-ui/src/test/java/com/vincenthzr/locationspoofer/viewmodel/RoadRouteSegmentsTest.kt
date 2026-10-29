package com.vincenthzr.locationspoofer.viewmodel

import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.data.model.RouteTravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoadRouteSegmentsTest {
    private val a = RoutePoint(30.0, 120.0)
    private val b = RoutePoint(30.1, 120.1)
    private val c = RoutePoint(30.2, 120.2)

    @Test fun `selected travel mode is used for every segment and waypoints are joined`() {
        for (mode in RouteTravelMode.entries) {
            val calls = mutableListOf<Triple<RoutePoint, RoutePoint, RouteTravelMode>>()
            val planned = planRoadSegments(listOf(a, b, c), mode) { start, end, selected ->
                calls.add(Triple(start, end, selected)); listOf(start, end)
            }
            assertEquals(listOf(a, b, c), planned)
            assertEquals(listOf(Triple(a, b, mode), Triple(b, c, mode)), calls)
        }
    }

    @Test fun `a failed or empty segment never returns a partial route`() {
        for (bad in listOf(null, emptyList(), listOf(b))) {
            val planned = planRoadSegments(listOf(a, b, c), RouteTravelMode.WALKING) { start, end, _ ->
                if (start == a) listOf(start, end) else bad
            }
            assertNull(planned)
        }
        assertNull(planRoadSegments(listOf(a), RouteTravelMode.DRIVING) { _, _, _ -> error("unexpected request") })
    }
    @Test fun `rejects invalid coordinates and degenerate paths`() {
        assertNull(planRoadSegments(listOf(a, b), RouteTravelMode.WALKING) { _, end, _ -> listOf(RoutePoint(Double.NaN, 120.0), end) })
        assertNull(planRoadSegments(listOf(a, b), RouteTravelMode.WALKING) { start, _, _ -> listOf(start, start) })
        assertNull(planRoadSegments(listOf(a, a), RouteTravelMode.DRIVING) { _, _, _ -> error("unexpected request") })
        assertNull(planRoadSegments(listOf(a, RoutePoint(91.0, 0.0)), RouteTravelMode.DRIVING) { _, _, _ -> error("unexpected request") })
    }

    @Test fun `rejects badly snapped endpoints and disconnected segments`() {
        val closeToB1 = RoutePoint(b.lat + 0.001, b.lng)
        val closeToB2 = RoutePoint(b.lat - 0.001, b.lng)
        assertNull(planRoadSegments(listOf(a, b, c), RouteTravelMode.CYCLING) { start, end, _ ->
            if (start == a) listOf(start, closeToB1) else listOf(closeToB2, end)
        })
        assertNull(planRoadSegments(listOf(a, b), RouteTravelMode.CYCLING) { start, _, _ -> listOf(start, c) })
    }
}
