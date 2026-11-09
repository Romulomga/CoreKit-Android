package io.github.romulomga.corekit

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Os dispatchers do app num objeto só, para chegarem por injeção. `Dispatchers.IO` fixo no código obriga o teste a
 * esperar em relógio de parede; com este objeto o teste entrega um dispatcher de teste nos três.
 */
public data class AppDispatchers(
    val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    val default: CoroutineDispatcher = Dispatchers.Default,
    val io: CoroutineDispatcher = Dispatchers.IO
) {
    public companion object {
        /** Os três no mesmo dispatcher: o de teste. */
        public fun all(dispatcher: CoroutineDispatcher): AppDispatchers = AppDispatchers(dispatcher, dispatcher, dispatcher)
    }
}
