package com.vincenthzr.locationspoofer.data.model

/** Room-backed routes retain their stable identifier even when names or geometry match. */
data class SavedRoute(val name: String, val points: List<RoutePoint>, val id: Long? = null)
