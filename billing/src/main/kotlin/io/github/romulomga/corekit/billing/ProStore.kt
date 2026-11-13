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

    /**
     * A busca de produtos falhou, mesmo que em parte: [reason] é `error`, `empty` ou `unavailable` (não deu para falar
     * com a Play, como num aparelho sem ela; o app decide se isso conta como falha no analytics).
     */
    public fun onProductsLoadFailed(reason: String) {}

    /** A Play não respondeu e o Pro em uso vem do último valor confirmado. Uma vez por processo. */
    public fun onProKeptWithoutAnswer() {}

    public object None : ProStoreObserver
}

/**
 * O direito ao Pro pela Play, gêmeo do `ProStore` do iOS. Cada regra vem de um incidente:
 *
 * - O último direito confirmado fica guardado, e uma consulta que FALHA não tira o Pro de ninguém. Só uma resposta
 *   completa da Play muda o direito, ou a compra que ela acabou de confirmar, mesmo que a consulta seguinte falhe.
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
    private var forced: EntitlementKind? = null

    @Volatile
    private var hasReportedKeptPro = false

    init {
        gateway.setOutOfFlowListener { settle(it) }
    }

    /** No lançamento: o último direito confirmado, a consulta à Play e o catálogo. */
    public suspend fun start() {
        if (forced == null) memory.load()?.let(::apply)

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

    /**
     * [kind] até o fim do processo, diga a Play o que disser, e nada é guardado: o Pro das capturas da loja, ou o grátis
     * numa conta que tem o vitalício (que não se cancela). Só para builds de teste: quem chama decide. A compra feita
     * assim ainda é reconhecida. Gêmeo do `force(_:)` do iOS.
     */
    public fun force(kind: EntitlementKind) {
        forced = kind
        apply(kind)
    }

    /** O mesmo que `force(EntitlementKind.LIFETIME)`. */
    public fun forcePro(): Unit = force(EntitlementKind.LIFETIME)

    public suspend fun loadProducts() {
        if (!productsLock.tryLock()) return

        try {
            if (_products.value.isEmpty()) _productsState.value = ProductsState.LOADING

            val answer = gateway.queryProducts(plans)

            if (answer.products.isNotEmpty()) _products.value = answer.products.sortedBy { it.priceMicros }

            _productsState.value = if (_products.value.isNotEmpty()) ProductsState.LOADED else ProductsState.FAILED
            answer.failure?.let { observer.onProductsLoadFailed(it.reason) }
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

    private suspend fun settle(update: BillingUpdate): PurchaseOutcome = when (update.code) {
        BillingUpdateCode.OK -> {
            val purchased = update.purchases.firstOrNull { it.isPurchased }

            purchased?.let { observer.onPurchased(it) }

            // Compra pendente (boleto) não dá o direito ainda; a resposta completa da Play decide.
            val delivered = acknowledge(update.purchases)

            query(evidence = delivered.filter { it.isPurchased })

            finish(PurchaseOutcome(if (purchased != null) PurchaseResult.PURCHASED else PurchaseResult.PENDING, purchased))
        }

        BillingUpdateCode.CANCELLED -> finish(PurchaseOutcome(PurchaseResult.CANCELLED))

        // A Play diz que a conta já tem o produto: nada foi cobrado, só falta o app saber. O observador fecha a
        // tentativa ANTES da consulta: o que ele guardou para uma compra nova (um trial à espera) não pode ser adotado
        // pela compra antiga que a consulta vai trazer.
        BillingUpdateCode.ALREADY_OWNED -> finish(PurchaseOutcome(PurchaseResult.ALREADY_OWNED)).also { query() }

        BillingUpdateCode.ERROR -> finish(PurchaseOutcome(PurchaseResult.FAILED, debugMessage = update.debugMessage))
    }

    private suspend fun finish(outcome: PurchaseOutcome): PurchaseOutcome {
        observer.onPurchaseFinished(outcome.result)

        return outcome
    }

    /**
     * `false` se a Play não deu uma resposta completa: nada muda, salvo a compra que ela acabou de confirmar
     * ([evidence]). Essa vale mesmo que a consulta falhe ou ainda não a traga: quem acabou de pagar não fica no grátis
     * até a próxima volta ao app (no iOS, a transação verificada entra do mesmo jeito).
     */
    private suspend fun query(evidence: List<PlayPurchase> = emptyList()): Boolean {
        if (forced != null) return true

        return refreshLock.withLock {
            val purchases = gateway.queryPurchases()

            when {
                purchases != null -> confirm(purchases + evidence.filter { new -> purchases.none { it.isSameAs(new) } })
                !grant(evidence) -> reportKeptPro()
            }

            purchases != null
        }
    }

    /** Sem a resposta da Play, a compra recém-confirmada só pode subir o direito: as outras compras da conta não vieram. */
    private suspend fun grant(evidence: List<PlayPurchase>): Boolean {
        val proven = EntitlementKind.resolve(evidence.flatMap { it.productIds }.mapNotNull(::planOf))

        if (proven == EntitlementKind.NONE) return false

        // A ordem do enum é a do direito: vitalício vence assinatura, que vence nada.
        val kind = maxOf(_kind.value, proven)

        apply(kind)
        memory.save(kind)

        return true
    }

    private suspend fun confirm(purchases: List<PlayPurchase>) {
        val active = purchases.filter { purchase -> purchase.isPurchased && purchase.productIds.any { planOf(it) != null } }

        acknowledge(active)

        val resolved = EntitlementKind.resolve(active.flatMap { it.productIds }.mapNotNull(::planOf))

        apply(resolved)
        memory.save(resolved)
        observer.onConfirmed(active)
    }

    /** Devolve as compras com o reconhecimento que deu certo já marcado, para a mesma compra não ser reconhecida duas vezes. */
    private suspend fun acknowledge(purchases: List<PlayPurchase>): List<PlayPurchase> = purchases.map { purchase ->
        val done = purchase.isPurchased && !purchase.isAcknowledged && gateway.acknowledge(purchase)

        if (done) purchase.copy(isAcknowledged = true) else purchase
    }

    private fun apply(resolved: EntitlementKind) {
        // O start() do Application pode terminar depois que a Activity forçou o direito.
        val kind = forced ?: resolved

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

    private fun PlayPurchase.isSameAs(other: PlayPurchase): Boolean = purchaseToken == other.purchaseToken
}
