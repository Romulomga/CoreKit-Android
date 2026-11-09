package io.github.romulomga.corekit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SingleFlightTest {
    @Test
    fun secondTapWhileSavingIsIgnored() = runTest {
        val flight = SingleFlight()
        val gate = CompletableDeferred<Unit>()
        var saves = 0

        assertTrue(
            flight.launch(this) {
                saves += 1
                gate.await()
            }
        )
        assertFalse(flight.launch(this) { saves += 1 })
        assertTrue(flight.isRunning.value)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, saves)
        assertFalse(flight.isRunning.value)
    }

    @Test
    fun opensAgainAfterTheSaveEnds() = runTest {
        val flight = SingleFlight()
        var saves = 0

        flight.launch(this) { saves += 1 }
        advanceUntilIdle()
        flight.launch(this) { saves += 1 }
        advanceUntilIdle()

        assertEquals(2, saves)
    }

    @Test
    fun opensAgainWhenTheSaveFails() = runTest {
        val flight = SingleFlight()
        val failures = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, failure -> failures += failure }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job() + handler)

        flight.launch(scope) { error("disk full") }
        advanceUntilIdle()

        assertEquals(1, failures.size)
        assertFalse(flight.isRunning.value)
    }

    @Test
    fun opensAgainWhenTheScopeIsCancelledBeforeTheSaveStarts() = runTest {
        val flight = SingleFlight()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())

        flight.launch(scope) { }
        scope.cancel()
        advanceUntilIdle()

        assertFalse(flight.isRunning.value)
    }

    @Test
    fun runReturnsNullForTheIgnoredCall() = runTest {
        val flight = SingleFlight()
        val gate = CompletableDeferred<Unit>()

        flight.launch(this) { gate.await() }

        assertNull(flight.run { "saved" })

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("saved", flight.run { "saved" })
    }

    @Test
    fun keyedFlightLocksOnlyTheSameKey() = runTest {
        val flight = KeyedSingleFlight<String>()
        val gate = CompletableDeferred<Unit>()
        val saved = mutableListOf<String>()

        assertTrue(
            flight.launch(this, "a") {
                saved += "a"
                gate.await()
            }
        )
        assertFalse(flight.launch(this, "a") { saved += "a" })
        assertTrue(flight.launch(this, "b") { saved += "b" })
        advanceUntilIdle()

        assertEquals(setOf("a"), flight.running.value)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), saved)
        assertTrue(flight.running.value.isEmpty())
    }
}
