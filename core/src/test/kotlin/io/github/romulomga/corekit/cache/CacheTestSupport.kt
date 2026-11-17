package io.github.romulomga.corekit.cache

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** A falha do app, como um repositório a nomearia. */
sealed class TestFailure : Exception() {
    data object Offline : TestFailure()

    data object Server : TestFailure()

    /** O que a camada de rede do app lança num request cancelado: [map] a transforma em `null`. */
    data object AppCancelled : TestFailure()

    companion object {
        fun map(error: Exception): TestFailure? = when (error) {
            AppCancelled -> null
            is TestFailure -> error
            else -> Server
        }
    }
}

/** Um portão onde a chamada do dublê espera até o teste abrir: exercita o que acontece durante um request. */
class FakeGate {
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

/** Uma chamada configurável de um dublê: a resposta, os argumentos de cada chamada e um [gate] para segurá-la. */
class FakeCall<A, R>(answer: suspend (A) -> R) {
    constructor(value: R) : this({ value })

    @Volatile private var answer: suspend (A) -> R = answer

    private val recorded = mutableListOf<A>()

    val gate = FakeGate()

    val calls: List<A> get() = synchronized(recorded) { recorded.toList() }

    val count: Int get() = synchronized(recorded) { recorded.size }

    fun returns(value: R) {
        answer = { value }
    }

    fun fails(error: Throwable) {
        answer = { throw error }
    }

    fun answers(block: suspend (A) -> R) {
        answer = block
    }

    suspend operator fun invoke(args: A): R {
        synchronized(recorded) { recorded += args }
        gate.pass()

        return answer(args)
    }
}

/** O escopo "do app" num teste: roda na hora, no agendador do teste, e morre com ele. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.eagerBackground(): CoroutineScope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
