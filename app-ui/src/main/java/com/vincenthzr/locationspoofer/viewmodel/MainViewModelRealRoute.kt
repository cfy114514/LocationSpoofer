package com.vincenthzr.locationspoofer.viewmodel

import androidx.lifecycle.viewModelScope
import com.vincenthzr.locationspoofer.data.model.RoutePoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

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
