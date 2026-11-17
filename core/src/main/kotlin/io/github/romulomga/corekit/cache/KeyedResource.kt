package io.github.romulomga.corekit.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Valores lidos do servidor, um por chave, guardados em memória até o [clear]: o bloco dos repositórios de um assunto
 * "online" (o dado é do servidor e só faz sentido com ele; gêmeo do `KeyedResource` do CoreKit iOS). Toda busca roda no [scope] do app, não
 * no da tela: sair da tela para a leitura dela, nunca a busca, e quem chama ao mesmo tempo espera a mesma busca (o
 * `Deferred` em voo, não o `SingleFlight`, que largaria o segundo). Chamado do main, como o escopo do app.
 *
 * [mapError] dá nome de falha do app ao que a busca lançou; `null` diz que foi um cancelamento (a camada de rede do app
 * pode avisar um no tipo de erro dela), que nunca conta como falha.
 */
public class KeyedResource<K, V, F>(
    private val scope: CoroutineScope,
    private val slice: Slice<Map<K, Loadable<V, F>>>,
    private val mapError: (Exception) -> F?,
    private val fetch: suspend (K) -> V
) {
    private val tasks = mutableMapOf<K, Deferred<F?>>()

    /** Por chave: sobe a cada busca e a cada gravação, então uma resposta que saiu antes é descartada. */
    private val generations = mutableMapOf<K, Int>()

    /** Sobe no [clear]: uma resposta que saiu para a sessão anterior é descartada quando chega. */
    private var epoch = 0

    public val entries: Map<K, Loadable<V, F>> get() = slice.get()

    public fun entry(key: K): Loadable<V, F> = entries[key] ?: Loadable()

    /**
     * Uma tela mostra o valor: buscado na primeira vez (ou depois de uma carga cortada, ou de uma que falhou), nunca mais
     * depois de carregado. Idempotente, então a tela chama toda vez que aparece.
     */
    public fun load(key: K) {
        if (!entry(key).status.asksOnOpening || tasks[key] != null) return

        start(key)
    }

    /** "Tentar de novo" e puxar para atualizar: junta-se à busca a caminho (e à que a substituir). O erro é aviso. */
    public suspend fun refresh(key: K): F? {
        var task = tasks[key] ?: start(key)
        var generation = generation(key)
        val epoch = epoch

        while (true) {
            val outcome = task.outcome()

            if (epoch != this.epoch || generation == generation(key)) return outcome

            task = tasks[key] ?: return outcome
            generation = generation(key)
        }
    }

    /**
     * Algo pode ter mudado no servidor (uma gravação que deu timeout, a de outra tela): um valor já carregado é lido de
     * novo sem o esqueleto; um nunca pedido espera a tela dele.
     */
    public fun invalidate(key: K) {
        if (entry(key).status == LoadStatus.Idle) return

        start(key)
    }

    public fun invalidateAll() {
        entries.keys.toList().forEach(::invalidate)
    }

    /** O servidor confirmou uma gravação: o cache fica com o resultado, e uma leitura que saiu antes é descartada. */
    public fun set(key: K, value: V) {
        drop(key)
        edit(key) { Loadable.loaded(value) }
    }

    /** Uma mudança no lugar de um valor que já está aqui (um seguir, um contador). */
    public fun update(key: K, change: (V) -> V) {
        val entry = entries[key] ?: return

        if (!entry.hasValue) return

        @Suppress("UNCHECKED_CAST")
        edit(key) { it.copy(value = change(entry.value as V)) }
    }

    public fun updateAll(change: (V) -> V) {
        entries.keys.toList().forEach { update(it, change) }
    }

    /** A sessão acabou: nada fica, e as respostas ainda a caminho são descartadas. */
    public fun clear() {
        epoch += 1
        tasks.values.forEach { it.cancel() }
        tasks.clear()
        slice.update { emptyMap() }
    }

    // Busca

    private fun generation(key: K): Int = generations[key] ?: 0

    private fun drop(key: K) {
        tasks.remove(key)?.cancel()
        generations[key] = generation(key) + 1
    }

    private fun start(key: K): Deferred<F?> {
        drop(key)

        val generation = generation(key)
        val epoch = epoch
        val before = entry(key).status

        edit(key) { if (it.hasValue) it else it.copy(status = LoadStatus.Loading) }

        // Preguiçoso: guardado antes de rodar, porque no main imediato o corpo pode terminar dentro do `start()`.
        val task = scope.async(start = CoroutineStart.LAZY) { run(key, generation, epoch, before) }

        tasks[key] = task
        task.start()

        return task
    }

    private suspend fun run(key: K, generation: Int, epoch: Int, before: LoadStatus<F>): F? {
        val isCurrent = { epoch == this.epoch && generation == generation(key) }

        try {
            val value = fetch(key)

            currentCoroutineContext().ensureActive()

            if (!isCurrent()) return null

            edit(key) { Loadable.loaded(value) }

            return null
        } catch (error: CancellationException) {
            // Só uma busca mais nova, uma gravação ou o fim da sessão a cancelam: meia carga nunca é "carregada".
            if (isCurrent() && entry(key).status == LoadStatus.Loading) {
                edit(key) { it.copy(status = if (before == LoadStatus.Loaded) LoadStatus.Loaded else LoadStatus.Idle) }
            }

            throw error
        } catch (error: Exception) {
            if (!isCurrent()) return null

            val failure = mapError(error)

            if (failure == null) {
                // Cancelada pela camada de rede do app: como acima, nunca vira falha.
                if (entry(key).status == LoadStatus.Loading) {
                    edit(key) { it.copy(status = if (before == LoadStatus.Loaded) LoadStatus.Loaded else LoadStatus.Idle) }
                }

                return null
            }

            edit(key) { it.copy(status = if (it.hasValue) LoadStatus.Loaded else LoadStatus.Failed(failure)) }

            return failure
        } finally {
            if (isCurrent()) tasks.remove(key)
        }
    }

    private fun edit(key: K, change: (Loadable<V, F>) -> Loadable<V, F>) {
        slice.update { entries -> entries + (key to change(entries[key] ?: Loadable())) }
    }
}

/** O valor de um cache de um valor só. */
public val <V, F> KeyedResource<SingleKey, V, F>.value: Loadable<V, F> get() = entry(SingleKey.VALUE)

public fun <V, F> KeyedResource<SingleKey, V, F>.load(): Unit = load(SingleKey.VALUE)

public suspend fun <V, F> KeyedResource<SingleKey, V, F>.refresh(): F? = refresh(SingleKey.VALUE)

public fun <V, F> KeyedResource<SingleKey, V, F>.invalidate(): Unit = invalidate(SingleKey.VALUE)

public fun <V, F> KeyedResource<SingleKey, V, F>.set(value: V): Unit = set(SingleKey.VALUE, value)
