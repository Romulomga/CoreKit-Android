package io.github.romulomga.corekit.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.romulomga.corekit.compose.effects.TrackOnce
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackOnceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun activityRecreationDoesNotCountAgain() {
        val tester = StateRestorationTester(compose)
        var views = 0

        tester.setContent { TrackOnce("home") { views += 1 } }
        compose.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(1, views)
    }

    @Test
    fun aNewKeyCountsAgain() {
        var screen by mutableStateOf("home")
        val views = mutableListOf<String>()

        compose.setContent { TrackOnce(screen) { views += screen } }
        compose.waitForIdle()

        screen = "detail"
        compose.waitForIdle()

        assertEquals(listOf("home", "detail"), views)
    }
}
