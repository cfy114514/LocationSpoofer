package com.vincenthzr.locationspoofer.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoexistForceStopTest {
    @Test fun testBuildNeverForceStopsEitherInstallation() {
        val test = "com.vincenthzr.locationspoofer.cfytest"
        assertFalse(canForceStopPackage(test, "com.vincenthzr.locationspoofer"))
        assertFalse(canForceStopPackage(test, test))
        assertTrue(canForceStopPackage(test, "example.target"))
    }
    @Test fun originalBuildRetainsExistingBehavior() {
        assertTrue(canForceStopPackage("com.vincenthzr.locationspoofer", "example.target"))
    }
}
