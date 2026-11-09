package io.github.romulomga.corekit.compose.state

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json

/**
 * O rascunho de um formulário, guardado no [SavedStateHandle] do ViewModel: o que a pessoa digitou sobrevive à
 * recriação da Activity e à morte do processo em segundo plano (o iOS quase nunca mata um app suspenso enquanto a
 * pessoa confere a caixa do remédio ou lê um SMS; o Android mata).
 *
 * Regra: formulário e folha aberta moram no ViewModel, num [SavedDraft]; a tela nunca guarda em `remember` o que o
 * usuário digitou ou abriu.
 *
 * @param default o formulário em branco: o valor sem rascunho guardado e depois de [clear].
 */
public class SavedDraft<T>(
    private val handle: SavedStateHandle,
    private val key: String,
    private val serializer: KSerializer<T>,
    private val default: T
) {
    private val restored: T? = restore()

    /** Se o valor inicial veio de um rascunho guardado (processo restaurado), e não de [default]. */
    public val wasRestored: Boolean = restored != null

    private val state = MutableStateFlow(restored ?: default)

    public val flow: StateFlow<T> = state.asStateFlow()

    public var value: T
        get() = state.value
        set(value) {
            state.value = value
            store(value)
        }

    public inline fun update(transform: (T) -> T) {
        value = transform(value)
    }

    /** Gravou ou descartou: volta ao formulário em branco e apaga o rascunho guardado. */
    public fun clear() {
        state.value = default
        handle.remove<String>(key)
    }

    private fun store(value: T) {
        handle[key] = draftJson.encodeToString(serializer, value)
    }

    /** O rascunho guardado, ou `null` sem rascunho (ou com um de formato antigo, que não vale a pena recuperar). */
    private fun restore(): T? {
        val text = handle.get<String>(key) ?: return null

        return try {
            draftJson.decodeFromString(serializer, text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/** Um [SavedDraft] neste [SavedStateHandle]; [key] é único dentro do ViewModel. */
public fun <T> SavedStateHandle.draft(key: String, serializer: KSerializer<T>, default: T): SavedDraft<T> =
    SavedDraft(this, key, serializer, default)

/** O rascunho que pode não existir (o registro em edição, o documento duplicado): começa em `null`. */
public fun <T : Any> SavedStateHandle.optionalDraft(key: String, serializer: KSerializer<T>): SavedDraft<T?> =
    SavedDraft(this, key, serializer.nullable, null)

private val draftJson = Json { ignoreUnknownKeys = true }
