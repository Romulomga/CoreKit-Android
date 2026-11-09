package io.github.romulomga.corekit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Quanto o estado de tela continua coletando depois de a tela sair: cobre a recriação da Activity sem recarregar. */
public const val SCREEN_STOP_TIMEOUT_MILLIS: Long = 5_000L

/** O `stateIn` de um estado de tela: coleta enquanto há tela olhando, e mais um pouco para atravessar a recriação. */
public fun <T> Flow<T>.stateInScreen(scope: CoroutineScope, initial: T): StateFlow<T> =
    stateIn(scope, SharingStarted.WhileSubscribed(SCREEN_STOP_TIMEOUT_MILLIS), initial)
