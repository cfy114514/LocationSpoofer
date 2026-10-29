package com.vincenthzr.locationspoofer.ui.screen.tabs.route

import android.Manifest
import android.content.Context
import android.content.Intent
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
import com.vincenthzr.locationspoofer.data.state.SpoofingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/** GPS recorder shared with its location foreground service. */
internal class RealRouteRecorder private constructor(context: Context) : LocationListener {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val appContext = context.applicationContext
    var recording by mutableStateOf(false)
        private set
    var starting by mutableStateOf(false)
        private set
    val statistics = RealRouteStatistics()
    private val draft = RealRouteDraft(context.applicationContext)
    private val writes = Channel<Pair<Long, RealRouteDraftData>>(Channel.CONFLATED)
    private val revision = AtomicLong(0)
    var draftPending by mutableStateOf(false)
        private set
    private val draftScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var storageError by mutableStateOf(false)
        private set
    var unreadableDraft by mutableStateOf(false)
        private set
    private val restoredDraft = try { draft.load() } catch (_: Exception) { unreadableDraft = true; storageError = true; RealRouteDraftData(emptyList()) }
    var points by mutableStateOf(restoredDraft.points)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    init {
        draftScope.launch {
            for ((version, snapshot) in writes) {
                if (version != revision.get()) continue
                val failed = try { draft.save(snapshot); false } catch (_: Exception) { true }
                withContext(Dispatchers.Main) {
                    if (failed && (revision.get() == version || recording || starting)) {
                        val wasRecording = recording || starting
                        // Ignore any older queued snapshot; retry must persist the latest points.
                        revision.incrementAndGet()
                        storageError = true
                        draftPending = false
                        if (wasRecording || snapshot.gapAnchor != null) stopWithReason("storage")
                    } else if (!failed && revision.get() == version) {
                        storageError = false
                        draftPending = false
                        if (error == "storage") error = null
                        if (snapshot.points.isEmpty()) unreadableDraft = false
                    }
                }
            }
        }
    }
    private fun persist() {
        draftPending = true
        writes.trySend(revision.incrementAndGet() to RealRouteDraftData(points, gapAnchor))
    }
    fun retryDraftSave() { if (!unreadableDraft) persist() }
    fun retryDiscardDraft() { if (points.isEmpty()) persist() }
    private var last: Location? = null
    private var gapAnchor: Pair<Double, Double>? = restoredDraft.gapAnchor
    var hasGap by mutableStateOf(restoredDraft.gapAnchor != null)
        private set
    val atLimit: Boolean get() = points.size >= MAX_POINTS
    fun acknowledgeGap() { hasGap = false }


    fun start(): Boolean {
        if (SpoofingState.isActive) { error = "spoofing"; return false }
        if (unreadableDraft) { storageError = true; return false }
        if (storageError || draftPending) return false
        if (atLimit) { error = "limit"; return false }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            error = "permission"
            return false
        }
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            error = "gps"
            return false
        }
        if (recording || starting) return true
        return try {
            starting = true
            ContextCompat.startForegroundService(appContext, Intent(appContext, RealRouteRecordingService::class.java))
            true
        } catch (_: RuntimeException) {
            starting = false
            error = "service"
            false
        }
    }

    /** Called by the service only after it has entered the foreground. */
    fun startGps(): Boolean {
        if (SpoofingState.isActive) { starting = false; error = "spoofing"; return false }
        if (!starting) return false
        return try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            starting = false
            recording = true
            statistics.start(SystemClock.elapsedRealtime())
            error = null
            true
        } catch (_: SecurityException) {
            starting = false; error = "permission"; false
        } catch (_: IllegalArgumentException) {
            starting = false; error = "gps"; false
        }
    }

    fun serviceFailed() { starting = false; error = "service" }
    fun serviceStopped() {
        try { manager.removeUpdates(this) } catch (_: SecurityException) { }
        statistics.pause(SystemClock.elapsedRealtime())
        starting = false
        recording = false
        last = null
    }
    fun pause() {
        serviceStopped()
        appContext.stopService(Intent(appContext, RealRouteRecordingService::class.java))
    }

    private fun stopWithReason(reason: String) {
        pause()
        error = reason
        RealRouteRecordingService.notifyStopped(appContext, reason)
    }

    fun pauseForSimulation() {
        if (recording || starting) stopWithReason("spoofing")
    }

    fun clear() { pause(); statistics.reset(); points = emptyList(); error = null; hasGap = false; gapAnchor = null; persist() }

    override fun onLocationChanged(location: Location) {
        if (SpoofingState.isActive) { pauseForSimulation(); return }
        if (!recording || !location.hasAccuracy() || !location.accuracy.isFinite() || location.accuracy > 35f || location.accuracy <= 0f) return
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return
        if (android.os.Build.VERSION.SDK_INT >= 31 && location.isMock) return
        @Suppress("DEPRECATION")
        if (android.os.Build.VERSION.SDK_INT < 31 && location.isFromMockProvider) return
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        if (ageMs !in 0..10_000) return
        gapAnchor?.let { anchor ->
            val distance = FloatArray(1)
            Location.distanceBetween(anchor.first, anchor.second, location.latitude, location.longitude, distance)
            if (distance[0] > 30f) {
                hasGap = true
                stopWithReason("gap")
                return
            }
            gapAnchor = null
            hasGap = false
        }
        val previous = last
        if (previous != null) {
            val dt = (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000.0
            when (evaluateGpsSample(location.accuracy, previous.distanceTo(location), dt)) {
                GpsSampleDecision.REJECT -> return
                GpsSampleDecision.GAP -> {
                    gapAnchor = previous.latitude to previous.longitude
                    hasGap = true
                    persist()
                    stopWithReason("gap")
                    return
                }
                GpsSampleDecision.ACCEPT -> Unit
            }
        }
        last = Location(location)
        points = points + realGpsRoutePoint(location.latitude, location.longitude)
        persist()
        RealRouteRecordingService.update(appContext, points.size)
        if (atLimit) stopWithReason("limit")
    }
    companion object {
        const val MAX_POINTS = 10_000
        @Volatile private var instance: RealRouteRecorder? = null
        fun getInstance(context: Context): RealRouteRecorder = instance ?: synchronized(this) {
            instance ?: RealRouteRecorder(context.applicationContext).also { instance = it }
        }
    }
    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER && recording) stopWithReason("gps")
    }

    @Deprecated("Deprecated in Android") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
}
