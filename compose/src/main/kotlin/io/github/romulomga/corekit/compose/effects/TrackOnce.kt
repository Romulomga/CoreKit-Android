package io.github.romulomga.corekit.compose.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Roda [block] uma vez por visita à tela. Um `LaunchedEffect(Unit)` cru roda de novo a cada recriação da Activity
 * (tema, fonte, idioma, rotação, dobra), e o evento de "apareceu" (visualização de tela, impressão de paywall) sai
 * em dobro; o iOS não tem recriação, então os funis das duas plataformas deixam de bater.
 *
 * "Uma visita" é uma entrada na pilha: sair e entrar de novo conta outra vez; recriar a Activity ou restaurar o
 * processo não conta. Todo evento de "apareceu" passa por aqui.
 *
 * @param key o que identifica a visita dentro da entrada (o nome da tela, a origem do teaser). Mudou, conta de novo.
 */
@Composable
public fun TrackOnce(key: Any?, block: () -> Unit) {
    val current by rememberUpdatedState(block)
    var tracked by rememberSaveable(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        if (!tracked) {
            tracked = true
            current()
        }
    }
}
