package io.github.romulomga.corekit.compose

import androidx.lifecycle.SavedStateHandle
import io.github.romulomga.corekit.compose.state.InstantDraftSerializer
import io.github.romulomga.corekit.compose.state.draft
import java.time.Instant
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedDraftTest {
    @Serializable
    private data class Form(
        val name: String = "",
        val amount: Int = 0,
        @Serializable(with = InstantDraftSerializer::class) val date: Instant? = null
    )

    @Test
    fun whatWasTypedSurvivesANewViewModel() {
        val handle = SavedStateHandle()
        val draft = handle.draft("form", Form.serializer(), Form())

        draft.update { it.copy(name = "Ana", amount = 3, date = Instant.parse("2026-11-09T12:00:00Z")) }

        // O que o sistema guarda e devolve depois da morte do processo.
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = restoredHandle.draft("form", Form.serializer(), Form())

        assertTrue(restored.wasRestored)
        assertEquals(Form("Ana", 3, Instant.parse("2026-11-09T12:00:00Z")), restored.value)
        assertEquals(restored.value, restored.flow.value)
    }

    @Test
    fun anUntouchedDraftIsNotResumed() {
        val handle = SavedStateHandle()

        handle.draft("form", Form.serializer(), Form()).update { it.copy(amount = 7) }

        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = restoredHandle.draft("form", Form.serializer(), Form(amount = 9), restoreIf = { it.name.isNotEmpty() })

        assertFalse(restored.wasRestored)
        assertEquals(Form(amount = 9), restored.value)
        assertFalse(restoredHandle.contains("form"))
    }

    @Test
    fun startsFromTheDefaultWithoutASavedDraft() {
        val draft = SavedStateHandle().draft("form", Form.serializer(), Form(name = "novo"))

        assertFalse(draft.wasRestored)
        assertEquals(Form(name = "novo"), draft.value)
    }

    @Test
    fun clearForgetsTheDraft() {
        val handle = SavedStateHandle()
        val draft = handle.draft("form", Form.serializer(), Form())

        draft.value = Form(name = "Ana")
        draft.clear()

        assertEquals(Form(), draft.value)
        assertFalse(handle.draft("form", Form.serializer(), Form()).wasRestored)
    }

    @Test
    fun aDraftInAnOldShapeIsDropped() {
        val handle = SavedStateHandle(mapOf("form" to "{\"amount\":\"not a number\"}"))
        val draft = handle.draft("form", Form.serializer(), Form())

        assertFalse(draft.wasRestored)
        assertEquals(Form(), draft.value)
    }
}
