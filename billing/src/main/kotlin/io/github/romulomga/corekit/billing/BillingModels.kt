package io.github.romulomga.corekit.billing

import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/** Um plano Pro do app: o id do produto na Play e se ele é assinatura ou compra única. */
public data class ProPlan(val productId: String, val type: ProPlanType, val analyticsName: String = productId) {
    public val isSubscription: Boolean get() = type == ProPlanType.SUBSCRIPTION
}

public enum class ProPlanType { SUBSCRIPTION, LIFETIME }

/** De onde vem o direito ao Pro. */
public enum class EntitlementKind(public val analyticsName: String) {
    NONE("none"),
    SUBSCRIPTION("subscription"),
    LIFETIME("lifetime");

    public companion object {
        /** Vitalício vence assinatura. A Play só devolve assinatura vigente, então "tem compra ativa" é o direito. */
        public fun resolve(purchased: Collection<ProPlan>): EntitlementKind = when {
            purchased.any { it.type == ProPlanType.LIFETIME } -> LIFETIME
            purchased.isNotEmpty() -> SUBSCRIPTION
            else -> NONE
        }

        public fun of(name: String?): EntitlementKind? = entries.firstOrNull { it.name == name }
    }
}

/** O carregamento dos produtos, para o paywall mostrar carregando, erro com "tentar de novo" ou os planos. */
public enum class ProductsState {
    LOADING,
    LOADED,
    FAILED;

    public companion object {
        /**
         * O estado depois de uma busca. Resposta vazia também é falha para quem olha (id ou disponibilidade errados),
         * e um catálogo já carregado sobrevive a uma busca que falha: trocar planos vendáveis por um card de erro, não.
         */
        public fun after(succeeded: Boolean, loadedCount: Int, hadProducts: Boolean): ProductsState = when {
            succeeded && loadedCount > 0 -> LOADED
            hadProducts -> LOADED
            else -> FAILED
        }

        /** O motivo da falha para o analytics (`error` ou `empty`); `null` quando não houve falha. */
        public fun failureReason(succeeded: Boolean, loadedCount: Int): String? = when {
            !succeeded -> "error"
            loadedCount == 0 -> "empty"
            else -> null
        }
    }
}

/** Uma fase de preço de uma oferta da Play (o teste grátis tem preço zero). */
public data class PricingPhase(val priceMicros: Long, val billingPeriod: String, val formattedPrice: String, val currencyCode: String)

/** Uma oferta de assinatura. A Play só lista as ofertas a que ESTA conta tem direito: trial já usado não volta. */
public data class SubscriptionOffer(val offerToken: String, val offerId: String?, val phases: List<PricingPhase>) {
    /** A duração do teste grátis (`P1W`, `P7D`…), se a oferta começa com uma fase de graça. */
    public val freeTrial: Period? get() = phases.firstOrNull()?.takeIf { it.priceMicros == 0L }?.let { parsePeriod(it.billingPeriod) }

    /** A fase que se repete: o preço que a pessoa paga depois de qualquer introdução. */
    public val recurringPhase: PricingPhase? get() = phases.lastOrNull()

    public companion object {
        /** A oferta com teste grátis, se houver (a conta é elegível); senão o plano base, sem `offerId`. */
        public fun pick(offers: List<SubscriptionOffer>): SubscriptionOffer? =
            offers.firstOrNull { it.freeTrial != null } ?: offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull()

        public fun parsePeriod(iso: String): Period? = try {
            Period.parse(iso)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

/** Dias do teste grátis para a copy "X dias grátis": mês e ano viram aproximação em dias. */
public fun Period.approximateDays(): Int = years * DAYS_PER_YEAR + months * DAYS_PER_MONTH + days

/** A 1ª cobrança real se o trial começa em [start]: conta de calendário (um mês não vira 30 dias). */
public fun Period.firstChargeAt(start: Instant, zone: ZoneId): Instant = start.atZone(zone).plus(this).toInstant()

private const val DAYS_PER_YEAR = 365
private const val DAYS_PER_MONTH = 30

/** Um plano à venda, com o preço da Play. */
public data class ProProduct(
    val plan: ProPlan,
    /** O preço que se repete (depois do trial, numa assinatura). */
    val formattedPrice: String,
    val priceMicros: Long,
    val currencyCode: String,
    /** O teste grátis a que esta conta tem direito; `null` sem oferta ou com o trial já usado. */
    val freeTrial: Period? = null,
    val offerToken: String? = null
) {
    public val priceCents: Long get() = priceMicros / MICROS_PER_CENT

    /** O único gate da UI para anunciar "X dias grátis": nunca a mera existência da oferta no console. */
    public val hasEligibleFreeTrial: Boolean get() = freeTrial != null

    public val freeTrialDays: Int? get() = freeTrial?.approximateDays()

    /** A data real da 1ª cobrança se o trial começar agora. */
    public fun introFirstChargeAt(now: ZonedDateTime): Instant? = freeTrial?.let { now.plus(it).toInstant() }

    private companion object {
        const val MICROS_PER_CENT = 10_000L
    }
}

/** Uma compra como a Play devolve, só com o que as regras usam (sem o tipo do SDK, para testar na JVM). */
public data class PlayPurchase(
    val purchaseToken: String,
    val productIds: List<String>,
    /** `false` é compra pendente (boleto, aprovação): ainda não dá o direito. */
    val isPurchased: Boolean,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean,
    val purchasedAt: Instant,
    val orderId: String? = null
)

public enum class PurchaseResult { PURCHASED, PENDING, CANCELLED, ALREADY_OWNED, FAILED }

/** O fim de uma tentativa de compra. [purchase] vem só no [PurchaseResult.PURCHASED]. */
public data class PurchaseOutcome(val result: PurchaseResult, val purchase: PlayPurchase? = null, val debugMessage: String? = null)
