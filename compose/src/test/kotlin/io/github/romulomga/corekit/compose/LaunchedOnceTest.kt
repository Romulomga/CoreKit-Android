package io.github.romulomga.corekit.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.romulomga.corekit.compose.effects.LaunchedOnce
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LaunchedOnceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aFinishedLoadDoesNotRunAgainAfterRecreation() {
        val tester = StateRestorationTester(compose)
        var loads = 0

        tester.setContent { LaunchedOnce("feed") { loads += 1 } }
        compose.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(1, loads)
    }

    @Test
    fun returningToAScreenThatLoadedDoesNotLoadAgain() {
        var shown by mutableStateOf(true)
        var loads = 0

        compose.setContent {
            val entries = rememberSaveableStateHolder()

            if (shown) {
                entries.SaveableStateProvider("feed") { LaunchedOnce("feed") { loads += 1 } }
            }
        }
        compose.waitForIdle()

        shown = false
        compose.waitForIdle()

        shown = true
        compose.waitForIdle()

        assertEquals(1, loads)
    }

    @Test
    fun aLoadCancelledByLeavingRunsAgainOnReturn() {
        var shown by mutableStateOf(true)
        var loads = 0
        var hangs = true

        compose.setContent {
            val entries = rememberSaveableStateHolder()

            if (shown) {
                entries.SaveableStateProvider("feed") {
                    LaunchedOnce("feed") {
                        loads += 1
                        if (hangs) awaitCancellation()
                    }
                }
            }
        }
        compose.waitForIdle()

        shown = false
        compose.waitForIdle()

        hangs = false
        shown = true
        compose.waitForIdle()

        assertEquals(2, loads)
    }

    @Test
    fun aNewKeyLoadsAgain() {
        var key by mutableStateOf("a")
        val loads = mutableListOf<String>()

        compose.setContent { LaunchedOnce(key) { loads += key } }
        compose.waitForIdle()

        key = "b"
        compose.waitForIdle()

        assertEquals(listOf("a", "b"), loads)
    }

    @Test
    fun aClearedStoreWithRestoredStateLoadsAgain() {
        val tester = StateRestorationTester(compose)
        var owner by mutableStateOf(StoreOwner())
        var loads = 0

        tester.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                LaunchedOnce("feed") { loads += 1 }
            }
        }
        compose.waitForIdle()

        owner.viewModelStore.clear()
        owner = StoreOwner()
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(2, loads)
    }

    @Test
    fun twoLoadsWithTheSameKeyInOneOwnerBothRun() {
        val loads = mutableListOf<String>()

        compose.setContent {
            LaunchedOnce(Unit) { loads += "feed" }
            LaunchedOnce(Unit) { loads += "profile" }
        }
        compose.waitForIdle()

        assertEquals(listOf("feed", "profile"), loads)
    }

    @Test
    fun anEntryWithItsOwnStoreDoesNotLoadAgainOnReturn() {
        val owner = StoreOwner()
        var shown by mutableStateOf(true)
        var loads = 0

        compose.setContent {
            val entries = rememberSaveableStateHolder()

            if (shown) {
                entries.SaveableStateProvider("feed") {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        LaunchedOnce("feed") { loads += 1 }
                    }
                }
            }
        }
        compose.waitForIdle()

        shown = false
        compose.waitForIdle()

        shown = true
        compose.waitForIdle()

        assertEquals(1, loads)
    }

    @Test
    fun anEntryPoppedAndPushedAgainLoadsAgain() {
        var owner = StoreOwner()
        var shown by mutableStateOf(true)
        var loads = 0
        lateinit var pop: () -> Unit

        compose.setContent {
            val entries = rememberSaveableStateHolder()
            pop = { entries.removeState("feed") }

            if (shown) {
                entries.SaveableStateProvider("feed") {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        LaunchedOnce("feed") { loads += 1 }
                    }
                }
            }
        }
        compose.waitForIdle()

        shown = false
        compose.waitForIdle()

        pop()
        owner.viewModelStore.clear()
        owner = StoreOwner()
        shown = true
        compose.waitForIdle()

        assertEquals(2, loads)
    }

    private class StoreOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
}
