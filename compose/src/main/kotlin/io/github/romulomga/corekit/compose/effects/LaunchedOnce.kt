package io.github.romulomga.corekit.compose.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.UUID
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
 * A marca de "já carregou" vive no [owner], o mesmo dono da ViewModel que a carga preenche, e não no estado salvo.
 * Guardada só no estado salvo, ela voltava depois da morte do processo (ou com "Não manter atividades") junto de uma
 * ViewModel nova e vazia: a carga não rodava e a tela ficava presa no esqueleto. Com a marca no dono, as duas somem
 * juntas. O estado salvo guarda só um id deste ponto de chamada, para duas cargas no mesmo dono não dividirem a marca.
 *
 * @param key o que identifica a carga dentro da entrada. Mudou, roda de novo.
 * @param owner o dono da ViewModel que a carga preenche. O padrão é o da entrada (com o
 * `rememberViewModelStoreNavEntryDecorator`) ou o da Activity.
 */
@Composable
public fun LaunchedOnce(
    key: Any?,
    owner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "LaunchedOnce precisa de um LocalViewModelStoreOwner"
    },
    block: suspend () -> Unit
) {
    val current by rememberUpdatedState(block)
    val id = rememberSaveable(key) { UUID.randomUUID().toString() }
    val marks = viewModel(viewModelStoreOwner = owner, key = MARKS_KEY) { LaunchedOnceMarks() }

    LaunchedEffect(id, marks) {
        if (id !in marks.finished) {
            current()

            if (currentCoroutineContext().isActive) {
                marks.finished += id
            }
        }
    }
}

private const val MARKS_KEY = "io.github.romulomga.corekit.compose.effects.LaunchedOnce"

internal class LaunchedOnceMarks : ViewModel() {
    val finished: MutableSet<String> = mutableSetOf()
}
