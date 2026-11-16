package io.github.romulomga.corekit.compose.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Roda o [block] suspenso na primeira vez que a tela aparece, e não roda de novo depois que ele terminou.
 *
 * A carga inicial de uma tela ia num `LaunchedEffect(viewModel) { viewModel.load() }`, que carrega de novo a cada volta
 * à tela (voltar de outra tela da pilha, trocar de aba) e a cada recriação da Activity. Aqui ela roda uma vez por
 * entrada na pilha. Sair antes de terminar cancela a carga, e a próxima aparição roda de novo: uma tela abandonada no
 * meio da carga nunca volta vazia. Puxar para atualizar e tentar de novo chamam a ViewModel direto. Gêmeo do
 * `onFirstAppear` assíncrono do CoreKit iOS.
 *
 * @param key o que identifica a carga dentro da entrada. Mudou, roda de novo.
 */
@Composable
public fun LaunchedOnce(key: Any?, block: suspend () -> Unit) {
    val current by rememberUpdatedState(block)
    var finished by rememberSaveable(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        if (!finished) {
            current()

            if (currentCoroutineContext().isActive) {
                finished = true
            }
        }
    }
}
