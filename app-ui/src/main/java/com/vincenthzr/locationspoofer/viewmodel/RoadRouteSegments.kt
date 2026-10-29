package com.vincenthzr.locationspoofer.viewmodel

import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.data.model.RouteTravelMode
import com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng
import com.vincenthzr.locationspoofer.utils.GeoMath

// Road providers can snap to a nearby road, but must not connect unrelated segments.
internal const val MAX_ROAD_SNAP_METERS = 150.0
private const val MIN_ROAD_SEGMENT_METERS = 0.1
private fun validPoint(p: RoutePoint) = p.lat.isFinite() && p.lng.isFinite() &&
    p.lat in -90.0..90.0 && p.lng in -180.0..180.0 && p.waitSec.isFinite() && p.waitSec >= 0.0
private fun distance(a: RoutePoint, b: RoutePoint): Double =
    GeoMath.distance(LatLng(a.lat, a.lng), LatLng(b.lat, b.lng))
private fun near(a: RoutePoint, b: RoutePoint): Boolean =
    distance(a, b).let { it.isFinite() && it <= MAX_ROAD_SNAP_METERS }

internal fun validRouteWaypoints(waypoints: List<RoutePoint>): Boolean =
    waypoints.size >= 2 && waypoints.all { validPoint(it) } &&
        waypoints.zipWithNext().all { (a, b) ->
            distance(a, b).let { it.isFinite() && it >= MIN_ROAD_SEGMENT_METERS }
        }

/** Return null unless every pair has a valid and connected road segment. */
internal fun planRoadSegments(
    waypoints: List<RoutePoint>,
    mode: RouteTravelMode,
    request: (RoutePoint, RoutePoint, RouteTravelMode) -> List<RoutePoint>?
): List<RoutePoint>? {
    if (!validRouteWaypoints(waypoints)) return null
    val output = mutableListOf<RoutePoint>()
    for ((start, end) in waypoints.zipWithNext()) {
        val segment = request(start, end, mode)?.takeIf { it.size >= 2 } ?: return null
        if (segment.any { !validPoint(it) } || !near(start, segment.first()) || !near(end, segment.last())) return null
        if (segment.zipWithNext().sumOf { (a, b) -> distance(a, b) }.let { !it.isFinite() || it < MIN_ROAD_SEGMENT_METERS }) return null
        if (output.isNotEmpty() && !near(output.last(), segment.first())) return null
        if (output.isNotEmpty() && output.last().lat == segment.first().lat && output.last().lng == segment.first().lng) {
            output.addAll(segment.drop(1))
        } else output.addAll(segment)
    }
    return output.takeIf { it.size >= 2 }
}
