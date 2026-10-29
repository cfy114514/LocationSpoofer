package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vincenthzr.locationspoofer.data.model.RoutePoint

/** Foreground-only GPS recording. The caller owns and closes this listener. */
internal class RealRouteRecorder(context: Context) : LocationListener {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val appContext = context.applicationContext
    var recording by mutableStateOf(false)
        private set
    var points by mutableStateOf<List<RoutePoint>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var last: Location? = null

    fun start(): Boolean {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            error = "permission"
            return false
        }
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            error = "gps"
            return false
        }
        return try {
            // Do not use cached locations: only fixes delivered after starting the session.
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            recording = true
            error = null
            true
        } catch (_: SecurityException) {
            error = "permission"
            false
        } catch (_: IllegalArgumentException) {
            error = "gps"
            false
        }
    }

    fun pause() {
        try { manager.removeUpdates(this) } catch (_: SecurityException) { }
        recording = false
        // Avoid treating time spent paused as a normal sampling interval.
        last = null
    }

    fun clear() { pause(); points = emptyList(); error = null }

    override fun onLocationChanged(location: Location) {
        if (!recording || !location.hasAccuracy() || location.accuracy > 30f || location.accuracy <= 0f) return
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return
        if (android.os.Build.VERSION.SDK_INT >= 31 && location.isMock) return
        @Suppress("DEPRECATION")
        if (android.os.Build.VERSION.SDK_INT < 31 && location.isFromMockProvider) return
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        if (ageMs !in 0..10_000) return
        val previous = last
        if (previous != null) {
            val dt = (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000.0
            if (dt <= 0) return
            val distance = previous.distanceTo(location)
            if (distance < maxOf(3f, location.accuracy / 2f)) return
            if (distance / dt > 100) return
        }
        last = Location(location)
        points = points + realGpsRoutePoint(location.latitude, location.longitude)
    }
    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER && recording) { pause(); error = "gps" }
    }

    @Deprecated("Deprecated in Android") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
}
