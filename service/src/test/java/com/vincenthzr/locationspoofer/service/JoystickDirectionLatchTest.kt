package com.vincenthzr.locationspoofer.service

import org.junit.Assert.*
import org.junit.Test

class JoystickDirectionLatchTest {
    @Test fun requiresIntensityAndTime() {
        val latch = JoystickDirectionLatch()
        latch.update(0.0, 0.49f, 0)
        assertFalse(latch.ready(3000))
        latch.update(0.0, 0.5f, 3000)
        assertFalse(latch.ready(4499))
        assertTrue(latch.ready(4500))
    }
    @Test fun angularWrapAndDirectionChange() {
        val latch = JoystickDirectionLatch()
        latch.update(355.0, 1f, 0)
        latch.update(5.0, 1f, 800)
        assertTrue(latch.ready(1500))
        latch.update(20.0, 1f, 1501)
        assertFalse(latch.ready(3000))
        assertTrue(latch.ready(3001))
    }
    @Test fun weakInputClockRollbackAndResetStopTheLatch() {
        val latch = JoystickDirectionLatch()
        latch.update(90.0, 1f, 100)
        latch.update(90.0, 0.1f, 110)
        assertFalse(latch.ready(2000))
        latch.update(90.0, 1f, 2000)
        assertFalse(latch.ready(1999))
        assertFalse(latch.ready(4000))
        latch.update(Double.NaN, 1f, 4000)
        assertFalse(latch.ready(6000))
        latch.update(1.0, 1f, 7000)
        latch.reset()
        assertFalse(latch.ready(9000))
    }
}
