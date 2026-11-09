package io.github.romulomga.corekit.compose.effects

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import java.util.UUID

/**
 * As telas que estão à vista agora, pelo id de cada aparição. Mora no processo: sobrevive à recriação da Activity, que
 * é exatamente o que não pode contar de novo.
 */
internal class AppearanceRegistry {
    private val visible = mutableSetOf<String>()

    /** `true` quando a tela acabou de aparecer; `false` quando já estava à vista (a Activity só foi recriada). */
    fun enter(id: String): Boolean = visible.add(id)

    fun leave(id: String) {
        visible.remove(id)
    }
}

private val appearances = AppearanceRegistry()

/**
 * O evento de "a tela apareceu" com a conta do `.onAppear` do iOS: uma vez a cada vez que a tela aparece, inclusive na
 * volta de uma tela empilhada ou de outra aba, e nunca na recriação da Activity (rotação, tema, fonte, dobra), que o
 * iOS não tem. Para o app cujo funil é comparado com o do iOS.
 *
 * Use o [TrackOnce] quando a conta certa é uma vez por entrada na pilha (a volta não conta de novo).
 *
 * @param key mudou, é outra aparição.
 */
@Composable
public fun TrackAppearance(key: Any? = Unit, block: () -> Unit) {
    val activity = LocalActivity.current

    TrackAppearance(key, isRecreating = { activity?.isChangingConfigurations == true }, registry = appearances, block = block)
}

@Composable
internal fun TrackAppearance(key: Any?, isRecreating: () -> Boolean, registry: AppearanceRegistry, block: () -> Unit) {
    val current by rememberUpdatedState(block)
    val recreating by rememberUpdatedState(isRecreating)
    val id = rememberSaveable { UUID.randomUUID().toString() }

    DisposableEffect(id, key) {
        if (registry.enter(id)) current()

        onDispose { if (!recreating()) registry.leave(id) }
    }
}
