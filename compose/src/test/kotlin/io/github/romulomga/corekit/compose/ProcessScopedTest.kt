package io.github.romulomga.corekit.compose.state

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProcessScopedTest {
    @get:Rule
    val compose = createComposeRule()

    /** Na etapa 3, a recriação da Activity (rotação, tema, dobra) não volta à etapa 1. */
    @Test
    fun theStackSurvivesActivityRecreation() {
        val tester = StateRestorationTester(compose)
        lateinit var stack: NavBackStack<NavKey>

        tester.setContent { stack = processScoped("process-1") { rememberNavBackStack(FirstKey) } }
        compose.runOnIdle {
            stack.add(SecondKey)
            stack.add(ThirdKey)
        }

        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(listOf<NavKey>(FirstKey, SecondKey, ThirdKey), stack.toList())
    }

    /** O rascunho morreu com o processo: a pilha salva não pode abrir o fluxo seguinte numa etapa adiantada. */
    @Test
    fun theStackOfADeadProcessIsNotRestored() {
        val tester = StateRestorationTester(compose)
        var process = "process-1"
        lateinit var stack: NavBackStack<NavKey>

        tester.setContent { stack = processScoped(process) { rememberNavBackStack(FirstKey) } }
        compose.runOnIdle { stack.add(SecondKey) }

        process = "process-2"
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(listOf<NavKey>(FirstKey), stack.toList())
    }

    @Test
    fun savedStateFollowsTheProcessToo() {
        val tester = StateRestorationTester(compose)
        var process = "process-1"
        var step = mutableIntStateOf(0)

        tester.setContent { step = processScoped(process) { rememberSaveable { mutableIntStateOf(0) } } }
        compose.runOnIdle { step.intValue = 3 }

        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(3, step.intValue)

        process = "process-2"
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(0, step.intValue)
    }
}

@Serializable
internal data object FirstKey : NavKey

@Serializable
internal data object SecondKey : NavKey

@Serializable
internal data object ThirdKey : NavKey
