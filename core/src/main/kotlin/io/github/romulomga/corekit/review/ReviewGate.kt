package io.github.romulomga.corekit.review

import java.time.Duration
import java.time.Instant

/**
 * A trava de frequência do pedido de avaliação: uma vez por versão e, se o app quiser, com um intervalo mínimo entre
 * pedidos. A loja já limita a exibição sozinha; isto evita gastar a cota dela com pedidos em sequência. QUANDO pedir
 * (o momento de êxito) é regra de produto de cada app.
 */
public object ReviewGate {
    public fun isEligible(
        currentVersion: String,
        lastPromptVersion: String?,
        lastPromptAt: Instant? = null,
        now: Instant = Instant.now(),
        minDaysBetween: Int = 0
    ): Boolean {
        if (lastPromptVersion == currentVersion) return false

        if (lastPromptAt == null || minDaysBetween <= 0) return true

        return Duration.between(lastPromptAt, now).toDays() >= minDaysBetween
    }
}
