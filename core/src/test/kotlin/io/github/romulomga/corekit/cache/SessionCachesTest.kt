package io.github.romulomga.corekit.cache

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** O fim de uma sessão esvazia todo cache guardado, e o fluxo de um repositório segue o cache dele. */
class SessionCachesTest {
    data class Snapshot(val value: Loadable<String, TestFailure> = Loadable())

    private class Repository(scope: kotlinx.coroutines.CoroutineScope) : SnapshotCache<Snapshot> {
        private val state = MutableStateFlow(Snapshot())

        val resource = KeyedResource(scope, state.single({ it.value }, { s, v -> s.copy(value = v) }), TestFailure::map) { _: SingleKey ->
            "value"
        }

        override val snapshot: StateFlow<Snapshot> = state

        override fun clear() = resource.clear()
    }

    @Test
    fun theEndOfTheSessionEmptiesEveryCacheKept() = runTest {
        val first = Repository(eagerBackground())
        val second = Repository(eagerBackground())
        val caches = SessionCaches(setOf(first, second))

        first.resource.refresh()
        second.resource.refresh()
        caches.clear()

        assertEquals(2, caches.count)
        assertEquals(Loadable<String, TestFailure>(), first.snapshot.value.value)
        assertEquals(Loadable<String, TestFailure>(), second.snapshot.value.value)
    }

    @Test
    fun theFlowFollowsTheCache() = runTest {
        val repository = Repository(eagerBackground())

        repository.resource.load()

        assertEquals(LoadStatus.Loaded, repository.values { it.value.status }.first { it == LoadStatus.Loaded })
    }

    @Test
    fun whatTheAppMapsToNullIsACancellationNotAFailure() = runTest {
        val server = FakeCall<Int, String> { throw TestFailure.AppCancelled }
        val resource = KeyedResource(
            eagerBackground(),
            MutableStateFlow(emptyMap<Int, Loadable<String, TestFailure>>()).whole(),
            TestFailure::map
        ) { server(it) }

        assertEquals(null, resource.refresh(1))
        assertEquals(LoadStatus.Idle, resource.entry(1).status)
    }
}
