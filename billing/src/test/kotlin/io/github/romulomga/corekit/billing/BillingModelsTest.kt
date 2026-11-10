package io.github.romulomga.corekit.billing

import java.time.Instant
import java.time.Period
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillingModelsTest {
    private val paid = PricingPhase(49_900_000, "P1Y", "R$ 49,90", "BRL")
    private val free = PricingPhase(0, "P1W", "Grátis", "BRL")

    @Test
    fun `picks the trial offer when the account still has it`() {
        val base = SubscriptionOffer("base", null, listOf(paid))
        val trial = SubscriptionOffer("trial", "free-week", listOf(free, paid))

        assertEquals(trial, SubscriptionOffer.pick(listOf(base, trial)))
        assertEquals(base, SubscriptionOffer.pick(listOf(base)))
        assertNull(SubscriptionOffer.pick(emptyList()))
    }

    @Test
    fun `the trial is the leading free phase and the price is the last one`() {
        val trial = SubscriptionOffer("trial", "free-week", listOf(free, paid))

        assertEquals(Period.ofWeeks(1), trial.freeTrial)
        assertEquals(paid, trial.recurringPhase)
        assertNull(SubscriptionOffer("base", null, listOf(paid)).freeTrial)
        assertNull(SubscriptionOffer.parsePeriod("soon"))
    }

    @Test
    fun `first charge follows the calendar`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val start = Instant.parse("2026-01-31T15:00:00Z")

        assertEquals(Instant.parse("2026-02-28T15:00:00Z"), Period.ofMonths(1).firstChargeAt(start, zone))
        assertEquals(7, Period.ofWeeks(1).approximateDays())
        assertEquals(30, Period.ofMonths(1).approximateDays())
    }
}
