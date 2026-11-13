package io.github.romulomga.corekit.firebase

import com.google.firebase.analytics.FirebaseAnalytics.Param
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirebaseParametersTest {
    @Test
    fun `a screen view names the screen in the class too`() {
        val parameters = screenViewParameters("weight")

        assertEquals("weight", parameters.getString(Param.SCREEN_NAME))
        assertEquals("weight", parameters.getString(Param.SCREEN_CLASS))
    }
}
