package io.github.romulomga.corekit.billing

import android.app.Activity

/**
 * A Play de mentira, para os testes do app em volta do [ProStore] (analytics da compra, aviso de fim do teste grátis).
 * Mude os campos para montar o cenário; [purchases] `null` é a Play sem resposta.
 */
public class FakeBillingGateway(
    public var purchases: List<PlayPurchase>? = emptyList(),
    public var products: List<ProProduct>? = emptyList(),
    public var online: Boolean = true
) : BillingGateway {
    /** O que a próxima compra devolve. */
    public var nextPurchase: BillingUpdate = BillingUpdate(BillingUpdateCode.CANCELLED)

    public val acknowledged: MutableList<String> = mutableListOf()

    public var purchaseQueries: Int = 0
        private set

    /** Roda antes de cada consulta de compras responder: o lugar de suspender para simular a Play lenta. */
    public var beforePurchasesAnswer: suspend () -> Unit = {}

    private var outOfFlow: (suspend (BillingUpdate) -> Unit)? = null

    override suspend fun queryPurchases(): List<PlayPurchase>? {
        purchaseQueries++
        beforePurchasesAnswer()

        return purchases
    }

    override suspend fun queryProducts(plans: List<ProPlan>): List<ProProduct>? = products

    override suspend fun acknowledge(purchase: PlayPurchase): Boolean {
        acknowledged += purchase.purchaseToken
        purchases = purchases?.map { if (it.purchaseToken == purchase.purchaseToken) it.copy(isAcknowledged = true) else it }

        return true
    }

    override suspend fun purchase(activity: Activity, product: ProProduct): BillingUpdate = nextPurchase

    override fun setOutOfFlowListener(listener: suspend (BillingUpdate) -> Unit) {
        outOfFlow = listener
    }

    override fun isOnline(): Boolean = online

    /** Entrega uma atualização fora do fluxo, como a compra pendente que caiu. */
    public suspend fun deliverOutOfFlow(update: BillingUpdate) {
        outOfFlow?.invoke(update)
    }
}
