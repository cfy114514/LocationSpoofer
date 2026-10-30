package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng
import com.vincenthzr.locationspoofer.utils.GeoMath

/** Counts active recording time within this process. Uses a monotonic clock supplied by the caller. */
internal class RealRouteStatistics {
    private var elapsedMs = 0L
    private var startedAtMs: Long? = null

    fun start(nowMs: Long) { if (startedAtMs == null) startedAtMs = nowMs }
    fun pause(nowMs: Long) {
        startedAtMs?.let { elapsedMs += (nowMs - it).coerceAtLeast(0L) }
        startedAtMs = null
    }
    fun reset() { elapsedMs = 0L; startedAtMs = null }
    fun elapsed(nowMs: Long): Long = elapsedMs + (startedAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L)
}

/** Distance of the saved polyline, including straight connections across pauses. */
internal fun realRouteDistanceMeters(points: List<RoutePoint>): Double = points.zipWithNext { a, b ->
    GeoMath.distance(LatLng(a.lat, a.lng), LatLng(b.lat, b.lng))
}.sum()

internal fun formatRecordingDuration(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    return "%d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
}
