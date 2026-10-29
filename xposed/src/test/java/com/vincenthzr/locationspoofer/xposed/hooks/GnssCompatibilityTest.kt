package com.vincenthzr.locationspoofer.xposed.hooks

import org.junit.Assert.*
import org.junit.Test

class GnssCompatibilityTest {
    @Test fun legacyPrnsFollowAndroidConstellationMapping() {
        assertEquals(1, legacySatellitePrn(1, 1))
        assertEquals(201, legacySatellitePrn(5, 1))
        assertEquals(65, legacySatellitePrn(3, 1))
        assertEquals(33, legacySatellitePrn(2, 120))
        assertEquals(193, legacySatellitePrn(4, 193))
        assertNull(legacySatellitePrn(6, 1)) // Galileo is not represented in GpsStatus.
        assertNull(legacySatellitePrn(5, 56)) // Out of the old interface's 1..255 range.
        assertNull(legacySatellitePrn(0, 1))
    }

    @Test fun supportedConstellationsWithSameSvidRemainDistinct() {
        val ids = listOf(legacySatellitePrn(1, 1), legacySatellitePrn(3, 1), legacySatellitePrn(5, 1))
        assertEquals(3, ids.filterNotNull().toSet().size)
    }

    @Test fun snapshotCacheRespectsJitterCountExpiryAndClockRollback() {
        val cache = GnssSnapshotCache<Any>()
        val first = Any()
        cache.put(20, true, 1000, first)
        assertSame(first, cache.get(20, true, 1999))
        assertNull(cache.get(20, false, 1001))
        assertNull(cache.get(21, true, 1001))
        assertNull(cache.get(20, true, 2000))
        assertNull(cache.get(20, true, 999))
        val second = Any()
        cache.put(20, false, 1001, second)
        assertSame(second, cache.get(20, false, 1001))
        assertNull(cache.get(20, true, 1001))
    }
}
