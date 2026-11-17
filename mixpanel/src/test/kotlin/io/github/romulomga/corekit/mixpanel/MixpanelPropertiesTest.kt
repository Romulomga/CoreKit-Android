package io.github.romulomga.corekit.mixpanel

import org.junit.Assert.assertEquals
import org.junit.Test

/** O que cada chamada entrega ao Mixpanel, igual ao motor do iOS. */
class MixpanelPropertiesTest {
    private enum class Kind { RECEIPT }

    @Test
    fun `types survive and anything else becomes text`() {
        val properties = mixpanelProperties(mapOf("count" to 3, "total" to 49.9, "has_note" to true, "name" to "x", "kind" to Kind.RECEIPT))

        assertEquals(mapOf("count" to 3, "total" to 49.9, "has_note" to true, "name" to "x", "kind" to "RECEIPT"), properties)
    }

    @Test
    fun `a screen goes with Firebase's names`() {
        assertEquals("screen_view", SCREEN_VIEW)
        assertEquals(mapOf("screen_name" to "home"), screenViewProperties("home"))
    }
}
