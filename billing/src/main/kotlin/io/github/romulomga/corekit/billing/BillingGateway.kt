package io.github.romulomga.corekit.billing

import android.app.Activity

/**
 * O que o [ProStore] precisa da Play, sem os tipos do SDK. A regra do direito (o que guardar, quando não mudar nada)
 * fica testável na JVM com um dublê; só o [PlayBillingGateway] fala com a Play de verdade.
 */
public interface BillingGateway {
    /** Todas as compras da conta (assinaturas e compras únicas). `null` se a Play não deu uma resposta completa. */
    public suspend fun queryPurchases(): List<PlayPurchase>?

    /** Os produtos à venda dos [plans]. `null` se alguma busca falhou; lista vazia é resposta (ids errados, país). */
    public suspend fun queryProducts(plans: List<ProPlan>): List<ProProduct>?

    /** Compra não reconhecida em 3 dias é estornada pela Play. */
    public suspend fun acknowledge(purchase: PlayPurchase): Boolean

    /** Abre a folha de compra e suspende até a Play responder. */
    public suspend fun purchase(activity: Activity, product: ProProduct): BillingUpdate

    /**
     * Atualizações que chegam sem uma compra em andamento neste processo: a compra pendente que caiu, ou a compra cujo
     * fluxo foi cancelado (a tela morreu com a folha aberta).
     */
    public fun setOutOfFlowListener(listener: suspend (BillingUpdate) -> Unit)

    /** Sem rede a Play responde do cache: "nada a restaurar" seria falso para quem comprou em outro aparelho. */
    public fun isOnline(): Boolean
}

public enum class BillingUpdateCode { OK, CANCELLED, ALREADY_OWNED, ERROR }

/** O que a Play disse sobre uma compra. */
public data class BillingUpdate(
    val code: BillingUpdateCode,
    val purchases: List<PlayPurchase> = emptyList(),
    val debugMessage: String? = null
)
