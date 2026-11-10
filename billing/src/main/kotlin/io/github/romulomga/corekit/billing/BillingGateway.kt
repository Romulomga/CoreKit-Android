package io.github.romulomga.corekit.billing

import android.app.Activity

/**
 * O que o [ProStore] precisa da Play, sem os tipos do SDK. A regra do direito (o que guardar, quando não mudar nada)
 * fica testável na JVM com um dublê; só o [PlayBillingGateway] fala com a Play de verdade.
 */
public interface BillingGateway {
    /** Todas as compras da conta (assinaturas e compras únicas). `null` se a Play não deu uma resposta completa. */
    public suspend fun queryPurchases(): List<PlayPurchase>?

    /** Os produtos à venda dos [plans], com o que deu certo mesmo quando parte da busca falhou. */
    public suspend fun queryProducts(plans: List<ProPlan>): ProductsAnswer

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

/**
 * A resposta da busca de produtos. Uma falha parcial (assinaturas responderam, compra única não) ainda traz o que
 * carregou: esconder planos vendáveis por causa de um erro em outro tipo de produto, não.
 */
public data class ProductsAnswer(val products: List<ProProduct>, val failure: ProductsFailure? = null)

public enum class ProductsFailure(public val reason: String) {
    /** Não deu para falar com a Play (aparelho sem Play, serviço desligado): nenhuma busca chegou a acontecer. */
    UNAVAILABLE("unavailable"),

    /** A Play respondeu com erro a pelo menos uma busca. */
    ERROR("error"),

    /** Tudo respondeu e não veio produto nenhum (id ou disponibilidade errados). */
    EMPTY("empty")
}
