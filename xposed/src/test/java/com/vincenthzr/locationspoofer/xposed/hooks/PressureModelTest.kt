package com.vincenthzr.locationspoofer.xposed.hooks

import org.junit.Assert.*
import org.junit.Test

class PressureModelTest {
    @Test fun pressureFollowsAltitude() {
        assertEquals(1013.25f, PressureModel.pressureHpa(0.0, 0)!!, 0.01f)
        assertTrue(PressureModel.pressureHpa(1000.0, 0)!! < PressureModel.pressureHpa(0.0, 0)!!)
    }
    @Test fun invalidAltitudeLeavesPhysicalReadingUntouched() {
        assertNull(PressureModel.pressureHpa(Double.NaN, 0))
        assertNull(PressureModel.pressureHpa(11000.0, 0))
    }
    @Test fun fluctuationIsSmall() {
        assertTrue(kotlin.math.abs(PressureModel.pressureHpa(0.0, 0)!! - PressureModel.pressureHpa(0.0, 188496)!!) < 0.06f)
    }
}
