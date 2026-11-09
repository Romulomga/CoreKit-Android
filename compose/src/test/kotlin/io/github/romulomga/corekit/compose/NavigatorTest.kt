package io.github.romulomga.corekit.compose

import androidx.compose.runtime.mutableIntStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.romulomga.corekit.compose.navigation.NavigationState
import io.github.romulomga.corekit.compose.navigation.Navigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {
    private data object Home : NavKey

    private data object Settings : NavKey

    private data class Detail(val id: String) : NavKey

    private val state = NavigationState(
        startKey = Home,
        topLevelKeys = listOf(Home, Settings),
        backStacks = mapOf(Home to NavBackStack<NavKey>(Home), Settings to NavBackStack<NavKey>(Settings)),
        selection = mutableIntStateOf(0)
    )

    private val navigator = Navigator(state)

    @Test
    fun aDoubleTapOpensTheScreenOnce() {
        navigator.navigate(Detail("a"))
        navigator.navigate(Detail("a"))

        assertEquals(listOf(Home, Detail("a")), state.currentStack.toList())
    }

    @Test
    fun aDifferentKeyStillStacks() {
        navigator.navigate(Detail("a"))
        navigator.navigate(Detail("b"))

        assertEquals(listOf(Home, Detail("a"), Detail("b")), state.currentStack.toList())
    }

    @Test
    fun aTopLevelKeySwitchesTabsInsteadOfStacking() {
        navigator.navigate(Settings)

        assertEquals(Settings, state.topLevelKey)
        assertTrue(navigator.lastChangeWasTabSwitch)
        assertEquals(listOf(Settings), state.currentStack.toList())
    }

    @Test
    fun backPopsThenReturnsToTheStartTab() {
        navigator.navigate(Settings)
        navigator.navigate(Detail("a"))

        navigator.goBack()

        assertEquals(listOf(Settings), state.currentStack.toList())
        assertFalse(navigator.lastChangeWasTabSwitch)

        navigator.goBack()

        assertEquals(Home, state.topLevelKey)
    }

    @Test
    fun beforeRemoveCanHoldTheBack() {
        var release: (() -> Unit)? = null
        val guarded = Navigator(state) { _, then -> release = then }

        guarded.navigate(Detail("a"))
        guarded.goBack()

        assertEquals(listOf(Home, Detail("a")), state.currentStack.toList())

        release?.invoke()

        assertEquals(listOf(Home), state.currentStack.toList())
    }

    @Test
    fun replaceStackLeavesRootPlusTheNewKey() {
        navigator.navigate(Detail("form"))
        navigator.replaceStack(Home, Detail("issued"))

        assertEquals(listOf(Home, Detail("issued")), state.currentStack.toList())
    }

    @Test
    fun popToRootKeepsOnlyTheRoot() {
        navigator.navigate(Detail("a"))
        navigator.navigate(Detail("b"))
        navigator.popToRoot()

        assertEquals(listOf(Home), state.currentStack.toList())
    }
}
