package com.vincenthzr.locationspoofer.data.state

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class RecordingSimulationCoordinatorTest {
    @Test fun withoutRecorderContinues() = runBlocking {
        RecordingSimulationCoordinator().beforeSimulation()
    }

    @Test fun publicationWaitsForRecorderAndPropagatesFailures() = runBlocking {
        val coordinator = RecordingSimulationCoordinator()
        val stopStarted = CompletableDeferred<Unit>()
        val releaseStop = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        coordinator.register {
            events += "stop-start"
            stopStarted.complete(Unit)
            releaseStop.await()
            events += "stop-complete"
        }
        val start = async { coordinator.beforeSimulation(); events += "publish" }
        stopStarted.await()
        yield()
        assertFalse(start.isCompleted)
        assertEquals(listOf("stop-start"), events)
        releaseStop.complete(Unit)
        start.await()
        assertEquals(listOf("stop-start", "stop-complete", "publish"), events)

        coordinator.register { throw IllegalStateException("cannot pause") }
        try {
            coordinator.beforeSimulation()
            fail("failure must prevent publication")
        } catch (expected: IllegalStateException) {
            assertEquals("cannot pause", expected.message)
        }
    }
}
