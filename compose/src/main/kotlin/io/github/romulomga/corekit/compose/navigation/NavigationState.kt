package io.github.romulomga.corekit.compose.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * Uma pilha por destino de nível superior (aba) e qual delas está na tela. As pilhas e a seleção sobrevivem à
 * recriação da atividade e à morte do processo.
 *
 * Por isso toda chave empilhada precisa conseguir remontar a tela sozinha: leve na chave só ids, e nunca dependa de
 * um estado em memória (singleton de fluxo, rascunho) que a morte do processo apaga e a pilha restaurada não.
 */
@Stable
public class NavigationState(
    public val startKey: NavKey,
    public val topLevelKeys: List<NavKey>,
    public val backStacks: Map<NavKey, NavBackStack<NavKey>>,
    selection: MutableIntState
) {
    private var selectedIndex by selection

    public var topLevelKey: NavKey
        get() = topLevelKeys[selectedIndex]
        set(value) {
            selectedIndex = topLevelKeys.indexOf(value).also { require(it >= 0) { "$value não é um destino de nível superior" } }
        }

    public val currentStack: NavBackStack<NavKey> get() = backStacks.getValue(topLevelKey)

    /** As pilhas que compõem a tela: a de início por baixo, para o voltar na raiz de outra aba levar a ela. */
    public val stacksInUse: List<NavKey> get() = if (topLevelKey == startKey) listOf(startKey) else listOf(startKey, topLevelKey)
}

@Composable
public fun rememberNavigationState(startKey: NavKey, topLevelKeys: List<NavKey>): NavigationState {
    val selection = rememberSaveable { mutableIntStateOf(topLevelKeys.indexOf(startKey)) }
    val backStacks = topLevelKeys.associateWith { topLevelKey -> key(topLevelKey) { rememberNavBackStack(topLevelKey) } }

    return remember(startKey, topLevelKeys) { NavigationState(startKey, topLevelKeys, backStacks, selection) }
}

/**
 * As entradas que o `NavDisplay` mostra. Toda pilha é decorada, mesmo a de uma aba fora da tela: assim o estado
 * salvo e os ViewModels dela continuam vivos até a tela sair da pilha.
 */
@Composable
public fun NavigationState.toEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
    val decorated = backStacks.mapValues { (topLevelKey, stack) ->
        key(topLevelKey) {
            val decorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>()
            )
            rememberDecoratedNavEntries(stack, decorators, entryProvider)
        }
    }

    return stacksInUse.flatMap { decorated.getValue(it) }
}
