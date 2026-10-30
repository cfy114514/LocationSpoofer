package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import com.vincenthzr.locationspoofer.data.model.RoutePoint

/** Points use the app route coordinate system; the optional gap anchor is raw WGS-84 GPS. */
internal data class RealRouteDraftData(val points: List<RoutePoint>, val gapAnchor: Pair<Double, Double>? = null)

internal fun encodeDraft(points: List<RoutePoint>): String = encodeDraftData(RealRouteDraftData(points))

internal fun encodeDraftData(draft: RealRouteDraftData): String = buildString {
    append("real-route-v2\n")
    val anchor = draft.gapAnchor
    if (anchor == null) append("gap,none\n")
    else append("gap,").append(anchor.first).append(',').append(anchor.second).append('\n')
    draft.points.forEach { append(it.lat).append(',').append(it.lng).append('\n') }
}

internal fun decodeDraft(data: String): List<RoutePoint> = decodeDraftData(data).points

internal fun decodeDraftData(data: String): RealRouteDraftData {
    val lines = data.lineSequence().toList()
    val v2 = lines.firstOrNull() == "real-route-v2"
    require(v2 || lines.firstOrNull() == "real-route-v1")
    fun coordinate(fields: List<String>): Pair<Double, Double> {
        require(fields.size == 2)
        val lat = fields[0].toDouble()
        val lng = fields[1].toDouble()
        require(lat.isFinite() && lat in -90.0..90.0 && lng.isFinite() && lng in -180.0..180.0)
        return lat to lng
    }
    val anchor = if (v2) {
        val fields = lines.getOrNull(1)?.split(',') ?: throw IllegalArgumentException("Missing gap state")
        require(fields.firstOrNull() == "gap")
        if (fields == listOf("gap", "none")) null else coordinate(fields.drop(1))
    } else null
    val rows = lines.drop(if (v2) 2 else 1).filter { it.isNotEmpty() }
    require(rows.size <= 10_000)
    val points = rows.map { row -> coordinate(row.split(',')).let { RoutePoint(it.first, it.second) } }
    require(anchor == null || points.isNotEmpty())
    return RealRouteDraftData(points, anchor)
}
