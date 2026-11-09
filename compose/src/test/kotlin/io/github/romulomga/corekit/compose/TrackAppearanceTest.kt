package io.github.romulomga.corekit.compose.effects

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TrackAppearanceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun activityRecreationDoesNotCountAgain() {
        val tester = StateRestorationTester(compose)
        val registry = AppearanceRegistry()
        var views = 0

        tester.setContent { TrackAppearance(Unit, isRecreating = { true }, registry = registry) { views += 1 } }
        compose.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(1, views)
    }

    @Test
    fun comingBackFromAPushedScreenCountsAgainLikeOnAppear() {
        val registry = AppearanceRegistry()
        var onTop by mutableStateOf(true)
        var views = 0

        compose.setContent {
            val holder = rememberSaveableStateHolder()

            // Como a entrada do Navigation 3: fora da tela, o estado salvo dela fica guardado.
            if (onTop) {
                holder.SaveableStateProvider("screen") {
                    TrackAppearance(Unit, isRecreating = { false }, registry = registry) { views += 1 }
                }
            }
        }
        compose.waitForIdle()

        onTop = false
        compose.waitForIdle()
        onTop = true
        compose.waitForIdle()

        assertEquals(2, views)
    }

    @Test
    fun aRestoredProcessCountsOnce() {
        val tester = StateRestorationTester(compose)
        var registry = AppearanceRegistry()
        var views = 0

        tester.setContent { TrackAppearance(Unit, isRecreating = { true }, registry = registry) { views += 1 } }
        compose.waitForIdle()

        // O processo novo: o estado salvo volta, a memória não.
        registry = AppearanceRegistry()
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(2, views)
    }

    @Test
    fun aNewKeyIsAnotherAppearance() {
        val registry = AppearanceRegistry()
        var minimum by mutableStateOf(10)
        val views = mutableListOf<Int>()

        compose.setContent { TrackAppearance(minimum, isRecreating = { false }, registry = registry) { views += minimum } }
        compose.waitForIdle()

        minimum = 11
        compose.waitForIdle()

        assertEquals(listOf(10, 11), views)
    }
}
