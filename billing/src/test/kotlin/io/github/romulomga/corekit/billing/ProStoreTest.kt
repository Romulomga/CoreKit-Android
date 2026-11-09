package io.github.romulomga.corekit.billing

import android.app.Activity
import io.github.romulomga.corekit.EntitlementState
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProStoreTest {
    private val yearly = ProPlan("pro.yearly", ProPlanType.SUBSCRIPTION, "yearly")
    private val lifetime = ProPlan("pro.lifetime", ProPlanType.LIFETIME, "lifetime")
    private val plans = listOf(yearly, lifetime)
    private val memory = FakeMemory()
    private val observer = RecordingObserver()
    private val gateway = FakeBillingGateway()

    private fun store() = ProStore(gateway, plans, memory, observer)

    private fun purchase(productId: String, token: String = "token", purchased: Boolean = true, acknowledged: Boolean = true) =
        PlayPurchase(token, listOf(productId), purchased, acknowledged, isAutoRenewing = true, purchasedAt = Instant.EPOCH)

    private fun product(plan: ProPlan) = ProProduct(plan, "R$ 49,90", 49_900_000, "BRL")

    @Test
    fun `starts unknown and resolves from the Play answer`() = runTest {
        val store = store()

        assertEquals(EntitlementState.UNKNOWN, store.state.value)

        gateway.purchases = listOf(purchase(yearly.productId))
        store.start()

        assertEquals(EntitlementState.PRO, store.state.value)
        assertEquals(EntitlementKind.SUBSCRIPTION, store.kind.value)
        assertEquals(EntitlementKind.SUBSCRIPTION, memory.saved)
    }

    @Test
    fun `a confirmed empty answer is remembered as free`() = runTest {
        val store = store()

        store.start()

        assertEquals(EntitlementState.FREE, store.state.value)
        assertEquals(EntitlementKind.NONE, memory.saved)
    }

    @Test
    fun `a failed query keeps the remembered Pro`() = runTest {
        memory.saved = EntitlementKind.LIFETIME
        gateway.purchases = null

        val store = store()

        store.start()

        assertEquals(EntitlementState.PRO, store.state.value)
        assertEquals(EntitlementKind.LIFETIME, memory.saved)
        assertEquals(1, observer.keptWithoutAnswer)

        assertFalse(store.refresh())
        assertEquals(1, observer.keptWithoutAnswer)
    }

    @Test
    fun `the remembered right is applied before the Play answers`() = runTest {
        val gate = CompletableDeferred<Unit>()

        memory.saved = EntitlementKind.SUBSCRIPTION
        gateway.purchases = listOf(purchase(yearly.productId))
        gateway.beforePurchasesAnswer = { gate.await() }

        val store = store()
        val start = launch { store.start() }

        runCurrent()

        assertEquals(EntitlementState.PRO, store.state.value)

        gate.complete(Unit)
        start.join()
    }

    @Test
    fun `without memory a slow Play resolves as free after the timeout and Pro when it answers`() = runTest {
        val gate = CompletableDeferred<Unit>()

        gateway.purchases = listOf(purchase(lifetime.productId))
        gateway.beforePurchasesAnswer = { gate.await() }

        val store = store()
        val start = launch { store.start() }

        runCurrent()

        assertEquals(EntitlementState.UNKNOWN, store.state.value)

        advanceTimeBy(3_001)

        assertEquals(EntitlementState.FREE, store.state.value)
        assertNull(memory.saved)

        gate.complete(Unit)
        start.join()

        assertEquals(EntitlementState.PRO, store.state.value)
        assertEquals(EntitlementKind.LIFETIME, memory.saved)
    }

    @Test
    fun `without memory a failed query resolves as free without remembering it`() = runTest {
        gateway.purchases = null

        val store = store()

        store.start()

        assertEquals(EntitlementState.FREE, store.state.value)
        assertNull(memory.saved)
    }

    @Test
    fun `lifetime wins over a subscription and foreign products are ignored`() = runTest {
        gateway.purchases = listOf(purchase(yearly.productId, "a"), purchase(lifetime.productId, "b"), purchase("other.app", "c"))

        val store = store()

        store.start()

        assertEquals(EntitlementKind.LIFETIME, store.kind.value)
        assertEquals(listOf("a", "b"), observer.confirmed.single().map { it.purchaseToken })
    }

    @Test
    fun `a pending purchase gives no right`() = runTest {
        gateway.purchases = listOf(purchase(yearly.productId, purchased = false))

        val store = store()

        store.start()

        assertEquals(EntitlementState.FREE, store.state.value)
    }

    @Test
    fun `unacknowledged purchases are acknowledged`() = runTest {
        gateway.purchases = listOf(purchase(yearly.productId, "new", acknowledged = false), purchase(lifetime.productId, "old"))

        store().start()

        assertEquals(listOf("new"), gateway.acknowledged)
    }

    @Test
    fun `a purchase updates the right and reports the purchase before finishing`() = runTest {
        val store = store()

        store.start()

        val bought = purchase(yearly.productId, "bought", acknowledged = false)

        gateway.purchases = listOf(bought)
        gateway.nextPurchase = BillingUpdate(BillingUpdateCode.OK, listOf(bought))

        val outcome = store.purchase(Activity(), product(yearly))

        assertEquals(PurchaseResult.PURCHASED, outcome.result)
        assertEquals("bought", outcome.purchase?.purchaseToken)
        assertEquals(EntitlementState.PRO, store.state.value)
        assertEquals(listOf("launching", "purchased", "finished PURCHASED"), observer.purchaseSteps)
        assertTrue("bought" in gateway.acknowledged)
    }

    @Test
    fun `a cancelled purchase changes nothing`() = runTest {
        val store = store()

        store.start()

        val outcome = store.purchase(Activity(), product(yearly))

        assertEquals(PurchaseResult.CANCELLED, outcome.result)
        assertEquals(EntitlementState.FREE, store.state.value)
        assertEquals(listOf("launching", "finished CANCELLED"), observer.purchaseSteps)
    }

    @Test
    fun `already owned asks the Play again`() = runTest {
        val store = store()

        store.start()

        gateway.purchases = listOf(purchase(lifetime.productId))
        gateway.nextPurchase = BillingUpdate(BillingUpdateCode.ALREADY_OWNED)

        assertEquals(PurchaseResult.ALREADY_OWNED, store.purchase(Activity(), product(lifetime)).result)
        assertEquals(EntitlementState.PRO, store.state.value)
    }

    @Test
    fun `a pending purchase that lands out of flow gives the right`() = runTest {
        val store = store()

        store.start()

        val landed = purchase(yearly.productId, "landed", acknowledged = false)

        gateway.purchases = listOf(landed)
        gateway.deliverOutOfFlow(BillingUpdate(BillingUpdateCode.OK, listOf(landed)))

        assertEquals(EntitlementState.PRO, store.state.value)
        assertEquals(listOf("purchased", "finished PURCHASED"), observer.purchaseSteps)
    }

    @Test
    fun `restore offline with nothing found is not an answer`() = runTest {
        val store = store()

        store.start()
        gateway.online = false

        assertFalse(store.restore())

        gateway.online = true

        assertTrue(store.restore())

        gateway.purchases = null

        assertFalse(store.restore())
    }

    @Test
    fun `forced Pro survives the Play saying there is nothing`() = runTest {
        val store = store()

        store.forcePro()
        store.start()

        assertEquals(EntitlementState.PRO, store.state.value)
        assertEquals(EntitlementKind.LIFETIME, store.kind.value)
        assertEquals(0, gateway.purchaseQueries)
    }

    @Test
    fun `products are sorted by price and an empty answer is a failure`() = runTest {
        val store = store()

        store.loadProducts()

        assertEquals(ProductsState.FAILED, store.productsState.value)
        assertEquals(listOf("empty"), observer.productFailures)

        gateway.products = listOf(product(lifetime).copy(priceMicros = 79_900_000), product(yearly))
        store.reloadProductsIfFailed()

        assertEquals(ProductsState.LOADED, store.productsState.value)
        assertEquals(listOf(yearly, lifetime), store.products.value.map { it.plan })

        gateway.products = null
        store.loadProducts()

        assertEquals(ProductsState.LOADED, store.productsState.value)
        assertEquals(listOf("empty", "error"), observer.productFailures)
    }

    private class FakeMemory : EntitlementMemory {
        var saved: EntitlementKind? = null

        override suspend fun load(): EntitlementKind? = saved

        override suspend fun save(kind: EntitlementKind) {
            saved = kind
        }
    }

    private class RecordingObserver : ProStoreObserver {
        val purchaseSteps = mutableListOf<String>()
        val confirmed = mutableListOf<List<PlayPurchase>>()
        val productFailures = mutableListOf<String>()
        var keptWithoutAnswer = 0

        override suspend fun onPurchaseLaunching(product: ProProduct) {
            purchaseSteps += "launching"
        }

        override suspend fun onPurchased(purchase: PlayPurchase) {
            purchaseSteps += "purchased"
        }

        override suspend fun onPurchaseFinished(result: PurchaseResult) {
            purchaseSteps += "finished $result"
        }

        override suspend fun onConfirmed(active: List<PlayPurchase>) {
            confirmed += active
        }

        override fun onProductsLoadFailed(reason: String) {
            productFailures += reason
        }

        override fun onProKeptWithoutAnswer() {
            keptWithoutAnswer++
        }
    }
}
