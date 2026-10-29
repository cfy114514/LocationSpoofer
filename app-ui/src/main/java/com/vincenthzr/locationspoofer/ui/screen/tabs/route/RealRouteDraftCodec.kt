package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import com.vincenthzr.locationspoofer.data.model.RoutePoint

internal fun encodeDraft(points: List<RoutePoint>): String = buildString {
    append("real-route-v1\n")
    points.forEach { append(it.lat).append(',').append(it.lng).append('\n') }
}

internal fun decodeDraft(data: String): List<RoutePoint> {
    val lines = data.lineSequence().toList()
    require(lines.firstOrNull() == "real-route-v1")
    val rows = lines.drop(1).filter { it.isNotEmpty() }
    require(rows.size <= 10_000)
    return rows.map { row ->
        val fields = row.split(',')
        require(fields.size == 2)
        val lat = fields[0].toDouble()
        val lng = fields[1].toDouble()
        require(lat.isFinite() && lat in -90.0..90.0 && lng.isFinite() && lng in -180.0..180.0)
        RoutePoint(lat, lng)
    }
}
