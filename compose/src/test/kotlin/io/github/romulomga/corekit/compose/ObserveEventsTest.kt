package io.github.romulomga.corekit.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import io.github.romulomga.corekit.compose.effects.ObserveEvents
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ObserveEventsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun anEventSentInTheBackgroundIsDeliveredOnReturn() {
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED)
        val events = Channel<String>(Channel.BUFFERED)
        val shown = mutableListOf<String>()

        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ObserveEvents(events.receiveAsFlow()) { shown += it }
            }
        }

        events.trySend("saved")
        compose.waitForIdle()

        assertEquals(listOf("saved"), shown)

        compose.runOnUiThread { owner.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        events.trySend("upload failed")
        compose.waitForIdle()

        assertEquals(listOf("saved"), shown)

        compose.runOnUiThread { owner.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()

        assertEquals(listOf("saved", "upload failed"), shown)
    }
}
