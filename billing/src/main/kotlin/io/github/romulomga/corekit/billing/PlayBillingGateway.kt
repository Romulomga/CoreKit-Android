package io.github.romulomga.corekit.billing

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** A Play de verdade (Play Billing). [scope] é o do app: a resposta de uma compra chega fora de qualquer tela. */
public class PlayBillingGateway(context: Context, private val scope: CoroutineScope) : BillingGateway {
    private val appContext = context.applicationContext
    private val details = ConcurrentHashMap<String, ProductDetails>()
    private val pendingFlow = AtomicReference<CompletableDeferred<BillingUpdate>?>(null)
    private val outOfFlow = AtomicReference<(suspend (BillingUpdate) -> Unit)?>(null)

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(appContext)
            .setListener { result, purchases -> onPurchasesUpdated(result, purchases.orEmpty()) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    override suspend fun queryPurchases(): List<PlayPurchase>? {
        if (!connect()) return null

        val all = mutableListOf<PlayPurchase>()

        for (type in PRODUCT_TYPES) {
            val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build())

            if (result.billingResult.responseCode != BillingResponseCode.OK) return null

            all += result.purchasesList.map { it.toPlayPurchase() }
        }

        return all
    }

    override suspend fun queryProducts(plans: List<ProPlan>): List<ProProduct>? {
        if (!connect()) return null

        val all = mutableListOf<ProProduct>()

        for (type in PRODUCT_TYPES) {
            val wanted = plans.filter { it.productType == type }

            if (wanted.isEmpty()) continue

            val products = wanted.map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it.productId).setProductType(type).build()
            }
            val result = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(products).build())

            if (result.billingResult.responseCode != BillingResponseCode.OK) {
                Log.w(TAG, "queryProductDetails failed: ${result.billingResult.debugMessage}")
                return null
            }

            all += result.productDetailsList.orEmpty().mapNotNull { found ->
                val plan = wanted.firstOrNull { it.productId == found.productId } ?: return@mapNotNull null

                toProduct(plan, found)?.also { details[plan.productId] = found }
            }
        }

        return all
    }

    private fun toProduct(plan: ProPlan, details: ProductDetails): ProProduct? {
        if (!plan.isSubscription) {
            val offer = details.oneTimePurchaseOfferDetails ?: return null

            return ProProduct(plan, offer.formattedPrice, offer.priceAmountMicros, offer.priceCurrencyCode)
        }

        val offers = details.subscriptionOfferDetails.orEmpty().map { offer ->
            SubscriptionOffer(
                offerToken = offer.offerToken,
                offerId = offer.offerId,
                phases = offer.pricingPhases.pricingPhaseList.map {
                    PricingPhase(it.priceAmountMicros, it.billingPeriod, it.formattedPrice, it.priceCurrencyCode)
                }
            )
        }
        val offer = SubscriptionOffer.pick(offers) ?: return null
        val phase = offer.recurringPhase ?: return null

        return ProProduct(plan, phase.formattedPrice, phase.priceMicros, phase.currencyCode, offer.freeTrial, offer.offerToken)
    }

    override suspend fun acknowledge(purchase: PlayPurchase): Boolean {
        if (!connect()) return false

        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        val result = client.acknowledgePurchase(params)

        if (result.responseCode != BillingResponseCode.OK) Log.w(TAG, "acknowledge failed: ${result.debugMessage}")

        return result.responseCode == BillingResponseCode.OK
    }

    override suspend fun purchase(activity: Activity, product: ProProduct): BillingUpdate {
        val found = details[product.plan.productId] ?: return BillingUpdate(BillingUpdateCode.ERROR, debugMessage = "product not loaded")

        if (!connect()) return BillingUpdate(BillingUpdateCode.ERROR, debugMessage = "billing unavailable")

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(found)

        product.offerToken?.let(productParams::setOfferToken)

        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams.build())).build()
        val flow = CompletableDeferred<BillingUpdate>()

        pendingFlow.set(flow)

        try {
            val launched = client.launchBillingFlow(activity, params)

            if (launched.responseCode !=
                BillingResponseCode.OK
            ) {
                return BillingUpdate(launched.toCode(), debugMessage = launched.debugMessage)
            }

            return flow.await()
        } finally {
            // Quem esperava foi embora (a tela morreu com a folha aberta): a resposta vai para o ouvinte de fora do fluxo.
            pendingFlow.compareAndSet(flow, null)
        }
    }

    override fun setOutOfFlowListener(listener: suspend (BillingUpdate) -> Unit) {
        outOfFlow.set(listener)
    }

    override fun isOnline(): Boolean {
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)

        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>) {
        val update = BillingUpdate(result.toCode(), purchases.map { it.toPlayPurchase() }, result.debugMessage)
        val flow = pendingFlow.getAndSet(null)

        if (flow != null && flow.complete(update)) return

        outOfFlow.get()?.let { listener -> scope.launch { listener(update) } }
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true

        return suspendCancellableCoroutine { continuation ->
            client.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (continuation.isActive) continuation.resume(result.responseCode == BillingResponseCode.OK)
                    }

                    override fun onBillingServiceDisconnected() {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            )
        }
    }

    private fun BillingResult.toCode(): BillingUpdateCode = when (responseCode) {
        BillingResponseCode.OK -> BillingUpdateCode.OK
        BillingResponseCode.USER_CANCELED -> BillingUpdateCode.CANCELLED
        BillingResponseCode.ITEM_ALREADY_OWNED -> BillingUpdateCode.ALREADY_OWNED
        else -> BillingUpdateCode.ERROR
    }

    private fun Purchase.toPlayPurchase() = PlayPurchase(
        purchaseToken = purchaseToken,
        productIds = products,
        isPurchased = purchaseState == Purchase.PurchaseState.PURCHASED,
        isAcknowledged = isAcknowledged,
        isAutoRenewing = isAutoRenewing,
        purchasedAt = Instant.ofEpochMilli(purchaseTime),
        orderId = orderId
    )

    private val ProPlan.productType: String get() = if (isSubscription) ProductType.SUBS else ProductType.INAPP

    private companion object {
        const val TAG = "CoreKit.billing"

        val PRODUCT_TYPES = listOf(ProductType.SUBS, ProductType.INAPP)
    }
}
