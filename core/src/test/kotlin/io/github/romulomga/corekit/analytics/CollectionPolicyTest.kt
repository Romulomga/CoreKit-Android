package io.github.romulomga.corekit.analytics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionPolicyTest {
    @Test
    fun `only the store build collects by default`() {
        assertTrue(CollectionPolicy.shouldCollect(isDebugBuild = false))
        assertFalse(CollectionPolicy.shouldCollect(isDebugBuild = true))
        assertFalse(CollectionPolicy.shouldCollect(isDebugBuild = false, isAutomatedRun = true))
    }

    @Test
    fun `a debug build collects when the developer opts in`() {
        assertTrue(CollectionPolicy.shouldCollect(isDebugBuild = true, debugOptIn = true))
    }

    @Test
    fun `an automated run never collects, even opted in`() {
        assertFalse(CollectionPolicy.shouldCollect(isDebugBuild = true, isAutomatedRun = true, debugOptIn = true))
        assertFalse(CollectionPolicy.shouldCollect(isDebugBuild = false, isAutomatedRun = true, debugOptIn = true))
    }
}
