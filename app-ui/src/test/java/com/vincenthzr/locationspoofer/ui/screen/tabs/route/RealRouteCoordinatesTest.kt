package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealRouteCoordinatesTest {
    @Test fun `GPS samples use the saved route coordinate convention`() {
        val beijing = realGpsRoutePoint(39.908823, 116.397470)
        assertTrue(kotlin.math.abs(beijing.lat - 39.908823) > 0.0001)
        assertTrue(kotlin.math.abs(beijing.lng - 116.397470) > 0.0001)
        val newYork = realGpsRoutePoint(40.7128, -74.0060)
        assertEquals(40.7128, newYork.lat, 0.0000001)
        assertEquals(-74.0060, newYork.lng, 0.0000001)
    }
}
