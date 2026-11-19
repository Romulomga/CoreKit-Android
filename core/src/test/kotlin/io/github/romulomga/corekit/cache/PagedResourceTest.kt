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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A paginação dos caches de `data/` (o `PagedResourceTests` do iOS): offset ou número da página, sem ids repetidos,
 * nenhuma próxima página durante um recomeço, e buscas que sobrevivem à tela.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PagedResourceTest {
    data class Row(val id: Int, val isFollowing: Boolean = false)

    private val server = FakeCall<PageRequest, Page<Row>> { page(0, 3, hasMore = true) }

    private fun TestScope.resource(): PagedResource<String, Row, TestFailure> = PagedResource(
        eagerBackground(),
        MutableStateFlow(emptyMap<String, PagedList<Row, TestFailure>>()).whole(),
        Row::id,
        TestFailure::map
    ) {
            _,
            request
        ->
        server(request)
    }

    @Test
    fun loadingTwiceKeepsThePagesAndAsksTheServerOnce() = runTest {
        val resource = resource()

        resource.load("list")
        server.returns(page(3, 3, hasMore = false))
        resource.loadMore("list")
        resource.load("list")

        assertEquals((0 until 6).toList(), resource.list("list").items.map { it.id })
        assertEquals(listOf(PageRequest(offset = 0, number = 1), PageRequest(offset = 3, number = 2)), server.calls)
    }

    @Test
    fun theNextPageStartsAtTheRowsLoadedAndSkipsTheOnesAlreadyThere() = runTest {
        val resource = resource()

        resource.refresh("list")
        server.returns(page(2, 3, hasMore = false))

        assertNull(resource.loadMore("list"))
        assertEquals("a linha repetida não é desenhada duas vezes", listOf(0, 1, 2, 3, 4), resource.list("list").items.map { it.id })
        assertFalse(resource.list("list").hasMore)
        assertNull(resource.loadMore("list"))
        assertEquals("nenhuma página depois da última", 2, server.count)
    }

    /** A tela que pediu o recomeço sai antes de ele chegar: a primeira página é do cache e chega mesmo assim. */
    @Test
    fun aRefreshAskedByALeavingScreenStillLands() = runTest {
        val resource = resource()
        val screen = CoroutineScope(eagerBackground().coroutineContext + Job())

        server.gate.hold()
        screen.launch { resource.refresh("list") }
        screen.cancel()
        server.gate.release()
        advanceUntilIdle()

        assertEquals("a busca é do cache, não de quem foi cancelado", listOf(0, 1, 2), resource.list("list").items.map { it.id })
        assertEquals(LoadStatus.Loaded, resource.list("list").status)
    }

    /** A linha que pediu a página sai da tela; a página chega mesmo assim. */
    @Test
    fun aPageAskedByARowThatLeftStillLands() = runTest {
        val resource = resource()
        val row = CoroutineScope(eagerBackground().coroutineContext + Job())

        resource.refresh("list")
        server.returns(page(3, 3, hasMore = false))
        server.gate.hold()
        row.launch { resource.loadMore("list") }
        row.cancel()
        server.gate.release()
        advanceUntilIdle()

        assertEquals(6, resource.list("list").items.size)
        assertFalse(resource.list("list").isLoadingMore)
    }

    /** As linhas na tela são da lista velha: a conta delas seria o offset da nova. */
    @Test
    fun noNextPageLeavesWhileThePageRestartsAndAPageOnItsWayIsLetGo() = runTest {
        val resource = resource()

        resource.refresh("list")
        server.gate.hold()

        val nextPage = eagerBackground().async { resource.loadMore("list") }

        assertTrue(resource.list("list").isLoadingMore)

        val refreshing = eagerBackground().async { resource.refresh("list") }

        assertFalse("o recomeço largou a próxima página", resource.list("list").isLoadingMore)
        assertNull(resource.loadMore("list"))
        assertEquals("nenhuma próxima página com a primeira a caminho", 3, server.count)

        server.gate.release()
        advanceUntilIdle()

        assertNull("uma página largada não diz nada", nextPage.await())
        refreshing.await()
        assertEquals(listOf(0, 1, 2), resource.list("list").items.map { it.id })
    }

    @Test
    fun aFailedFirstPageFailsAndAFailedRefreshKeepsTheRows() = runTest {
        val resource = resource()

        server.fails(TestFailure.Offline)
        resource.load("list")

        assertEquals(LoadStatus.Failed(TestFailure.Offline), resource.list("list").status)

        server.returns(page(0, 3, hasMore = false))

        assertNull(resource.refresh("list"))

        server.fails(TestFailure.Server)

        assertEquals(TestFailure.Server, resource.refresh("list"))
        assertEquals(3, resource.list("list").items.size)
        assertEquals(LoadStatus.Loaded, resource.list("list").status)
    }

    /**
     * Uma primeira página cortada no meio (o escopo do app cancelado) volta ao que era, nunca "carregada" pela metade
     * nem falha, e a próxima tela pede de novo.
     */
    @Test
    fun aFirstPageCutShortGoesBackToIdle() = runTest {
        val scope = CoroutineScope(eagerBackground().coroutineContext + Job())
        val resource =
            PagedResource(scope, MutableStateFlow(emptyMap<String, PagedList<Row, TestFailure>>()).whole(), Row::id, TestFailure::map) {
                    _,
                    request
                ->
                server(request)
            }

        server.gate.hold()
        resource.load("list")
        scope.cancel()

        assertEquals(LoadStatus.Idle, resource.list("list").status)
    }

    /** Endpoints que contam páginas recebem o número; a geração de um recomeço volta à página 1. */
    @Test
    fun aRestartStartsAgainAtPageOne() = runTest {
        val resource = resource()

        resource.refresh("list")
        resource.loadMore("list")
        resource.refresh("list")

        assertEquals(listOf(1, 2, 1), server.calls.map { it.number })
        assertEquals(1, resource.list("list").pages)
    }

    @Test
    fun anInvalidatedListIsReadAgainQuietlyAndOneNeverShownWaits() = runTest {
        val resource = resource()

        resource.refresh("list")
        server.gate.hold()
        resource.invalidate("list")
        resource.invalidate("other")

        assertEquals("sem esqueleto", LoadStatus.Loaded, resource.list("list").status)
        assertEquals(3, resource.list("list").items.size)
        assertEquals(LoadStatus.Idle, resource.list("other").status)
        assertEquals(2, server.count)
    }

    /** A próxima abertura de uma lista que falhou pede a primeira página de novo; uma carregada fica como está. */
    @Test
    fun theNextOpeningOfAFailedListAsksAgain() = runTest {
        val resource = resource()

        server.fails(TestFailure.Offline)
        resource.load("list")

        assertEquals(LoadStatus.Failed(TestFailure.Offline), resource.list("list").status)

        server.returns(page(0, 3, hasMore = false))
        resource.load("list")
        resource.load("list")

        assertEquals(2, server.count)
        assertEquals(3, resource.list("list").items.size)
    }

    /** Uma gravação que pode ter chegado: só as listas que mostram a linha leem de novo. */
    @Test
    fun theListsShowingARowAreReadAgain() = runTest {
        val resource = resource()

        server.returns(page(0, 3, hasMore = false))
        resource.refresh("followers")
        server.returns(page(10, 3, hasMore = false))
        resource.refresh("following")
        server.returns(page(0, 2, hasMore = false))
        resource.invalidateLists(showing = 1)

        assertEquals(3, server.count)
        assertEquals(listOf(0, 1), resource.list("followers").items.map { it.id })
        assertEquals(listOf(10, 11, 12), resource.list("following").items.map { it.id })
    }

    @Test
    fun aRowChangesInEveryListThatShowsIt() = runTest {
        val resource = resource()

        server.returns(page(0, 3, hasMore = false))
        resource.refresh("followers")
        resource.refresh("following")
        resource.updateItem(1) { it.copy(isFollowing = true) }

        assertTrue(resource.list("followers").items[1].isFollowing)
        assertTrue(resource.list("following").items[1].isFollowing)
    }

    /** Uma abertura que não pode mostrar o guardado: as linhas saem na hora, e a próxima página a caminho nunca pousa. */
    @Test
    fun aReloadDropsTheRowsAndThePagesOnTheirWay() = runTest {
        val resource = resource()

        resource.refresh("list")
        server.gate.hold()
        val nextPage = eagerBackground().async { resource.loadMore("list") }
        server.returns(page(10, 2, hasMore = false))
        resource.reload("list")

        assertTrue("nada guardado na tela", resource.list("list").items.isEmpty())
        assertEquals(LoadStatus.Loading, resource.list("list").status)

        server.gate.release()
        advanceUntilIdle()
        nextPage.await()

        assertEquals(listOf(10, 11), resource.list("list").items.map { it.id })
        assertEquals(1, resource.list("list").pages)
    }

    // O total, linhas tiradas, e um refresh juntado a um recomeço trocado no caminho

    @Test
    fun theTotalFollowsTheServerAndKeepsWhenAPageDoesNotSayIt() = runTest {
        val resource = resource()

        server.returns(Page((0 until 3).map { Row(it) }, hasMore = true, total = 7))
        resource.refresh("list")

        assertEquals(7, resource.list("list").total)

        server.returns(page(3, 3, hasMore = true))
        resource.loadMore("list")

        assertEquals("uma página que não diz mantém o último total", 7, resource.list("list").total)
    }

    /** QA 26/09 (Homer): excluir 15 de 162 publicações inativas mostrava "nenhuma publicação". */
    @Test
    fun aListEmptiedByAWriteReadsTheServerAgainUnderTheSkeleton() = runTest {
        val resource = resource()

        server.returns(Page((0 until 3).map { Row(it) }, hasMore = true, total = 10))
        resource.refresh("list")
        server.gate.hold()
        server.returns(Page((3 until 6).map { Row(it) }, hasMore = true, total = 7))

        assertTrue(resource.removeItems(setOf(0, 1, 2), from = "list"))
        assertTrue(resource.list("list").items.isEmpty())
        assertEquals("o esqueleto, nunca o vazio", LoadStatus.Loading, resource.list("list").status)
        assertEquals(7, resource.list("list").total)

        server.gate.release()
        advanceUntilIdle()

        assertEquals(listOf(3, 4, 5), resource.list("list").items.map { it.id })
        assertEquals(PageRequest(offset = 0, number = 1), server.calls.last())
    }

    @Test
    fun rowsTakenOutBringTheNextPageWhenTheServerHasMore() = runTest {
        val resource = resource()

        server.returns(Page((0 until 3).map { Row(it) }, hasMore = true, total = 6))
        resource.refresh("list")
        server.returns(Page((3 until 6).map { Row(it) }, hasMore = false, total = 5))

        assertTrue(resource.removeItems(setOf(1), from = "list"))
        advanceUntilIdle()

        assertEquals(listOf(0, 2, 3, 4, 5), resource.list("list").items.map { it.id })
        assertEquals("o offset são as linhas que ficaram", PageRequest(offset = 2, number = 2), server.calls.last())
        assertEquals(5, resource.list("list").total)
    }

    @Test
    fun rowsThatWereNotThereChangeNothing() = runTest {
        val resource = resource()

        server.returns(Page((0 until 3).map { Row(it) }, hasMore = false, total = 3))
        resource.refresh("list")

        assertFalse(resource.removeItems(setOf(9), from = "list"))
        assertTrue(resource.removeItems(setOf(0, 1, 2), from = "list"))
        assertEquals("esvaziada sem mais no servidor: o vazio", LoadStatus.Loaded, resource.list("list").status)
        assertEquals(0, resource.list("list").total)
        assertEquals(1, server.count)
    }

    /** Um puxar juntado a um recomeço que uma gravação trocou, e a troca chegou antes: diz o que decidiu a lista. */
    @Test
    fun aRefreshSaysWhatTheRestartThatReplacedItsAnswered() = runTest {
        val resource = resource()

        server.returns(page(0, 3, hasMore = false))
        resource.refresh("list")
        server.gate.hold()

        val pulling = eagerBackground().async { resource.refresh("list") }

        server.fails(TestFailure.Offline)
        resource.invalidate("list")
        server.gate.release()
        advanceUntilIdle()

        assertEquals(TestFailure.Offline, pulling.await())
    }

    @Test
    fun theEndOfTheSessionEmptiesTheListsAndDropsAnswersOnTheirWay() = runTest {
        val resource = resource()

        resource.refresh("list")
        server.gate.hold()

        val refreshing = eagerBackground().async { resource.refresh("list") }

        resource.clear()
        server.gate.release()
        refreshing.await()

        assertTrue("a resposta do usuário anterior nunca chega", resource.lists.isEmpty())
    }

    private companion object {
        /** As linhas `start until start + count`, com mais depois delas enquanto [hasMore]. */
        fun page(start: Int, count: Int, hasMore: Boolean): Page<Row> = Page((start until start + count).map { Row(it) }, hasMore)
    }
}
