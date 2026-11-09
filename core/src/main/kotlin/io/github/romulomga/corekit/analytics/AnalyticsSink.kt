package io.github.romulomga.corekit.analytics

/**
 * Para onde os eventos vão (Firebase, Mixpanel, Logcat). O app mantém os seus eventos tipados e só o adaptador conhece
 * o SDK: trocar de serviço, ou desligar a coleta em debug, não toca em tela nenhuma.
 */
public interface AnalyticsSink {
    public fun log(name: String, parameters: Map<String, Any> = emptyMap())

    public fun screen(name: String)

    public fun setUserProperty(name: String, value: String?)

    public fun recordError(error: Throwable, context: String? = null)

    /** A venda com valor e moeda, para os relatórios de receita do serviço; separada dos eventos de funil. */
    public fun logPurchase(purchase: PurchaseRevenue)
}

/** Uma venda confirmada. O valor vai em centavos: nada de ponto flutuante no caminho do dinheiro. */
public data class PurchaseRevenue(
    val itemId: String,
    val valueCents: Long,
    val currency: String,
    val transactionId: String? = null,
    val category: String = "pro"
)

/** O destino de quem não coleta: debug, testes e builds sem a configuração do serviço. */
public object NoopAnalyticsSink : AnalyticsSink {
    override fun log(name: String, parameters: Map<String, Any>): Unit = Unit

    override fun screen(name: String): Unit = Unit

    override fun setUserProperty(name: String, value: String?): Unit = Unit

    override fun recordError(error: Throwable, context: String?): Unit = Unit

    override fun logPurchase(purchase: PurchaseRevenue): Unit = Unit
}

/** Repassa cada chamada a vários destinos (por exemplo Logcat + o serviço real num build de homologação). */
public class CompositeAnalyticsSink(private val sinks: List<AnalyticsSink>) : AnalyticsSink {
    override fun log(name: String, parameters: Map<String, Any>): Unit = sinks.forEach { it.log(name, parameters) }

    override fun screen(name: String): Unit = sinks.forEach { it.screen(name) }

    override fun setUserProperty(name: String, value: String?): Unit = sinks.forEach { it.setUserProperty(name, value) }

    override fun recordError(error: Throwable, context: String?): Unit = sinks.forEach { it.recordError(error, context) }

    override fun logPurchase(purchase: PurchaseRevenue): Unit = sinks.forEach { it.logPurchase(purchase) }
}

/**
 * Quem coleta: só o build de loja em uso real. Cada instalação de desenvolvimento ou rodada de teste automatizado
 * viraria um `first_open` e um funil falso nos dados de produção.
 */
public object CollectionPolicy {
    public fun shouldCollect(isDebugBuild: Boolean, isAutomatedRun: Boolean = false): Boolean = !isDebugBuild && !isAutomatedRun
}
