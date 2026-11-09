package io.github.romulomga.corekit.review

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewGateTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")

    @Test
    fun `asks once per version`() {
        assertTrue(ReviewGate.isEligible(currentVersion = "1.1", lastPromptVersion = "1.0"))
        assertFalse(ReviewGate.isEligible(currentVersion = "1.1", lastPromptVersion = "1.1"))
        assertTrue(ReviewGate.isEligible(currentVersion = "1.0", lastPromptVersion = null))
    }

    @Test
    fun `a new version still waits the minimum interval`() {
        val recent = now.minus(89, ChronoUnit.DAYS)
        val old = now.minus(90, ChronoUnit.DAYS)

        assertFalse(ReviewGate.isEligible("1.1", "1.0", lastPromptAt = recent, now = now, minDaysBetween = 90))
        assertTrue(ReviewGate.isEligible("1.1", "1.0", lastPromptAt = old, now = now, minDaysBetween = 90))
    }

    @Test
    fun `without an interval the date does not matter`() {
        assertTrue(ReviewGate.isEligible("1.1", "1.0", lastPromptAt = now, now = now))
    }
}
