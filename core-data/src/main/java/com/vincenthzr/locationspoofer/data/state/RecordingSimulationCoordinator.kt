package com.vincenthzr.locationspoofer.data.state

/** Process-local bridge: the UI recorder registers its suspendable stop operation here. */
class RecordingSimulationCoordinator {
    @Volatile private var stopRecorder: (suspend () -> Unit)? = null

    fun register(stop: suspend () -> Unit) { stopRecorder = stop }

    /** Returns only after the registered recorder has stopped; errors prevent publication. */
    suspend fun beforeSimulation() { stopRecorder?.invoke() }
}

val recordingSimulationCoordinator = RecordingSimulationCoordinator()
