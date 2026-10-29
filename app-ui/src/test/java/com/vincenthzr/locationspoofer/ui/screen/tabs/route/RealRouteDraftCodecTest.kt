package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import com.vincenthzr.locationspoofer.data.model.RoutePoint
import org.junit.Assert.*
import org.junit.Test

class RealRouteDraftCodecTest {
    @Test fun preservesPointsInOrder() {
        val points = listOf(RoutePoint(31.2, 121.4), RoutePoint(-10.0, -179.0))
        assertEquals(points, decodeDraft(encodeDraft(points)))
    }
    @Test fun rejectsCorruptOrInvalidData() {
        for (data in listOf("bad", "real-route-v1\n91,0\n", "real-route-v1\nNaN,2\n", "real-route-v1\n1,2,3\n")) {
            assertThrows(IllegalArgumentException::class.java) { decodeDraft(data) }
        }
    }
    @Test fun rejectsOversizedDraft() {
        val data = "real-route-v1\n" + "1,2\n".repeat(10_001)
        assertThrows(IllegalArgumentException::class.java) { decodeDraft(data) }
    }
}
