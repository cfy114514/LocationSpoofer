package com.vincenthzr.locationspoofer.service

/** Tracks a stable, deliberate joystick direction using monotonic milliseconds. */
internal class JoystickDirectionLatch {
    private var since: Long? = null
    private var lastTime: Long? = null
    private var bearing = 0.0

    fun reset() { since = null; lastTime = null }

    fun update(direction: Double, intensity: Float, now: Long) {
        if (!direction.isFinite() || !intensity.isFinite() || intensity < 0.5f) { reset(); return }
        val normalized = ((direction % 360.0) + 360.0) % 360.0
        val difference = kotlin.math.abs(((normalized - bearing + 540.0) % 360.0) - 180.0)
        if (since == null || (lastTime != null && now < lastTime!!) || difference > 12.0) {
            since = now
            bearing = normalized
        }
        lastTime = now
    }

    fun ready(now: Long): Boolean {
        val start = since ?: return false
        if (now < (lastTime ?: start)) { reset(); return false }
        lastTime = now
        return now - start >= 1_500
    }
}

/** One drag may lock, return to center, then lock again without lifting the finger. */
internal class JoystickGestureLatch {
    private val direction = JoystickDirectionLatch()
    var locked = false
        private set

    fun update(bearing: Double, intensity: Float, now: Long) {
        if (locked && intensity == 0f) {
            locked = false
            direction.reset()
        }
        if (!locked) direction.update(bearing, intensity, now)
    }

    fun tick(now: Long): Boolean {
        if (locked || !direction.ready(now)) return false
        locked = true
        return true
    }

    fun reset() { locked = false; direction.reset() }
}
