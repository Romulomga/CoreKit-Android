package io.github.romulomga.corekit.network

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import okhttp3.Interceptor
import okhttp3.Response

/** Header interno que tira um GET do retry (GET com efeito colateral, como "marcar como lido"). Nunca sai do app. */
public const val NO_RETRY_HEADER: String = "X-CoreKit-No-Retry"

/** Para o `@Headers` do Retrofit: `@Headers(NO_RETRY)`. */
public const val NO_RETRY: String = "$NO_RETRY_HEADER: true"

/**
 * Quando repetir um request, igual ao `RetryPolicy` do CoreKitNetwork (iOS). O padrão: 3 tentativas, espera de 1 s
 * dobrando até 30 s (o dobro num 429), ±25 %, **só em GET/HEAD/OPTIONS**, para 408, 429, 5xx, timeout e conexão perdida.
 *
 * Mutação nunca entra por padrão: um `PUT` ou `DELETE` idempotente no HTTP ainda pode ter efeito num servidor qualquer,
 * e reenviar às cegas duplica. Um GET com efeito colateral sai pelo [NO_RETRY_HEADER].
 */
public data class RetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 1_000,
    val maxDelayMs: Long = 30_000,
    val retryableMethods: Set<String> = setOf("GET", "HEAD", "OPTIONS"),
    val retryableStatus: Set<Int> = setOf(408, 429) + (500..599),
    val jitter: ClosedFloatingPointRange<Double> = -0.25..0.25
) {
    /** A espera antes da tentativa seguinte a [attempt] (a partir de 0), dobrada num 429 e limitada a [maxDelayMs]. */
    public fun delayFor(attempt: Int, status: Int?, random: Random = Random): Long {
        val multiplier = if (status == 429) 2.0 else 1.0
        val exponential = baseDelayMs * multiplier * 2.0.pow(attempt.coerceAtLeast(0))
        val fraction = if (jitter.endInclusive > jitter.start) random.nextDouble(jitter.start, jitter.endInclusive) else jitter.start
        val spread = exponential * fraction

        return min((exponential + spread).toLong(), maxDelayMs)
    }

    public companion object {
        public val DEFAULT: RetryPolicy = RetryPolicy()

        /** Uma tentativa só, para todo método. */
        public val NONE: RetryPolicy = RetryPolicy(retryableMethods = emptySet())
    }
}

/**
 * Repete pelo [policy]. Sem rede ([OfflineException]) e sem token ([MissingTokenException]) falham na hora: esperar não
 * traz nenhum dos dois, e quem chamou mostra "sem conexão" ou leva ao logout. Request cancelado nunca é repetido.
 */
public class RetryInterceptor(private val policy: RetryPolicy = RetryPolicy.DEFAULT, private val sleep: (Long) -> Unit = Thread::sleep) :
    Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val optedOut = original.header(NO_RETRY_HEADER) != null
        val request = original.newBuilder().removeHeader(NO_RETRY_HEADER).build()

        if (optedOut || request.method !in policy.retryableMethods) return chain.proceed(request)

        var attempt = 0

        while (true) {
            val outcome = runCatching { chain.proceed(request) }
            val response = outcome.getOrNull()
            val error = outcome.exceptionOrNull()
            val retryable = when {
                response != null -> response.code in policy.retryableStatus
                error is OfflineException || error is MissingTokenException -> false
                error is SocketTimeoutException -> true
                error is InterruptedIOException -> false
                error is IOException -> !chain.call().isCanceled()
                else -> false
            }

            if (!retryable || attempt >= policy.maxAttempts - 1) {
                return response ?: throw checkNotNull(error)
            }

            response?.close()
            sleep(policy.delayFor(attempt, response?.code))
            attempt++
        }
    }
}
