package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import com.vincenthzr.locationspoofer.data.model.RoutePoint
import org.junit.Assert.*
import org.junit.Test

class RealRouteDraftCodecTest {
    @Test fun preservesPointsInOrder() {
        val points = listOf(RoutePoint(31.2, 121.4), RoutePoint(-10.0, -179.0))
        assertEquals(points, decodeDraft(encodeDraft(points)))
    }
    @Test fun restoresGapAnchorAndReadsLegacyDrafts() {
        val points = listOf(RoutePoint(31.2, 121.4))
        val draft = RealRouteDraftData(points, 31.19 to 121.39)
        assertEquals(draft, decodeDraftData(encodeDraftData(draft)))
        assertEquals(RealRouteDraftData(points), decodeDraftData("real-route-v1\n31.2,121.4\n"))
    }
    @Test fun rejectsInvalidGapAnchors() {
        for (data in listOf("real-route-v2\ngap,NaN,1\n1,2\n", "real-route-v2\ngap,1,2\n", "real-route-v2\ninvalid\n1,2\n")) {
            assertThrows(IllegalArgumentException::class.java) { decodeDraftData(data) }
        }
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
