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

    /**
     * A chave do usuário ("Enviar estatísticas" nos Ajustes). O `UserSwitchSink` para de repassar e desliga a coleta do
     * SDK; quem não coleta ignora. Gêmeo do `setCollectionEnabled` do CoreKit iOS.
     */
    public fun setCollectionEnabled(enabled: Boolean): Unit = Unit
}

/**
 * Uma venda confirmada, gêmea da `PurchaseRevenue` do iOS. O valor vai em centavos: nada de ponto flutuante no caminho do
 * dinheiro. Prefira o preço COBRADO (`ProProduct.chargedAtPurchaseCents` no `billing`: zero quando a compra começa com
 * teste grátis) ao de lista, que conta como receita uma venda que ainda não cobrou nada.
 */
public data class PurchaseRevenue(
    val itemId: String,
    val valueCents: Long,
    val currency: String,
    val transactionId: String? = null,
    val category: String = "pro",
    val itemName: String = itemId
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

    override fun setCollectionEnabled(enabled: Boolean): Unit = sinks.forEach { it.setCollectionEnabled(enabled) }
}

/**
 * A chave do usuário diante de um destino: repassa enquanto ela está ligada e nada enquanto está desligada, e avisa o
 * SDK a cada virada ([switchCollection]: a coleta do Firebase e do Crashlytics fica gravada no aparelho). Um erro vai
 * só com a classe e a pilha, nunca com a mensagem (que pode citar o que o usuário digitou). O `FirebaseMonitoring` já o
 * monta; um app com outro serviço envolve o destino dele. Gêmeo do hub do CoreKit iOS.
 */
public class UserSwitchSink(private val delegate: AnalyticsSink, enabled: Boolean, private val switchCollection: (Boolean) -> Unit = {}) :
    AnalyticsSink {
    @Volatile
    public var isEnabled: Boolean = enabled
        private set

    override fun log(name: String, parameters: Map<String, Any>) {
        if (isEnabled) delegate.log(name, parameters)
    }

    override fun screen(name: String) {
        if (isEnabled) delegate.screen(name)
    }

    override fun setUserProperty(name: String, value: String?) {
        if (isEnabled) delegate.setUserProperty(name, value)
    }

    override fun recordError(error: Throwable, context: String?) {
        if (isEnabled) delegate.recordError(error.withoutMessage(), context)
    }

    override fun logPurchase(purchase: PurchaseRevenue) {
        if (isEnabled) delegate.logPurchase(purchase)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        if (enabled == isEnabled) return

        isEnabled = enabled
        switchCollection(enabled)
    }
}

/**
 * O erro sem a mensagem: a classe vira o texto, a pilha fica (é por ela que o Crashlytics agrupa), a causa sai (a
 * mensagem dela também pode citar dados).
 */
public fun Throwable.withoutMessage(): Throwable {
    val bare = RuntimeException(javaClass.name)
    bare.stackTrace = stackTrace
    return bare
}

/**
 * Quem coleta: só o build de loja em uso real. Cada instalação de desenvolvimento ou rodada de teste automatizado
 * viraria um `first_open` e um funil falso nos dados de produção. Gêmeo do `CollectionPolicy` do CoreKit iOS.
 */
public object CollectionPolicy {
    /**
     * [debugOptIn] é o pedido explícito de quem desenvolve para um build de debug coletar, para conferir os eventos (o
     * `--analytics-debug` do iOS). Com o Firebase, passe `FirebaseMonitoring.isDebugViewEnabled`: os eventos vão só
     * para o DebugView e ficam fora dos relatórios. A rodada automatizada nunca coleta, nem com ele.
     */
    public fun shouldCollect(isDebugBuild: Boolean, isAutomatedRun: Boolean = false, debugOptIn: Boolean = false): Boolean = when {
        isAutomatedRun -> false
        isDebugBuild -> debugOptIn
        else -> true
    }
}
