package io.github.romulomga.corekit.sync

import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Onde está o sync de um assunto, para as telas: "Atualizado há 3 h", "2 alterações esperando conexão". */
public data class SyncStatus<out F>(
    val syncedAt: Instant? = null,
    val isSyncing: Boolean = false,
    /** Mudanças esperando para ir (as recusadas não contam). */
    val pendingCount: Int = 0,
    /** Mudanças que o servidor recusou, esperando o app resolver. */
    val rejectedCount: Int = 0,
    /** A falha da última rodada; `null` quando uma rodada dá certo. */
    val lastFailure: F? = null
)

/** Quanto uma rodada que falhou espera antes de o motor tentar de novo sozinho: dobra a partir de [base], até [max]. */
public data class SyncRetry(val base: Duration = 5.seconds, val max: Duration = 300.seconds) {
    /** A espera depois de [failures] rodadas seguidas que falharam (1 é a primeira). */
    public fun delay(failures: Int): Duration {
        val doubled = base * (1L shl (failures - 1).coerceIn(0, 30)).toDouble()

        return if (doubled > max) max else doubled
    }
}

/**
 * Roda o sync de um assunto: manda a fila primeiro (da mais antiga, as mudanças seguintes de um registro depois das
 * anteriores), depois lê a partir do cursor e entrega ao assunto o que ele pode gravar. Uma rodada por vez: quem chama
 * durante uma rodada espera por ela (o `Deferred` em voo, não o `SingleFlight`, que largaria o segundo). Toda rodada
 * roda no [scope] do app, então sair da tela nunca a cancela. Gêmeo do `SyncEngine` do CoreKit iOS.
 *
 * Quando roda é do app: [syncIfStale] ao abrir, [sync] ao puxar, [pushPending] depois de uma gravação local, [follow]
 * quando a rede volta, e o WorkManager ([SyncWork]) como extra. Uma rodada que falha tenta de novo sozinha depois do
 * [SyncRetry].
 *
 * [mapError] dá nome de falha do app ao que falhou; `null` diz que foi um cancelamento, que nunca é falha.
 */
public class SyncEngine<R : SyncedRecord, F>(
    private val scope: CoroutineScope,
    private val subject: SyncSubject<R>,
    private val outbox: Outbox,
    private val mapError: (Exception) -> F?,
    private val retry: SyncRetry = SyncRetry(),
    private val now: () -> Instant = Instant::now,
    private val sleep: suspend (Duration) -> Unit = { delay(it) }
) {
    private val state = MutableStateFlow(SyncStatus<F>())

    public val status: StateFlow<SyncStatus<F>> = state.asStateFlow()

    private var running: Deferred<F?>? = null

    private var retrying: Job? = null

    private var failuresInARow = 0

    /** Uma gravação local durante uma rodada: a rodada manda a fila mais uma vez antes de terminar. */
    private var pushAgain = false

    /** Sobe no [reset]: uma rodada da sessão anterior termina sem gravar. */
    private var epoch = 0

    init {
        scope.launch {
            val syncedAt = subject.state().syncedAt

            state.update { it.copy(syncedAt = it.syncedAt ?: syncedAt) }
            refreshCounts()
        }
    }

    // Gatilhos

    /** Manda, depois lê. Quem chama durante uma rodada espera por ela. A falha é aviso. */
    public suspend fun sync(): F? = (running ?: start(pulling = true)).awaitOutcome()

    /** Abrir uma tela: sincroniza quando a cópia é mais velha que [maxAge], ou nunca foi lida. */
    public suspend fun syncIfStale(maxAge: Duration) {
        val syncedAt = state.value.syncedAt ?: subject.state().syncedAt

        if (syncedAt != null && java.time.Duration.between(syncedAt, now()) < maxAge.toJavaDuration()) return

        sync()
    }

    /** O app acabou de pôr uma mudança na fila (na mesma transação do registro): manda agora. */
    public suspend fun pushPending() {
        refreshCounts()

        val current = running

        if (current != null) {
            pushAgain = true
            current.awaitOutcome()
            return
        }

        start(pulling = false).awaitOutcome()
    }

    /** Sincroniza toda vez que a rede volta, até a coroutine de quem chama acabar. */
    public suspend fun follow(connectivity: Flow<Boolean>) {
        var wasOnline: Boolean? = null

        connectivity.collect { isOnline ->
            if (isOnline && wasOnline == false) sync()

            wasOnline = isOnline
        }
    }

    /** As contas de novo, pela fila (depois de o app resolver uma mudança recusada). */
    public suspend fun refreshCounts() {
        val changes = outbox.pending(subject.name)

        state.update { status ->
            status.copy(pendingCount = changes.count { it.rejection == null }, rejectedCount = changes.count { it.rejection != null })
        }
    }

    /** A sessão acabou: nada na fila fica, o cursor sai, e uma rodada a caminho termina sem gravar. */
    public suspend fun reset() {
        epoch += 1
        running?.cancel()
        running = null
        retrying?.cancel()
        retrying = null
        failuresInARow = 0
        pushAgain = false

        outbox.removeAll(subject.name)
        subject.resetState()

        state.value = SyncStatus()
    }

    // Rodada

    private fun start(pulling: Boolean): Deferred<F?> {
        retrying?.cancel()
        retrying = null
        state.update { it.copy(isSyncing = true) }

        val epoch = epoch

        // Preguiçoso: guardado antes de rodar, porque no main imediato o corpo pode terminar dentro do `start()`.
        val task = scope.async(start = CoroutineStart.LAZY) { run(pulling, epoch) }

        running = task
        task.start()

        return task
    }

    private suspend fun run(pulling: Boolean, epoch: Int): F? {
        val failure: F? = try {
            sendOutbox(epoch)

            if (pulling) pull(epoch)

            // Uma gravação local durante a rodada vai antes de ela terminar.
            while (pushAgain && epoch == this.epoch) {
                pushAgain = false
                sendOutbox(epoch)
            }

            null
        } catch (error: CancellationException) {
            // Nunca é falha: a rodada só sai do caminho (nada de suspender aqui, a coroutine pode estar cancelada).
            if (epoch == this.epoch) {
                running = null
                state.update { it.copy(isSyncing = false) }
            }

            throw error
        } catch (error: Exception) {
            mapError(error)
        }

        if (epoch != this.epoch) return null

        running = null
        state.update { it.copy(isSyncing = false) }
        refreshCounts()

        if (failure != null) {
            state.update { it.copy(lastFailure = failure) }
            failuresInARow += 1
            scheduleRetry()
        } else {
            state.update { it.copy(lastFailure = null) }
            failuresInARow = 0
        }

        return failure
    }

    /**
     * Da mais antiga; um registro com mudança recusada segura as seguintes até o app resolver. Uma falha no caminho para
     * a rodada: o resto vai, em ordem, da próxima vez.
     */
    private suspend fun sendOutbox(epoch: Int) {
        val blocked = mutableSetOf<String>()

        for (change in outbox.pending(subject.name)) {
            if (epoch != this.epoch) return

            if (change.rejection != null || change.entityId in blocked) {
                blocked += change.entityId
                continue
            }

            val outcome = try {
                subject.push(change).also { currentCoroutineContext().ensureActive() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (epoch == this.epoch) outbox.recordAttempt(change.id)

                throw error
            }

            if (epoch != this.epoch) return

            when (outcome) {
                PushOutcome.Accepted -> outbox.confirm(change.id)

                is PushOutcome.Rejected -> {
                    outbox.reject(change.id, outcome.reason)
                    blocked += change.entityId
                }
            }

            refreshCounts()
        }
    }

    /** A partir do cursor, página por página; cada página é gravada com o cursor dela numa transação só. */
    private suspend fun pull(epoch: Int) {
        var cursor = subject.state().cursor

        while (true) {
            val batch = subject.pull(cursor)

            currentCoroutineContext().ensureActive()

            if (epoch != this.epoch) return

            val waiting = outbox.pending(subject.name).map { it.entityId }.toSet()
            val touched = waiting intersect (batch.records.map { it.entityId }.toSet() + batch.removed)

            val (applied, keeping) = when (subject.conflictPolicy) {
                ConflictPolicy.PENDING_WINS ->
                    batch.copy(records = batch.records.filterNot { it.entityId in waiting }, removed = batch.removed - waiting) to waiting

                ConflictPolicy.SERVER_WINS -> {
                    if (touched.isNotEmpty()) outbox.discard(touched, subject.name)

                    batch to (waiting - touched)
                }
            }

            val syncedAt = now()

            subject.apply(applied, keeping, SyncState(batch.cursor, syncedAt))
            state.update { it.copy(syncedAt = syncedAt) }
            cursor = batch.cursor

            if (!batch.hasMore) return
        }
    }

    private fun scheduleRetry() {
        val wait = retry.delay(failuresInARow)

        retrying = scope.launch {
            sleep(wait)
            sync()
        }
    }

    /** A resposta da rodada a que alguém se juntou; nula quando ela foi cancelada (o fim da sessão). */
    private suspend fun Deferred<F?>.awaitOutcome(): F? = try {
        await()
    } catch (error: CancellationException) {
        currentCoroutineContext().ensureActive()

        null
    }
}
