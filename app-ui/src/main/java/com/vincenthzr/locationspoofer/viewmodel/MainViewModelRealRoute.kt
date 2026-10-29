package com.vincenthzr.locationspoofer.viewmodel

import androidx.lifecycle.viewModelScope
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import com.vincenthzr.locationspoofer.data.model.RoutePlanStage
import com.vincenthzr.locationspoofer.data.state.SpoofingState

internal fun MainViewModel.saveRealRoute(name: String, points: List<RoutePoint>, done: (Boolean) -> Unit) {
    viewModelScope.launch {
        try {
            locationRepository.insertSavedRoute(name, points)
            done(true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            done(false)
        }
    }
}

/** Load the recorded geometry for planning without starting or re-routing it. */
internal fun MainViewModel.useRealRoute(points: List<RoutePoint>): Boolean {
    val state = _uiState.value
    if (state.isSpoofingActive || SpoofingState.isActive || state.isFetchingRoute || state.routePlanStage == RoutePlanStage.RUNNING) return false
    _uiState.update { it.copy(routePoints = points, routePlanStage = RoutePlanStage.READY,
        selectedCollectionRouteId = null, useRealRoute = false) }
    return true
}
