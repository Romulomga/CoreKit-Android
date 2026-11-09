package io.github.romulomga.corekit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Uma ação que grava roda uma vez por vez: o segundo toque, dado enquanto a primeira gravação ainda roda, é ignorado.
 * No iOS a gravação é síncrona e o botão some na hora; no Android ela é uma corrotina, e sem esta trava dois toques
 * rápidos gravam dois registros.
 *
 * A trava fecha de forma síncrona, antes de a corrotina começar, e abre quando ela termina, falha ou é cancelada.
 * Ligue [isRunning] ao botão (`DSButton(loading = …)` ou `enabled = !running`).
 */
public class SingleFlight {
    private val running = MutableStateFlow(false)

    public val isRunning: StateFlow<Boolean> = running.asStateFlow()

    /** Lança [block] em [scope] se nada estiver rodando; devolve `false` quando o toque foi ignorado. */
    public fun launch(scope: CoroutineScope, block: suspend () -> Unit): Boolean = launchJob(scope, block) != null

    /** O mesmo, devolvendo o `Job` para quem precisa esperar ou cancelar; `null` quando o toque foi ignorado. */
    public fun launchJob(scope: CoroutineScope, block: suspend () -> Unit): Job? {
        if (!running.compareAndSet(expect = false, update = true)) return null

        // `invokeOnCompletion` e não `finally`: cobre também o escopo cancelado antes de a corrotina começar.
        return scope.launch { block() }.also { job -> job.invokeOnCompletion { running.value = false } }
    }

    /** O mesmo para quem já está numa corrotina; devolve `null` quando a chamada foi ignorada. */
    public suspend fun <T : Any> run(block: suspend () -> T): T? {
        if (!running.compareAndSet(expect = false, update = true)) return null

        return try {
            block()
        } finally {
            running.value = false
        }
    }
}

/**
 * A mesma trava por chave, para ações de linha (seguir, ativar, excluir um item da lista): cada item tem a sua, e uma
 * linha gravando não trava as outras.
 */
public class KeyedSingleFlight<K : Any> {
    private val keys = MutableStateFlow(emptySet<K>())

    /** As chaves com gravação em andamento. */
    public val running: StateFlow<Set<K>> = keys.asStateFlow()

    public fun launch(scope: CoroutineScope, key: K, block: suspend () -> Unit): Boolean {
        var acquired = false

        keys.update { current ->
            acquired = key !in current

            if (acquired) current + key else current
        }

        if (!acquired) return false

        scope.launch { block() }.invokeOnCompletion { keys.update { it - key } }

        return true
    }
}
