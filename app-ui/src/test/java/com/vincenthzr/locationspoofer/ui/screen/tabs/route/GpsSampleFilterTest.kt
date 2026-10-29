package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsSampleFilterTest {
    @Test fun accuracyAndSpacingBounds() {
        assertEquals(GpsSampleDecision.REJECT, evaluateGpsSample(35.1f, 10f, 2.0))
        assertEquals(GpsSampleDecision.REJECT, evaluateGpsSample(35f, 4.9f, 2.0))
        assertEquals(GpsSampleDecision.ACCEPT, evaluateGpsSample(35f, 5f, 2.0))
        assertEquals(GpsSampleDecision.REJECT, evaluateGpsSample(Float.NaN, 10f, 2.0))
    }
    @Test fun jumpAndGapRules() {
        assertEquals(GpsSampleDecision.REJECT, evaluateGpsSample(10f, 81f, 1.0))
        assertEquals(GpsSampleDecision.ACCEPT, evaluateGpsSample(10f, 80f, 1.0))
        assertEquals(GpsSampleDecision.GAP, evaluateGpsSample(10f, 31f, 16.0))
        assertEquals(GpsSampleDecision.ACCEPT, evaluateGpsSample(10f, 30f, 16.0))
        assertEquals(GpsSampleDecision.REJECT, evaluateGpsSample(10f, 10f, 0.0))
    }
}
