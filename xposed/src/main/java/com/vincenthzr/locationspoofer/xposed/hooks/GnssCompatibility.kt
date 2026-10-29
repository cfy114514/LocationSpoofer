package com.vincenthzr.locationspoofer.xposed.hooks

/** Mapping used by AOSP GpsStatus.setStatus; Galileo has no legacy mapping.
 * https://android.googlesource.com/platform/frameworks/base/+/bb83b89/location/java/android/location/GpsStatus.java
 */
internal fun legacySatellitePrn(constellation: Int, svid: Int): Int? {
    val prn = when (constellation) {
        1, 4 -> svid.toLong() // GPS, QZSS
        2 -> svid.toLong() - 87 // SBAS
        3 -> svid.toLong() + 64 // GLONASS
        5 -> svid.toLong() + 200 // BeiDou
        else -> return null
    }
    return prn.takeIf { it in 1L..255L }?.toInt()
}

/** One immutable entry avoids publishing a status separately from its cache key. */
internal class GnssSnapshotCache<T : Any> {
    private data class Entry<T>(val count: Int, val jitter: Boolean, val timeMs: Long, val value: T)
    @Volatile private var entry: Entry<T>? = null

    fun get(count: Int, jitter: Boolean, nowMs: Long): T? {
        val current = entry ?: return null
        val age = nowMs - current.timeMs
        return current.value.takeIf { current.count == count && current.jitter == jitter && age in 0..999 }
    }

    fun put(count: Int, jitter: Boolean, nowMs: Long, value: T) {
        entry = Entry(count, jitter, nowMs, value)
    }
}
