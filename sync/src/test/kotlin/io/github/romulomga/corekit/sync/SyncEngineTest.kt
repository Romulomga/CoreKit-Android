package io.github.romulomga.corekit.sync

import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * As regras que todo assunto sincronizado ganha do motor: ordem, reenvio sem duplicar, recusa, o que a leitura pode
 * tocar, uma rodada por vez, o cursor, o fim da sessão. Gêmeo do `SyncEngineTests` do CoreKit iOS.
 */
class SyncEngineTest {
    private val subject = FakeSubject()
    private val outbox = MemoryOutbox()
    private val sleeper = SleepSpy()
    private val clock = Instant.ofEpochSecond(1_000_000)

    private fun TestScope.engine(retry: SyncRetry = SyncRetry()): SyncEngine<Row, SyncFailure> =
        SyncEngine(eagerBackground(), subject, outbox, SyncFailure::map, retry, now = { clock }, sleep = sleeper.sleep)

    // Envio

    @Test
    fun sendsTheOutboxOldestFirstBeforeReading() = runTest {
        outbox.queue("b", at = 2)
        outbox.queue("a", at = 1)
        val engine = engine()

        assertNull(engine.sync())

        assertEquals(listOf("push a", "push b", "pull"), subject.events)
        assertTrue(outbox.changes.isEmpty())
        assertEquals(0, engine.status.value.pendingCount)
    }

    /** A resposta se perdeu no caminho: a mesma mudança vai de novo, com o mesmo id, e o servidor a aplica uma vez. */
    @Test
    fun aChangeThatFailedOnTheWayGoesAgainWithTheSameId() = runTest {
        val change = outbox.queue("a", at = 1)
        subject.pushAnswer = { throw SyncFailure.Offline }
        val engine = engine()

        assertEquals(SyncFailure.Offline, engine.sync())
        assertEquals(listOf(1), outbox.changes.map { it.attempts })
        assertEquals(1, engine.status.value.pendingCount)
        assertFalse("nenhuma leitura depois de um envio que falhou no caminho", "pull" in subject.events)

        subject.pushAnswer = { PushOutcome.Accepted }
        assertNull(engine.sync())

        assertEquals(listOf(change.id, change.id), subject.pushed.map { it.id })
        assertTrue(outbox.changes.isEmpty())
    }

    /** As mudanças seguintes do registro esperam o app resolver a recusada; os outros registros seguem. */
    @Test
    fun aRefusedChangeHoldsTheRecordsLaterChanges() = runTest {
        val refused = outbox.queue("a", at = 1)
        outbox.queue("a", at = 2)
        outbox.queue("b", at = 3)
        subject.pushAnswer = { if (it.id == refused.id) PushOutcome.Rejected("invalid") else PushOutcome.Accepted }
        val engine = engine()

        assertNull(engine.sync())

        assertEquals(listOf("a", "b"), subject.pushed.map { it.entityId })
        assertEquals(listOf("a", "a"), outbox.changes.map { it.entityId })
        assertEquals("invalid", outbox.changes.first().rejection)
        assertEquals(1, engine.status.value.rejectedCount)
        assertEquals(1, engine.status.value.pendingCount)

        engine.sync()
        assertEquals("nada do registro vai enquanto a recusa fica", 2, subject.pushed.size)
    }

    @Test
    fun aLocalWriteWhileARunIsOnItsWayGoesBeforeTheRunEnds() = runTest {
        subject.pullGate.hold()
        val engine = engine()
        val syncing = eagerBackground().async { engine.sync() }

        outbox.queue("a", at = 1)
        val pushing = eagerBackground().async { engine.pushPending() }
        subject.pullGate.release()
        syncing.await()
        pushing.await()

        assertEquals(listOf("a"), subject.pushed.map { it.entityId })
        assertTrue(outbox.changes.isEmpty())
    }

    // Leitura

    /** Um registro com mudança esperando fica com a versão local: a mudança vai primeiro e o servidor decide. */
    @Test
    fun aPullLeavesARecordWithAChangeWaitingAlone() = runTest {
        subject.rows["a"] = Row("a", "local")
        subject.rows["c"] = Row("c", "gone")
        outbox.queue("a", at = 1)
        subject.pushAnswer = { PushOutcome.Rejected("held") }
        subject.pulls += PullBatch(listOf(Row("a", "server"), Row("b", "new")), cursor = "1", shape = PullBatch.Shape.FULL_LIST)
        val engine = engine()

        engine.sync()

        assertEquals("local", subject.rows["a"]?.name)
        assertEquals("new", subject.rows["b"]?.name)
        assertNull("uma lista inteira apaga o que não traz", subject.rows["c"])
        assertEquals(setOf("a"), subject.applied.last().second)
    }

    @Test
    fun withServerWinsThePullReplacesTheRecordAndItsChangesGo() = runTest {
        subject.policy = ConflictPolicy.SERVER_WINS
        subject.rows["a"] = Row("a", "local")
        outbox.queue("a", at = 1)
        subject.pushAnswer = { PushOutcome.Rejected("conflict") }
        subject.pulls += PullBatch(listOf(Row("a", "server")), cursor = "1", shape = PullBatch.Shape.CHANGES)
        val engine = engine()

        engine.sync()

        assertEquals("server", subject.rows["a"]?.name)
        assertTrue(outbox.changes.isEmpty())
        assertEquals(0, engine.status.value.rejectedCount)
    }

    @Test
    fun theCursorMovesPageByPageWithTheData() = runTest {
        subject.current = SyncState(cursor = "0")
        subject.pulls += PullBatch(listOf(Row("a", "1")), cursor = "1", shape = PullBatch.Shape.CHANGES, hasMore = true)
        subject.pulls += PullBatch(listOf(Row("b", "2")), removed = setOf("a"), cursor = "2", shape = PullBatch.Shape.CHANGES)
        val engine = engine()

        engine.sync()

        assertEquals(listOf("0", "1"), subject.pullCursors)
        assertEquals("2", subject.current.cursor)
        assertEquals(listOf("b"), subject.rows.keys.sorted())
        assertEquals(clock, engine.status.value.syncedAt)
    }

    /** Uma leitura que falha não grava nada: o cursor fica, e o motor tenta de novo sozinho depois da espera. */
    @Test
    fun aFailedPullKeepsTheCursorAndTriesAgainLater() = runTest {
        subject.current = SyncState(cursor = "5")
        subject.pullFailure = SyncFailure.Offline
        val engine = engine(SyncRetry(base = 5.seconds, max = 300.seconds))

        assertEquals(SyncFailure.Offline, engine.sync())

        assertTrue(subject.applied.isEmpty())
        assertEquals("5", subject.current.cursor)
        assertEquals(SyncFailure.Offline, engine.status.value.lastFailure)
        advanceUntilIdle()
        assertEquals(listOf(5.seconds), sleeper.delays)

        subject.pullFailure = null
        assertNull(engine.sync())
        assertNull(engine.status.value.lastFailure)
    }

    @Test
    fun aCancellationIsNotAFailure() = runTest {
        subject.pullFailure = kotlinx.coroutines.CancellationException("gone")
        val engine = engine()

        assertNull(engine.sync())
        assertNull(engine.status.value.lastFailure)
        assertTrue("nada a tentar de novo", sleeper.delays.isEmpty())
        assertFalse(engine.status.value.isSyncing)
    }

    // Uma por vez

    @Test
    fun callersAtTheSameTimeWaitForTheSameRun() = runTest {
        subject.pullGate.hold()
        val engine = engine()

        val first = eagerBackground().async { engine.sync() }
        val second = eagerBackground().async { engine.sync() }

        assertTrue(engine.status.value.isSyncing)
        subject.pullGate.release()
        first.await()
        second.await()

        assertEquals(1, subject.pullCursors.size)
        assertFalse(engine.status.value.isSyncing)
    }

    // Quando

    @Test
    fun aFreshCopyIsLeftAloneAndAStaleOneIsReadAgain() = runTest {
        subject.current = SyncState(cursor = "1", syncedAt = clock.minusSeconds(3600))
        val engine = engine()

        engine.syncIfStale(86_400.seconds)
        assertTrue(subject.pullCursors.isEmpty())

        engine.syncIfStale(1800.seconds)
        assertEquals(listOf<String?>("1"), subject.pullCursors)
    }

    @Test
    fun theNetworkComingBackSyncs() = runTest {
        val engine = engine()

        engine.follow(flowOf(true, true, false, true))

        assertEquals("só de sem rede para com rede", 1, subject.pullCursors.size)
    }

    @Test
    fun theWaitDoublesUpToTheMaximum() {
        val retry = SyncRetry(base = 5.seconds, max = 30.seconds)

        assertEquals(listOf(5.seconds, 10.seconds, 20.seconds, 30.seconds, 30.seconds), (1..5).map(retry::delay))
    }

    // Fim da sessão

    @Test
    fun theEndOfTheSessionEmptiesTheQueueAndDropsARunOnItsWay() = runTest {
        outbox.queue("other", at = 1)
        subject.current = SyncState(cursor = "9", syncedAt = clock)
        subject.pulls += PullBatch(listOf(Row("a", "old user")), cursor = "10", shape = PullBatch.Shape.CHANGES)
        subject.pullGate.hold()
        val engine = engine()
        val syncing = eagerBackground().async { engine.sync() }

        engine.reset()
        subject.pullGate.release()
        syncing.await()

        assertTrue(outbox.changes.isEmpty())
        assertEquals(SyncState(), subject.current)
        assertTrue("a resposta de quem saiu nunca chega", subject.applied.isEmpty())
        assertEquals(SyncStatus<SyncFailure>(), engine.status.value)
    }

    /** A fila é do app, no banco dele: um motor novo (o app aberto de novo) manda o que o anterior deixou. */
    @Test
    fun aNewEngineSendsWhatTheLastOneLeft() = runTest {
        outbox.queue("a", at = 1)
        subject.pushAnswer = { throw SyncFailure.Offline }
        engine().sync()

        subject.pushAnswer = { PushOutcome.Accepted }
        val reopened = engine()
        assertEquals(1, reopened.status.value.pendingCount)
        reopened.sync()

        assertTrue(outbox.changes.isEmpty())
    }
}
