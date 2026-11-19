package io.github.romulomga.corekit.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow

/** O que uma página pede ao servidor: o [offset] (as linhas já carregadas) nos endpoints offset/limit, o [number] (de 1) nos que contam páginas. */
public data class PageRequest(val offset: Int, val number: Int)

/** Uma página como o servidor respondeu; [hasMore] é a regra do endpoint (`total`, ou uma página menor que o limite). */
public data class Page<out T>(val items: List<T>, val hasMore: Boolean)

/** Uma lista paginada como o servidor deu por último, sem ids repetidos. */
public data class PagedList<out T, out F>(
    val items: List<T> = emptyList(),
    val hasMore: Boolean = false,
    val status: LoadStatus<F> = LoadStatus.Idle,
    val isLoadingMore: Boolean = false,
    /** Páginas somadas desde o último recomeço: a próxima é `pages + 1`. */
    val pages: Int = 0
)

/**
 * Listas paginadas lidas do servidor, uma por chave (uma busca, um usuário), guardadas em memória até o [clear]: o
 * gêmeo do `PagedResource` do CoreKit iOS. [mapError] funciona como no [KeyedResource]. Um recomeço (puxar, tentar de novo,
 * uma gravação que pode ter acontecido) substitui a primeira página e larga a próxima a caminho; nenhuma próxima página
 * sai durante um recomeço (as linhas na tela são da lista velha, e a conta delas seria o offset da nova); toda busca
 * roda no [scope] do app, então sair da tela nunca a cancela. [id] identifica a linha para o dedupe.
 */
public class PagedResource<K, T, F>(
    private val scope: CoroutineScope,
    private val slice: Slice<Map<K, PagedList<T, F>>>,
    private val id: (T) -> Any?,
    private val mapError: (Exception) -> F?,
    private val fetch: suspend (K, PageRequest) -> Page<T>
) {
    private val restarts = mutableMapOf<K, Deferred<F?>>()
    private val nextPages = mutableMapOf<K, Deferred<F?>>()

    /** Por lista: sobe a cada recomeço, então uma página pedida para a lista substituída é descartada. */
    private val generations = mutableMapOf<K, Int>()
    private var epoch = 0

    public val lists: Map<K, PagedList<T, F>> get() = slice.get()

    public fun list(key: K): PagedList<T, F> = lists[key] ?: PagedList()

    /**
     * Uma tela mostra a lista: a primeira página na primeira vez (ou depois de uma carga cortada, ou de uma que falhou),
     * nunca mais depois de carregada.
     */
    public fun load(key: K) {
        if (!list(key).status.asksOnOpening || restarts[key] != null) return

        startRestart(key)
    }

    /** Puxar para atualizar e "Tentar de novo": junta-se ao recomeço a caminho. As linhas na tela ficam até a nova primeira página. */
    public suspend fun refresh(key: K): F? {
        var task = restarts[key] ?: startRestart(key)
        var generation = generation(key)
        val epoch = epoch

        while (true) {
            val outcome = task.outcome()

            if (epoch != this.epoch || generation == generation(key)) return outcome

            task = restarts[key] ?: return outcome
            generation = generation(key)
        }
    }

    /** A próxima página; o erro é aviso. A linha que pediu pode sair da tela: a página chega mesmo assim. */
    public suspend fun loadMore(key: K): F? {
        nextPages[key]?.let { return it.outcome() }

        val list = list(key)

        if (list.status != LoadStatus.Loaded || !list.hasMore || restarts[key] != null) return null

        return startNextPage(key).outcome()
    }

    /** Algo pode ter mudado no servidor: uma lista já mostrada lê a primeira página de novo, sem esqueleto. */
    public fun invalidate(key: K) {
        if (list(key).status == LoadStatus.Idle) return

        startRestart(key)
    }

    public fun invalidateAll() {
        lists.keys.toList().forEach(::invalidate)
    }

    /**
     * Uma tela abre e não pode mostrar o guardado: as linhas saem, as páginas a caminho são largadas e a primeira página
     * é lida de novo sob o esqueleto. O [invalidate] é o silencioso, com as linhas na tela. Gêmeo do `reload` do iOS.
     */
    public fun reload(key: K) {
        edit(key) { PagedList() }
        startRestart(key)
    }

    /** Toda lista que mostra a linha lê a primeira página de novo: uma gravação que pode ter chegado a mexeu. */
    public fun invalidateLists(showing: Any?) {
        lists.filterValues { list -> list.items.any { id(it) == showing } }.keys.forEach(::invalidate)
    }

    /** Uma linha mudada por uma gravação que o servidor confirmou, em toda lista que a mostra. */
    public fun updateItem(itemId: Any?, change: (T) -> T) {
        slice.update { lists ->
            lists.mapValues { (_, list) ->
                if (list.items.none { id(it) == itemId }) {
                    list
                } else {
                    list.copy(
                        items = list.items.map {
                            if (id(it) ==
                                itemId
                            ) {
                                change(it)
                            } else {
                                it
                            }
                        }
                    )
                }
            }
        }
    }

    /** A sessão acabou: nada fica, e as respostas ainda a caminho são descartadas. */
    public fun clear() {
        epoch += 1
        (restarts.values + nextPages.values).forEach { it.cancel() }
        restarts.clear()
        nextPages.clear()
        slice.update { emptyMap() }
    }

    // Primeira página

    private fun generation(key: K): Int = generations[key] ?: 0

    private fun isCurrent(key: K, generation: Int, epoch: Int): Boolean = epoch == this.epoch && generation == generation(key)

    private fun startRestart(key: K): Deferred<F?> {
        nextPages.remove(key)?.cancel()
        restarts[key]?.cancel()

        val generation = generation(key) + 1
        val epoch = epoch
        val before = list(key).status

        generations[key] = generation
        edit(key) { list ->
            val showsSkeleton = list.items.isEmpty() && list.status != LoadStatus.Loaded

            list.copy(isLoadingMore = false, status = if (showsSkeleton) LoadStatus.Loading else list.status)
        }

        // Preguiçoso: guardado antes de rodar, porque no main imediato o corpo pode terminar dentro do `start()`.
        val task = scope.async(start = CoroutineStart.LAZY) { fetchFirstPage(key, generation, epoch, before) }

        restarts[key] = task
        task.start()

        return task
    }

    private suspend fun fetchFirstPage(key: K, generation: Int, epoch: Int, before: LoadStatus<F>): F? {
        try {
            val page = fetch(key, PageRequest(offset = 0, number = 1))

            currentCoroutineContext().ensureActive()

            if (!isCurrent(key, generation, epoch)) return null

            edit(key) { PagedList(items = unique(page.items), hasMore = page.hasMore, status = LoadStatus.Loaded, pages = 1) }

            return null
        } catch (error: CancellationException) {
            if (isCurrent(key, generation, epoch) && list(key).status == LoadStatus.Loading) {
                edit(key) { it.copy(status = if (before == LoadStatus.Loaded) LoadStatus.Loaded else LoadStatus.Idle) }
            }

            throw error
        } catch (error: Exception) {
            if (!isCurrent(key, generation, epoch)) return null

            val failure = mapError(error)

            if (failure == null) {
                if (list(key).status == LoadStatus.Loading) {
                    edit(key) { it.copy(status = if (before == LoadStatus.Loaded) LoadStatus.Loaded else LoadStatus.Idle) }
                }

                return null
            }

            edit(key) {
                it.copy(status = if (it.items.isEmpty() && before != LoadStatus.Loaded) LoadStatus.Failed(failure) else LoadStatus.Loaded)
            }

            return failure
        } finally {
            if (isCurrent(key, generation, epoch)) restarts.remove(key)
        }
    }

    // Próxima página

    private fun startNextPage(key: K): Deferred<F?> {
        val generation = generation(key)
        val epoch = epoch

        edit(key) { it.copy(isLoadingMore = true) }

        val task = scope.async(start = CoroutineStart.LAZY) { fetchNextPage(key, generation, epoch) }

        nextPages[key] = task
        task.start()

        return task
    }

    private suspend fun fetchNextPage(key: K, generation: Int, epoch: Int): F? {
        val current = list(key)

        try {
            val page = fetch(key, PageRequest(offset = current.items.size, number = current.pages + 1))

            currentCoroutineContext().ensureActive()

            if (!isCurrent(key, generation, epoch)) return null

            edit(key) { list ->
                val known = list.items.map(id).toSet()

                list.copy(
                    items = list.items + unique(page.items.filterNot { id(it) in known }),
                    hasMore = page.hasMore,
                    pages =
                        list.pages + 1
                )
            }

            return null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return if (isCurrent(key, generation, epoch)) mapError(error) else null
        } finally {
            // Um recomeço no meio já largou esta página e pode ter começado o dele.
            if (isCurrent(key, generation, epoch)) {
                edit(key) { it.copy(isLoadingMore = false) }
                nextPages.remove(key)
            }
        }
    }

    private fun unique(items: List<T>): List<T> = items.distinctBy(id)

    private fun edit(key: K, change: (PagedList<T, F>) -> PagedList<T, F>) {
        slice.update { lists -> lists + (key to change(lists[key] ?: PagedList())) }
    }
}

/** O pedaço de uma lista só ([SingleKey]), guardada no snapshot como um [PagedList] simples. */
public fun <S, T, F> MutableStateFlow<S>.singleList(
    get: (S) -> PagedList<T, F>,
    set: (S, PagedList<T, F>) -> S
): Slice<Map<SingleKey, PagedList<T, F>>> = slice(
    get = { mapOf(SingleKey.VALUE to get(it)) },
    set = { state, lists -> set(state, lists[SingleKey.VALUE] ?: PagedList()) }
)
