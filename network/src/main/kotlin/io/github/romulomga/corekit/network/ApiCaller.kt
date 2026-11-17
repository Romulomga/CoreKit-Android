package io.github.romulomga.corekit.network

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * O limite entre o transporte e o erro do app. Todo repositório chama a rede por aqui: cancelamento atravessa intacto,
 * qualquer outra falha vira o erro do app por [map], e uma sessão expirada chama [onSessionExpired] (o logout central:
 * tela nenhuma trata 401).
 *
 * Um pedido cancelado no meio (o fim da sessão, uma busca mais nova) não avisa nada, mesmo que a falha chegue depois:
 * a resposta não é de sessão nenhuma, e um 401 atrasado derrubaria o próximo usuário.
 *
 * Gêmeo por conceito do `failureObserver` do `NetworkService` do iOS.
 */
public class ApiCaller<E : Throwable>(
    private val map: (Throwable) -> E,
    private val isSessionExpired: (E) -> Boolean,
    private val onSessionExpired: () -> Unit
) {
    public suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        currentCoroutineContext().ensureActive()

        val mapped = map(error)

        if (isSessionExpired(mapped)) onSessionExpired()

        throw mapped
    }
}
