package io.github.romulomga.corekit.sync

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

sealed class SyncFailure : Exception() {
    data object Offline : SyncFailure()

    data object Local : SyncFailure()

    companion object {
        fun map(error: Exception): SyncFailure? = error as? SyncFailure ?: Local
    }
}

data class Row(override val entityId: String, val name: String) : SyncedRecord

class Gate {
    private val isHeld = MutableStateFlow(false)
    private val waiting = AtomicInteger(0)

    val waitingCount: Int get() = waiting.get()

    fun hold() {
        isHeld.value = true
    }

    fun release() {
        isHeld.value = false
    }

    suspend fun pass() {
        if (!isHeld.value) return

        waiting.incrementAndGet()

        try {
            isHeld.first { !it }
        } finally {
            waiting.decrementAndGet()
        }
    }
}

/** A fila do app, em memória. */
class MemoryOutbox : Outbox {
    val changes = mutableListOf<PendingChange>()

    override suspend fun pending(subject: String): List<PendingChange> = changes.filter { it.subject == subject }.sortedBy { it.createdAt }

    override suspend fun confirm(id: String) {
        changes.removeAll { it.id == id }
    }

    override suspend fun reject(id: String, reason: String) {
        edit(id) { it.copy(rejection = reason) }
    }

    override suspend fun recordAttempt(id: String) {
        edit(id) { it.copy(attempts = it.attempts + 1) }
    }

    override suspend fun discard(entityIds: Set<String>, subject: String) {
        changes.removeAll { it.subject == subject && it.entityId in entityIds }
    }

    override suspend fun removeAll(subject: String) {
        changes.removeAll { it.subject == subject }
    }

    fun queue(entityId: String, at: Long): PendingChange = PendingChange(
        subject = "rows",
        entityId = entityId,
        kind = PendingChange.Kind.UPSERT,
        payload = entityId,
        createdAt = Instant.ofEpochSecond(at)
    ).also { changes += it }

    private fun edit(id: String, change: (PendingChange) -> PendingChange) {
        val index = changes.indexOfFirst { it.id == id }

        if (index >= 0) changes[index] = change(changes[index])
    }
}

/** Um assunto sobre um servidor roteirizado: guarda o que foi mandado e o que foi aplicado. */
class FakeSubject : SyncSubject<Row> {
    override val name = "rows"

    var current = SyncState()
    var policy = ConflictPolicy.PENDING_WINS
    val rows = mutableMapOf<String, Row>()

    val pullGate = Gate()
    val pulls = ArrayDeque<PullBatch<Row>>()
    var pullFailure: Exception? = null
    val pullCursors = mutableListOf<String?>()

    var pushAnswer: (PendingChange) -> PushOutcome = { PushOutcome.Accepted }
    val pushed = mutableListOf<PendingChange>()
    val applied = mutableListOf<Pair<PullBatch<Row>, Set<String>>>()
    val events = mutableListOf<String>()

    override val conflictPolicy: ConflictPolicy get() = policy

    override suspend fun state(): SyncState = current

    override suspend fun pull(since: String?): PullBatch<Row> {
        pullCursors += since
        events += "pull"
        pullGate.pass()

        pullFailure?.let { throw it }

        return pulls.removeFirstOrNull() ?: PullBatch(emptyList(), cursor = since, shape = PullBatch.Shape.CHANGES)
    }

    override suspend fun apply(batch: PullBatch<Row>, keeping: Set<String>, state: SyncState) {
        applied += batch to keeping

        if (batch.shape == PullBatch.Shape.FULL_LIST) rows.keys.retainAll(keeping)

        batch.removed.forEach { rows.remove(it) }
        batch.records.forEach { rows[it.entityId] = it }
        current = state
    }

    override suspend fun push(change: PendingChange): PushOutcome {
        pushed += change
        events += "push ${change.entityId}"

        return pushAnswer(change)
    }

    override suspend fun resetState() {
        current = SyncState()
    }
}

/** O que o motor pediu para dormir; o sono termina cancelado, então um reenvio nunca roda sozinho num teste. */
class SleepSpy {
    val delays = mutableListOf<Duration>()

    val sleep: suspend (Duration) -> Unit = {
        delays += it
        throw CancellationException("test")
    }
}

/** O escopo "do app" num teste: roda na hora, no agendador do teste, e morre com ele. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.eagerBackground(): CoroutineScope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
