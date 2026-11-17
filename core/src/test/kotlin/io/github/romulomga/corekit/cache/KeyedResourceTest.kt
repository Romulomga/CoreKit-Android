package io.github.romulomga.corekit.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O bloco dos caches de um assunto online (gêmeo do `KeyedResourceTests` do CoreKit iOS, vindo do Homer): o que todo repositório sobre ele ganha de graça (uma
 * busca para quem chama ao mesmo tempo, uma busca que sobrevive à tela, o fim da sessão).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeyedResourceTest {
    private val server = FakeCall<Int, String> { "value $it" }

    private fun TestScope.resource(server: FakeCall<Int, String> = this@KeyedResourceTest.server): KeyedResource<Int, String, TestFailure> =
        KeyedResource(eagerBackground(), MutableStateFlow(emptyMap<Int, Loadable<String, TestFailure>>()).whole(), TestFailure::map) {
            server(it)
        }

    // Leituras

    /** "Voltar de um push não recarrega": a tela pede toda vez que aparece, o cache responde. */
    @Test
    fun loadingTwiceAsksTheServerOnce() = runTest {
        val resource = resource()

        resource.load(1)
        resource.load(1)
        resource.load(1)

        assertEquals(Loadable.loaded("value 1"), resource.entry(1))
        assertEquals(listOf(1), server.calls)
    }

    @Test
    fun callersAtTheSameTimeWaitForTheSameFetch() = runTest {
        val resource = resource()

        server.gate.hold()
        resource.load(1)

        val retrying = eagerBackground().async { resource.refresh(1) }

        assertEquals(LoadStatus.Loading, resource.entry(1).status)

        server.gate.release()
        advanceUntilIdle()

        assertNull(retrying.await())
        assertEquals(listOf(1), server.calls)
    }

    /** Sair da tela para a leitura dela, nunca a busca, e nada fica meio carregado. */
    @Test
    fun aFetchOutlivesTheScreenThatAskedForIt() = runTest {
        val resource = resource()
        val screen = CoroutineScope(eagerBackground().coroutineContext + Job())

        server.gate.hold()
        screen.launch { resource.refresh(1) }
        screen.cancel()
        server.gate.release()
        advanceUntilIdle()

        assertEquals("a busca é do cache, não de quem foi cancelado", Loadable.loaded("value 1"), resource.entry(1))
    }

    /**
     * Uma falha sem nada na tela é o erro da tela; a próxima abertura (ou volta) pede de novo, como toda abertura sempre
     * pediu, e o "Tentar de novo" também.
     */
    @Test
    fun aFailureWithNothingShownFailsAndTheNextOpeningOrARetryLoads() = runTest {
        val resource = resource()

        server.fails(TestFailure.Offline)
        resource.load(1)

        assertEquals(LoadStatus.Failed(TestFailure.Offline), resource.entry(1).status)

        resource.load(1)

        assertEquals(LoadStatus.Failed(TestFailure.Offline), resource.entry(1).status)
        assertEquals("a próxima abertura pede de novo", 2, server.count)

        server.answers { "value $it" }

        assertNull(resource.refresh(1))
        assertEquals(Loadable.loaded("value 1"), resource.entry(1))

        resource.load(1)

        assertEquals("um valor carregado fica como está", 3, server.count)
    }

    @Test
    fun aFailedRefreshKeepsTheValueOnScreen() = runTest {
        val resource = resource()

        resource.refresh(1)
        server.fails(TestFailure.Server)

        assertEquals(TestFailure.Server, resource.refresh(1))
        assertEquals(Loadable.loaded("value 1"), resource.entry(1))
    }

    /** Uma carga cortada no meio volta ao que era, nunca "carregada" pela metade, e a próxima tela pede de novo. */
    @Test
    fun aLoadCutShortGoesBackToIdle() = runTest {
        val scope = CoroutineScope(eagerBackground().coroutineContext + Job())
        val resource =
            KeyedResource(scope, MutableStateFlow(emptyMap<Int, Loadable<String, TestFailure>>()).whole(), TestFailure::map) { server(it) }

        server.gate.hold()
        resource.load(1)
        scope.cancel()

        assertEquals(LoadStatus.Idle, resource.entry(1).status)
    }

    /** Um nulo carregado ("sem resposta") é um valor: sobrevive a um refresh que falha e a um invalidate sem esqueleto. */
    @Test
    fun aLoadedNullIsAValue() = runTest {
        val answers = FakeCall<Int, String?>(null)
        val resource =
            KeyedResource(eagerBackground(), MutableStateFlow(emptyMap<Int, Loadable<String?, TestFailure>>()).whole(), TestFailure::map) {
                answers(it)
            }

        resource.load(1)

        assertEquals(Loadable<String?, TestFailure>(null, hasValue = true, status = LoadStatus.Loaded), resource.entry(1))

        answers.fails(TestFailure.Offline)

        assertEquals(TestFailure.Offline, resource.refresh(1))
        assertEquals(Loadable<String?, TestFailure>(null, hasValue = true, status = LoadStatus.Loaded), resource.entry(1))

        answers.gate.hold()
        resource.invalidate(1)

        assertEquals("lido de novo sem o esqueleto", LoadStatus.Loaded, resource.entry(1).status)
        assertTrue(resource.entry(1).hasValue)
    }

    // Gravações

    /** Uma gravação que o servidor confirmou ganha de uma leitura que saiu antes dela. */
    @Test
    fun aWriteDropsTheReadThatLeftBeforeIt() = runTest {
        val resource = resource()

        server.returns("old")
        resource.refresh(1)
        server.gate.hold()

        val refreshing = eagerBackground().async { resource.refresh(1) }

        resource.set(1, "written")
        server.gate.release()
        refreshing.await()

        assertEquals("written", resource.entry(1).value)
    }

    @Test
    fun anInvalidatedValueIsReadAgainAndOneNeverShownWaits() = runTest {
        val resource = resource()

        resource.refresh(1)
        resource.invalidate(1)
        resource.invalidate(2)

        assertEquals(listOf(1, 1), server.calls)
        assertEquals(LoadStatus.Idle, resource.entry(2).status)
    }

    @Test
    fun anUpdateChangesOnlyAValueAlreadyHere() = runTest {
        val resource = resource()

        resource.refresh(1)
        server.fails(TestFailure.Offline)
        resource.load(3)
        resource.update(1) { "$it!" }
        resource.update(2) { "never" }
        resource.update(3) { "never" }

        assertEquals("value 1!", resource.entry(1).value)
        assertEquals(Loadable<String, TestFailure>(), resource.entry(2))
        assertEquals(
            "um valor que nunca veio não é inventado",
            Loadable<String, TestFailure>(status = LoadStatus.Failed(TestFailure.Offline)),
            resource.entry(3)
        )
    }

    // Fim da sessão

    @Test
    fun theEndOfTheSessionEmptiesTheCacheAndDropsAnswersOnTheirWay() = runTest {
        val resource = resource()

        resource.refresh(1)
        server.gate.hold()

        val refreshing = eagerBackground().async { resource.refresh(2) }

        resource.clear()

        assertTrue(resource.entries.isEmpty())

        server.gate.release()
        refreshing.await()

        assertTrue("a resposta do usuário anterior nunca chega", resource.entries.isEmpty())
    }

    /** Vários blocos no mesmo snapshot: cada um escreve o seu pedaço na hora, e o valor do repositório nunca fica atrás. */
    @Test
    fun aSliceWritesIntoTheRepositorySnapshotAtOnce() = runTest {
        data class Snapshot(val name: Loadable<String, TestFailure> = Loadable(), val other: Int = 0)

        val state = MutableStateFlow(Snapshot(other = 7))
        val resource =
            KeyedResource(
                eagerBackground(),
                state.single({
                    it.name
                }, { s, v -> s.copy(name = v) }),
                TestFailure::map
            ) { _: SingleKey -> "Rômulo" }

        resource.load()

        assertEquals(Snapshot(Loadable.loaded("Rômulo"), other = 7), state.value)

        resource.clear()

        assertEquals(Snapshot(other = 7), state.value)
    }
}
