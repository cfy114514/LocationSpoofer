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
    @Test fun oneGestureCanRelockAfterReturningToCenter() {
        val latch = JoystickGestureLatch()
        latch.update(0.0, 1f, 0)
        assertFalse(latch.tick(1499))
        assertTrue(latch.tick(1500))
        assertTrue(latch.locked)
        assertFalse(latch.tick(1550))
        latch.update(0.0, 0f, 1600)
        assertFalse(latch.locked)
        latch.update(90.0, 0.8f, 1650)
        assertFalse(latch.tick(3149))
        assertTrue(latch.tick(3150))
        assertTrue(latch.locked)
        latch.reset()
        assertFalse(latch.locked)
        assertFalse(latch.tick(5000))
    }
}
