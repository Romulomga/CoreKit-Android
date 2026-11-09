package io.github.romulomga.corekit.billing

import android.app.Activity
import io.github.romulomga.corekit.Entitlement
import io.github.romulomga.corekit.EntitlementState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Onde o último direito confirmado fica guardado (o DataStore do app, com as chaves dele). */
public interface EntitlementMemory {
    /** `null` se a Play nunca respondeu neste aparelho; [EntitlementKind.NONE] é "respondeu, e não há compra". */
    public suspend fun load(): EntitlementKind?

    public suspend fun save(kind: EntitlementKind)
}

/**
 * O que é de cada app em volta da compra: analytics, espelho do direito para widget, aviso de fim do teste grátis.
 * Tudo com corpo vazio: implemente só o que usa.
 */
public interface ProStoreObserver {
    /** O direito foi aplicado (lido da memória, confirmado pela Play ou forçado). Pode repetir o mesmo valor. */
    public fun onEntitlement(kind: EntitlementKind) {}

    /** Antes de a folha da Play abrir: o lugar de guardar o que precisa sobreviver à morte do processo com ela aberta. */
    public suspend fun onPurchaseLaunching(product: ProProduct) {}

    /** A Play confirmou uma compra nova, antes de o direito ser consultado de novo. */
    public suspend fun onPurchased(purchase: PlayPurchase) {}

    /** Uma tentativa de compra terminou (também as que chegam fora do fluxo). */
    public suspend fun onPurchaseFinished(result: PurchaseResult) {}

    /** A Play deu uma resposta completa: [active] são as compras vigentes dos planos do app, já reconhecidas. */
    public suspend fun onConfirmed(active: List<PlayPurchase>) {}

    /** A busca de produtos falhou: [reason] é `error` ou `empty`. */
    public fun onProductsLoadFailed(reason: String) {}

    /** A Play não respondeu e o Pro em uso vem do último valor confirmado. Uma vez por processo. */
    public fun onProKeptWithoutAnswer() {}

    public object None : ProStoreObserver
}

/**
 * O direito ao Pro pela Play. Três regras, cada uma de um incidente:
 *
 * - O último direito confirmado fica guardado, e uma consulta que FALHA não tira o Pro de ninguém. Só uma resposta
 *   completa da Play muda o direito.
 * - Até saber, o estado é [EntitlementState.UNKNOWN], não "grátis": quem paga não vê teaser nem conta impressão de
 *   paywall na abertura. Sem memória (primeira abertura), a espera pela Play tem teto ([resolveTimeout]).
 * - Compra reconhecida sempre: a Play estorna em 3 dias a que não for.
 */
public class ProStore(
    private val gateway: BillingGateway,
    private val plans: List<ProPlan>,
    private val memory: EntitlementMemory,
    private val observer: ProStoreObserver = ProStoreObserver.None,
    private val resolveTimeout: Duration = 3.seconds
) : Entitlement {
    private val _state = MutableStateFlow(EntitlementState.UNKNOWN)
    override val state: StateFlow<EntitlementState> = _state.asStateFlow()

    private val _kind = MutableStateFlow(EntitlementKind.NONE)
    public val kind: StateFlow<EntitlementKind> = _kind.asStateFlow()

    private val _products = MutableStateFlow<List<ProProduct>>(emptyList())
    public val products: StateFlow<List<ProProduct>> = _products.asStateFlow()

    private val _productsState = MutableStateFlow(ProductsState.LOADING)
    public val productsState: StateFlow<ProductsState> = _productsState.asStateFlow()

    private val refreshLock = Mutex()
    private val productsLock = Mutex()

    @Volatile
    private var isForcedPro = false

    @Volatile
    private var hasReportedKeptPro = false

    init {
        gateway.setOutOfFlowListener { settle(it) }
    }

    /** No lançamento: o último direito confirmado, a consulta à Play e o catálogo. */
    public suspend fun start() {
        if (!isForcedPro) memory.load()?.let(::apply)

        coroutineScope {
            val query = async { query() }

            // Sem memória e com a Play lenta, a primeira tela não espera para sempre: segue como grátis até a resposta.
            if (withTimeoutOrNull(resolveTimeout) { query.await() } == null) resolveAsFreeIfUnknown()

            query.await()
        }

        resolveAsFreeIfUnknown()
        loadProducts()
    }

    /** A cada volta ao primeiro plano: assinatura que venceu, compra pendente que caiu. `false` se a Play não respondeu. */
    public suspend fun refresh(): Boolean = query()

    /** Pro vitalício até o fim do processo, e a Play não o derruba. Só para builds de teste: quem chama decide. */
    public fun forcePro() {
        isForcedPro = true
        apply(EntitlementKind.LIFETIME)
    }

    public suspend fun loadProducts() {
        if (!productsLock.tryLock()) return

        try {
            if (_products.value.isEmpty()) _productsState.value = ProductsState.LOADING

            val loaded = gateway.queryProducts(plans)
            val count = loaded?.size ?: 0

            if (!loaded.isNullOrEmpty()) _products.value = loaded.sortedBy { it.priceMicros }

            _productsState.value = ProductsState.after(loaded != null, count, _products.value.isNotEmpty())
            ProductsState.failureReason(loaded != null, count)?.let(observer::onProductsLoadFailed)
        } finally {
            productsLock.unlock()
        }
    }

    /** Ao abrir o paywall e na volta ao primeiro plano: a busca do lançamento pode ter falhado por rede. */
    public suspend fun reloadProductsIfFailed() {
        if (_productsState.value == ProductsState.FAILED) loadProducts()
    }

    /** Abre a folha da Play e devolve o desfecho. O analytics da compra é do app, em cima do [PurchaseOutcome]. */
    public suspend fun purchase(activity: Activity, product: ProProduct): PurchaseOutcome {
        observer.onPurchaseLaunching(product)

        return settle(gateway.purchase(activity, product))
    }

    /** `false` se a Play não respondeu de verdade: aí não dá para dizer que não há compras. */
    public suspend fun restore(): Boolean {
        val answered = query()

        return answered && (isPro || gateway.isOnline())
    }

    private suspend fun settle(update: BillingUpdate): PurchaseOutcome {
        val outcome = when (update.code) {
            BillingUpdateCode.OK -> {
                val purchased = update.purchases.firstOrNull { it.isPurchased }

                purchased?.let { observer.onPurchased(it) }

                // Compra pendente (boleto) não dá o direito ainda; a resposta completa da Play decide.
                acknowledge(update.purchases)
                query()

                PurchaseOutcome(if (purchased != null) PurchaseResult.PURCHASED else PurchaseResult.PENDING, purchased)
            }

            BillingUpdateCode.CANCELLED -> PurchaseOutcome(PurchaseResult.CANCELLED)

            // A Play diz que a conta já tem o produto: nada foi cobrado, só falta o app saber.
            BillingUpdateCode.ALREADY_OWNED -> PurchaseOutcome(PurchaseResult.ALREADY_OWNED).also { query() }

            BillingUpdateCode.ERROR -> PurchaseOutcome(PurchaseResult.FAILED, debugMessage = update.debugMessage)
        }

        observer.onPurchaseFinished(outcome.result)

        return outcome
    }

    /** `false` se a Play não deu uma resposta completa: nada muda. */
    private suspend fun query(): Boolean {
        if (isForcedPro) return true

        return refreshLock.withLock {
            val purchases = gateway.queryPurchases()

            if (purchases == null) reportKeptPro() else confirm(purchases)

            purchases != null
        }
    }

    private suspend fun confirm(purchases: List<PlayPurchase>) {
        val active = purchases.filter { purchase -> purchase.isPurchased && purchase.productIds.any { planOf(it) != null } }

        acknowledge(active)

        val resolved = EntitlementKind.resolve(active.flatMap { it.productIds }.mapNotNull(::planOf))

        apply(resolved)
        memory.save(resolved)
        observer.onConfirmed(active)
    }

    private suspend fun acknowledge(purchases: List<PlayPurchase>) {
        purchases.filter { it.isPurchased && !it.isAcknowledged }.forEach { gateway.acknowledge(it) }
    }

    private fun apply(resolved: EntitlementKind) {
        // O start() do Application pode terminar depois que a Activity forçou o Pro.
        val kind = if (isForcedPro) EntitlementKind.LIFETIME else resolved

        _kind.value = kind
        _state.value = if (kind == EntitlementKind.NONE) EntitlementState.FREE else EntitlementState.PRO
        observer.onEntitlement(kind)
    }

    private fun resolveAsFreeIfUnknown() {
        // Compare-and-set: a resposta da Play pode chegar no mesmo instante do teto, e ela vence.
        if (_state.compareAndSet(EntitlementState.UNKNOWN, EntitlementState.FREE)) observer.onEntitlement(EntitlementKind.NONE)
    }

    private fun reportKeptPro() {
        if (!isPro || hasReportedKeptPro) return

        hasReportedKeptPro = true
        observer.onProKeptWithoutAnswer()
    }

    private fun planOf(productId: String): ProPlan? = plans.firstOrNull { it.productId == productId }
}
