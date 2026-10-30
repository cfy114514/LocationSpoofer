package com.vincenthzr.locationspoofer.ui.screen.tabs.route

/** Apply GPS fix spacing and jump rules to the last accepted point. */
internal enum class GpsSampleDecision { ACCEPT, GAP, REJECT }
internal fun evaluateGpsSample(accuracyM: Float, distanceM: Float, elapsedSeconds: Double): GpsSampleDecision {
    if (!accuracyM.isFinite() || accuracyM <= 0 || accuracyM > 35 ||
        !distanceM.isFinite() || distanceM < 0 || !elapsedSeconds.isFinite() || elapsedSeconds <= 0) return GpsSampleDecision.REJECT
    if (distanceM < maxOf(3f, minOf(accuracyM, 10f) * .5f)) return GpsSampleDecision.REJECT
    if (distanceM > maxOf(30.0, elapsedSeconds * 80.0)) return GpsSampleDecision.REJECT
    return if (distanceM > 30 && elapsedSeconds > 15) GpsSampleDecision.GAP else GpsSampleDecision.ACCEPT
}
