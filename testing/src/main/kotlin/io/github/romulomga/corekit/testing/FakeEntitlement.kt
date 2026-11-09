package io.github.romulomga.corekit.testing

import io.github.romulomga.corekit.Entitlement
import io.github.romulomga.corekit.EntitlementState
import kotlinx.coroutines.flow.MutableStateFlow

/** O direito ao Pro que o teste escolhe; comece em [EntitlementState.UNKNOWN] para testar a tela antes da leitura. */
public class FakeEntitlement(initial: EntitlementState = EntitlementState.FREE) : Entitlement {
    override val state: MutableStateFlow<EntitlementState> = MutableStateFlow(initial)

    public fun set(value: EntitlementState) {
        state.value = value
    }
}
