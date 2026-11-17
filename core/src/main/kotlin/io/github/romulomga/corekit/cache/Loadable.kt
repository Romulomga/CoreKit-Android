package io.github.romulomga.corekit.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Onde está um pedaço de um cache. Uma carga cortada no meio nunca vira [Loaded]: volta ao que era. */
public sealed interface LoadStatus<out F> {
    /** Nunca pedido: o primeiro leitor pede. */
    public data object Idle : LoadStatus<Nothing>

    public data object Loading : LoadStatus<Nothing>

    public data object Loaded : LoadStatus<Nothing>

    /** A falha como o app a nomeia (o `mapError` do bloco). */
    public data class Failed<out F>(val error: F) : LoadStatus<F>
}

/**
 * O que a abertura de uma tela pede: o nunca pedido, ou o que falhou (cada abertura e cada volta sempre pediram de novo
 * depois de uma falha); um valor carregado ou a caminho fica como está.
 */
public val LoadStatus<*>.asksOnOpening: Boolean get() = this == LoadStatus.Idle || this is LoadStatus.Failed

/**
 * Um valor de um cache e onde ele está. O valor fica enquanto é lido de novo (um refresh, uma gravação que pode ter
 * acontecido): só um valor nunca carregado mostra [LoadStatus.Loading] ou [LoadStatus.Failed]. [hasValue] separa "nunca
 * veio" de "veio nulo" (uma pergunta sem resposta, um ajuste nunca feito), o `Optional` duplo do iOS.
 */
public data class Loadable<out V, out F>(
    val value: V? = null,
    val hasValue: Boolean = value != null,
    val status: LoadStatus<F> = LoadStatus.Idle
) {
    public companion object {
        public fun <V> loaded(value: V): Loadable<V, Nothing> = Loadable(value, hasValue = true, status = LoadStatus.Loaded)
    }
}

/** A chave de um cache que guarda um valor só. */
public enum class SingleKey { VALUE }

/**
 * Onde um bloco guarda o que é dele: um pedaço do único `MutableStateFlow` do repositório. O bloco escreve ali na hora,
 * então o `snapshot.value` do repositório nunca fica atrás de uma gravação (um `combine` + `stateIn` ficaria).
 */
public class Slice<T>(public val get: () -> T, public val update: ((T) -> T) -> Unit)

/** Um pedaço de [this]: o que [get] lê e [set] devolve no lugar. */
public fun <S, T> MutableStateFlow<S>.slice(get: (S) -> T, set: (S, T) -> S): Slice<T> =
    Slice(get = { get(value) }, update = { change -> update { set(it, change(get(it))) } })

/** O pedaço de um valor só ([SingleKey]), guardado no snapshot como um [Loadable] simples. */
public fun <S, V, F> MutableStateFlow<S>.single(
    get: (S) -> Loadable<V, F>,
    set: (S, Loadable<V, F>) -> S
): Slice<Map<SingleKey, Loadable<V, F>>> = slice(
    get = { mapOf(SingleKey.VALUE to get(it)) },
    set = { state, entries -> set(state, entries[SingleKey.VALUE] ?: Loadable()) }
)

/** O fluxo inteiro como pedaço: para um bloco sozinho (os testes dele). */
public fun <T> MutableStateFlow<T>.whole(): Slice<T> = slice(get = { it }, set = { _, value -> value })

/** A resposta de uma busca a que alguém se juntou; nula quando outra a substituiu (quem chamou vê a dela). */
internal suspend fun <F> Deferred<F?>.outcome(): F? = try {
    await()
} catch (error: CancellationException) {
    // Quem chamou foi cancelado: segue cancelado. Senão, a busca é que caiu, e isso não é erro de ninguém.
    currentCoroutineContext().ensureActive()

    null
}
