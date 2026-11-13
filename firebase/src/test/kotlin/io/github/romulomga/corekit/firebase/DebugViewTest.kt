package io.github.romulomga.corekit.firebase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemProperties

@RunWith(RobolectricTestRunner::class)
class DebugViewTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun resetProperties() = ShadowSystemProperties.reset()

    @Test
    fun `off until the DebugView property names this app`() {
        assertFalse(FirebaseMonitoring.isDebugViewEnabled(context))

        ShadowSystemProperties.override(PROPERTY, "com.other.app")

        assertFalse(FirebaseMonitoring.isDebugViewEnabled(context))

        ShadowSystemProperties.override(PROPERTY, context.packageName)

        assertTrue(FirebaseMonitoring.isDebugViewEnabled(context))

        ShadowSystemProperties.override(PROPERTY, ".none.")

        assertFalse(FirebaseMonitoring.isDebugViewEnabled(context))
    }

    private companion object {
        const val PROPERTY = "debug.firebase.analytics.app"
    }
}
