package com.vincenthzr.locationspoofer.xposed.hooks

import kotlin.math.pow
import kotlin.math.sin

/** Standard atmosphere approximation in hPa, with a small slow variation. */
internal object PressureModel {
    fun pressureHpa(altitudeMeters: Double, timeMillis: Long): Float? {
        if (!altitudeMeters.isFinite() || altitudeMeters !in -500.0..10000.0) return null
        val pressure = 1013.25 * (1.0 - 2.25577e-5 * altitudeMeters).pow(5.25588)
        return (pressure + 0.05 * sin(timeMillis / 120000.0)).toFloat()
    }
}
