package io.github.romulomga.corekit.firebase

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.romulomga.corekit.analytics.AnalyticsSink
import io.github.romulomga.corekit.analytics.NoopAnalyticsSink
import io.github.romulomga.corekit.analytics.PurchaseRevenue

/** A partida do Firebase Analytics + Crashlytics com a regra de coleta num lugar só. */
public object FirebaseMonitoring {
    /**
     * O destino dos eventos para este processo. Sem `google-services.json` (o Firebase não iniciou) ou com [collect]
     * `false`, devolve o destino nulo e desliga a coleta dos dois SDKs: o manifesto pode desligá-la antes de o app
     * abrir, e isto vale para o resto do processo. Calcule [collect] com o `CollectionPolicy`.
     */
    public fun start(context: Context, collect: Boolean): AnalyticsSink {
        if (FirebaseApp.getApps(context).isEmpty()) return NoopAnalyticsSink

        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(collect)
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = collect

        return if (collect) FirebaseAnalyticsSink(context) else NoopAnalyticsSink
    }
}

/**
 * O único lugar que conhece o SDK do Firebase. Sem ID de publicidade: o app retira `AD_ID` e as permissões do
 * AdServices no manifesto.
 */
public class FirebaseAnalyticsSink(context: Context) : AnalyticsSink {
    private val analytics = FirebaseAnalytics.getInstance(context)

    override fun log(name: String, parameters: Map<String, Any>): Unit = analytics.logEvent(name, parameters.toBundle())

    override fun screen(name: String): Unit = analytics.logEvent(
        FirebaseAnalytics.Event.SCREEN_VIEW,
        Bundle().apply { putString(FirebaseAnalytics.Param.SCREEN_NAME, name) }
    )

    override fun setUserProperty(name: String, value: String?): Unit = analytics.setUserProperty(name, value)

    override fun recordError(error: Throwable, context: String?) {
        val crashlytics = FirebaseCrashlytics.getInstance()

        context?.let(crashlytics::log)
        crashlytics.recordException(error)
    }

    override fun logPurchase(purchase: PurchaseRevenue) {
        val item = Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_ID, purchase.itemId)
            putString(FirebaseAnalytics.Param.ITEM_NAME, purchase.itemId)
            putString(FirebaseAnalytics.Param.ITEM_CATEGORY, purchase.category)
        }
        val params = Bundle().apply {
            putDouble(FirebaseAnalytics.Param.VALUE, purchase.valueCents / CENTS_PER_UNIT)
            putString(FirebaseAnalytics.Param.CURRENCY, purchase.currency)
            putParcelableArray(FirebaseAnalytics.Param.ITEMS, arrayOf(item))
            purchase.transactionId?.let { putString(FirebaseAnalytics.Param.TRANSACTION_ID, it) }
        }

        analytics.logEvent(FirebaseAnalytics.Event.PURCHASE, params)
    }

    private companion object {
        const val CENTS_PER_UNIT = 100.0
    }
}

/**
 * Número vai como número (o GA4 soma e faz média); o resto vira texto. Booleano vira `"true"`/`"false"`: uma dimensão
 * do GA4 só lê o slot de texto.
 */
internal fun Map<String, Any>.toBundle(): Bundle = Bundle().also { bundle ->
    forEach { (key, value) ->
        when (value) {
            is Int -> bundle.putLong(key, value.toLong())
            is Long -> bundle.putLong(key, value)
            is Float -> bundle.putDouble(key, value.toDouble())
            is Double -> bundle.putDouble(key, value)
            else -> bundle.putString(key, value.toString())
        }
    }
}
