package io.github.romulomga.corekit

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * O direito ao Pro com o estado "ainda não sei". Um `Boolean` que nasce `false` faz quem paga ver o teaser (e contar
 * uma impressão de paywall) até a leitura do direito guardado terminar.
 */
public enum class EntitlementState {
    /** O direito guardado ainda não foi lido: não desenhe gate de Pro nem conte impressão. */
    UNKNOWN,
    FREE,
    PRO;

    public val isPro: Boolean get() = this == PRO

    public val isResolved: Boolean get() = this != UNKNOWN
}

/** A única porta para "é Pro?". Telas e ViewModels dependem daqui, nunca do billing. */
public interface Entitlement {
    public val state: StateFlow<EntitlementState>

    /** `true` só com o Pro confirmado; [EntitlementState.UNKNOWN] conta como `false`. */
    public val isPro: Boolean get() = state.value.isPro
}

/** Suspende até o direito guardado ser lido: é o que a fase de carregamento do app espera antes de mostrar a primeira tela. */
public suspend fun Entitlement.awaitResolved(): EntitlementState = state.first { it.isResolved }
