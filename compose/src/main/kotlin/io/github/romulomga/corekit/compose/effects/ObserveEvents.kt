package io.github.romulomga.corekit.compose.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Coleta os eventos de uso único do ViewModel (snackbar, fechar, navegar) só com a tela à vista. Um
 * `LaunchedEffect { flow.collect { } }` cru continua coletando com o app em segundo plano: o aviso de erro passa
 * para ninguém, e na volta a tela parece ter terminado bem.
 *
 * O ViewModel expõe os eventos por `Channel(Channel.BUFFERED).receiveAsFlow()`: o que chega com a tela parada
 * espera no canal e é entregue na volta.
 */
@Composable
public fun <T> ObserveEvents(events: Flow<T>, minActiveState: Lifecycle.State = Lifecycle.State.STARTED, onEvent: suspend (T) -> Unit) {
    val current by rememberUpdatedState(onEvent)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(events, lifecycleOwner, minActiveState) {
        lifecycleOwner.repeatOnLifecycle(minActiveState) {
            // `immediate`: o evento é tratado no mesmo quadro em que chega, sem perder um emitido entre dois despachos.
            withContext(Dispatchers.Main.immediate) {
                events.collect { current(it) }
            }
        }
    }
}
