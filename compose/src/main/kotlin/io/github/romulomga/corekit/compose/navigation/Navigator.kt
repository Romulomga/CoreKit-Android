package io.github.romulomga.corekit.compose.navigation

import androidx.navigation3.runtime.NavKey

/** Muda o [NavigationState]. Como cada destino aparece (tela, painel, diálogo) é metadado da entrada, não argumento daqui. */
public class Navigator(
    public val state: NavigationState,
    /** Roda antes de a chave sair da pilha e chama `then` quando pode sair: um painel aberto desce primeiro. */
    private val beforeRemove: (key: NavKey, then: () -> Unit) -> Unit = { _, then -> then() }
) {
    public val currentKey: NavKey get() = state.currentStack.last()

    /** Se a última mudança trocou de aba: a transição das abas é o fade-through, a das telas é o eixo X. */
    public var lastChangeWasTabSwitch: Boolean = false
        private set

    /**
     * Empilha [key] na aba aberta; uma chave de aba troca de aba. A chave igual à do topo é ignorada: dois toques
     * rápidos na mesma linha abriam a tela duas vezes (dois "voltar", duas cargas, chave repetida no estado salvo).
     */
    public fun navigate(key: NavKey) {
        when {
            key in state.backStacks -> navigateToTopLevel(key)

            state.currentStack.lastOrNull() == key -> Unit

            else -> {
                lastChangeWasTabSwitch = false
                state.currentStack.add(key)
            }
        }
    }

    public fun navigateToTopLevel(key: NavKey) {
        lastChangeWasTabSwitch = key != state.topLevelKey
        state.topLevelKey = key
    }

    /** O voltar do sistema: tira a tela de cima; na raiz de outra aba, volta para a aba de início. */
    public fun goBack() {
        val stack = state.currentStack

        when {
            stack.size > 1 -> {
                val top = stack.last()

                beforeRemove(top) {
                    if (stack.lastOrNull() == top) {
                        lastChangeWasTabSwitch = false
                        stack.removeAt(stack.lastIndex)
                    }
                }
            }

            state.topLevelKey != state.startKey -> navigateToTopLevel(state.startKey)
        }
    }

    /** Volta para a raiz da aba aberta: tocar na aba já selecionada, como no iPhone. */
    public fun popToRoot() {
        lastChangeWasTabSwitch = false

        val stack = state.currentStack

        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Troca a pilha de [topLevelKey] por raiz + [key]: voltar da tela nova leva à raiz, não ao fluxo que a criou. */
    public fun replaceStack(topLevelKey: NavKey, key: NavKey) {
        navigateToTopLevel(topLevelKey)
        lastChangeWasTabSwitch = false

        val stack = state.currentStack

        stack.add(key)

        while (stack.size > 2) stack.removeAt(1)
    }
}
