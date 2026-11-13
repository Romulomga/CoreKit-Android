package io.github.romulomga.corekit.firebase

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics.Param
import io.github.romulomga.corekit.analytics.PurchaseRevenue
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

    @Test
    fun `a purchase carries the value in whole units and an item with its own price`() {
        val purchase = PurchaseRevenue(itemId = "yearly", valueCents = 4_990, currency = "BRL", transactionId = "GPA.1")
        val parameters = purchaseParameters(purchase)
        val item = parameters.items().single()

        assertEquals(49.9, parameters.getDouble(Param.VALUE), 0.0)
        assertEquals("BRL", parameters.getString(Param.CURRENCY))
        assertEquals("GPA.1", parameters.getString(Param.TRANSACTION_ID))
        assertEquals("yearly", item.getString(Param.ITEM_ID))
        assertEquals("pro", item.getString(Param.ITEM_CATEGORY))
        assertEquals(49.9, item.getDouble(Param.PRICE), 0.0)
        assertEquals("BRL", item.getString(Param.CURRENCY))
        assertEquals(1L, item.getLong(Param.QUANTITY))
        assertEquals("yearly", item.getString(Param.ITEM_NAME))
    }

    @Test
    fun `the item name can differ from its id`() {
        val purchase = PurchaseRevenue(itemId = "app.pro.yearly", valueCents = 0, currency = "BRL", itemName = "yearly")
        val parameters = purchaseParameters(purchase)

        assertEquals("yearly", parameters.items().single().getString(Param.ITEM_NAME))
    }

    @Suppress("DEPRECATION")
    private fun Bundle.items(): List<Bundle> = getParcelableArray(Param.ITEMS).orEmpty().map { it as Bundle }
}
