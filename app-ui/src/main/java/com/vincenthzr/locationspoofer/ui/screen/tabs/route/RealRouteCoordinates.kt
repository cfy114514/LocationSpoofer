package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.utils.CoordinateUtils

/** Saved routes use GCJ-02 internally, while GPS reports WGS-84. */
internal fun realGpsRoutePoint(latitude: Double, longitude: Double): RoutePoint {
    val converted = CoordinateUtils.wgs84ToGcj02(latitude, longitude)
    return RoutePoint(converted.lat, converted.lng)
}
