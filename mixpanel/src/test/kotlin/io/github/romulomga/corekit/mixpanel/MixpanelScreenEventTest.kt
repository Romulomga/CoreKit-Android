package io.github.romulomga.corekit.mixpanel

import org.junit.Assert.assertEquals
import org.junit.Test

/** Como uma tela chega ao Mixpanel, lido sem o Mixpanel ligado. Gêmeo do `MixpanelScreenEventTests` do iOS. */
class MixpanelScreenEventTest {
    @Test
    fun `a screen goes with Firebase's names by default`() {
        assertEquals("screen_view", MixpanelScreenEvent.STANDARD.event)
        assertEquals(mapOf("screen_name" to "home"), MixpanelScreenEvent.STANDARD.properties("home"))
    }

    @Test
    fun `an app keeps the shape its reports read`() {
        val legacy = MixpanelScreenEvent(event = "page_viewed", nameKey = "name_of_the_page_viewed")

        assertEquals("page_viewed", legacy.event)
        assertEquals(mapOf("name_of_the_page_viewed" to "profile"), legacy.properties("profile"))
    }
}
