package io.github.romulomga.corekit.mixpanel

import android.content.Context
import com.mixpanel.android.mpmetrics.MixpanelAPI
import io.github.romulomga.corekit.analytics.AnalyticsSink
import io.github.romulomga.corekit.analytics.NoopAnalyticsSink
import io.github.romulomga.corekit.analytics.PurchaseRevenue
import io.github.romulomga.corekit.analytics.UserSwitchSink
import org.json.JSONObject

/**
 * Liga o Mixpanel, gêmeo do `.mixpanel(token:)` do CoreKit iOS: com os eventos automáticos dele (primeira abertura,
 * atualização, sessões), atrás da chave do usuário. Para usar junto do Firebase, junte os dois destinos num
 * `CompositeAnalyticsSink`: cada um segue a chave.
 */
public object MixpanelMonitoring {
    /**
     * @param token o token do projeto; `null` ou vazio deixa o Mixpanel desligado, para um build sem o arquivo de
     *   segredos rodar.
     * @param collect a política do build (`CollectionPolicy.shouldCollect`); `false` não liga o SDK.
     * @param userEnabled a escolha salva do usuário. Desligada, nada sai, nem os eventos automáticos.
     */
    public fun start(context: Context, token: String?, collect: Boolean, userEnabled: Boolean = true): AnalyticsSink {
        if (!collect || token.isNullOrEmpty()) return NoopAnalyticsSink

        val mixpanel = MixpanelAPI.getInstance(context, token, !userEnabled, true)
        val switchCollection = { enabled: Boolean ->
            if (enabled) mixpanel.optInTracking() else mixpanel.optOutTracking()
        }

        // O opt-out fica gravado no aparelho: quem desligou e religou numa abertura anterior ficaria calado.
        switchCollection(userEnabled)

        return UserSwitchSink(MixpanelAnalyticsSink(mixpanel), userEnabled, switchCollection)
    }
}

/** O único lugar que conhece o SDK do Mixpanel. */
public class MixpanelAnalyticsSink(private val mixpanel: MixpanelAPI) : AnalyticsSink {
    override fun log(name: String, parameters: Map<String, Any>): Unit = mixpanel.track(name, JSONObject(mixpanelProperties(parameters)))

    /** O Mixpanel não tem evento de tela; este usa os nomes do Firebase, para uma tela se ler igual nos dois. */
    override fun screen(name: String): Unit = mixpanel.track(SCREEN_VIEW, JSONObject(screenViewProperties(name)))

    /** Como super propriedade, que vai em todo evento seguinte: o par da propriedade de usuário do GA4. */
    override fun setUserProperty(name: String, value: String?) {
        if (value == null) mixpanel.unregisterSuperProperty(name) else mixpanel.registerSuperProperties(JSONObject(mapOf(name to value)))
    }

    /** O Mixpanel não é registro de falhas; os erros vão para o do app (Crashlytics). */
    override fun recordError(error: Throwable, context: String?): Unit = Unit

    /** A receita do Mixpanel mora no perfil de pessoa; nenhum app vende por ele ainda. Igual ao iOS. */
    override fun logPurchase(purchase: PurchaseRevenue): Unit = Unit

    /**
     * O `identify` junta os eventos anônimos deste aparelho ao usuário; o `reset` no logout começa um id anônimo novo,
     * para a próxima pessoa no aparelho não contar como a anterior.
     */
    override fun setUserId(id: String?) {
        if (id == null) mixpanel.reset() else mixpanel.identify(id)
    }
}

internal const val SCREEN_VIEW = "screen_view"

internal fun screenViewProperties(name: String): Map<String, Any> = mapOf("screen_name" to name)

/**
 * Os tipos ficam: uma propriedade que muda de tipo divide os relatórios do Mixpanel (um filtro numérico deixa de achar
 * o valor que virou texto). Diferente do Firebase, booleano vai como booleano. O que não for número, booleano ou texto
 * vira texto.
 */
internal fun mixpanelProperties(parameters: Map<String, Any>): Map<String, Any> = parameters.mapValues { (_, value) ->
    when (value) {
        is Number, is Boolean, is String -> value
        else -> value.toString()
    }
}
