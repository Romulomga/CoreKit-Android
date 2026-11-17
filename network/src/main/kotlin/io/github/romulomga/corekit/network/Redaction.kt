package io.github.romulomga.corekit.network

import okhttp3.logging.HttpLoggingInterceptor

/**
 * Tira de uma URL o que identifica uma pessoa antes de ela ir para um log ou um rastro: a query inteira (um `?email=`,
 * a busca, a posição) e os segmentos do caminho que levam um dado digitado, que são os que seguem um dos
 * [sensitiveParents] (`…/phone-number/{telefone}/code/{código}`). Ids ficam: são eles que dizem qual chamada falhou.
 * Gêmeo do `URLRedaction` do iOS.
 *
 * O app diz os seus segmentos sensíveis; sem nenhum, só a query e o fragmento saem.
 *
 * @param routeWords pedaços fixos de rota que podem vir depois de um segmento sensível sem ser dado (`…/code/check/…`).
 */
public class UrlRedaction(sensitiveParents: Set<String> = emptySet(), routeWords: Set<String> = emptySet()) {
    private val parents = sensitiveParents.map { it.lowercase() }.toSet()

    private val words = routeWords.map { it.lowercase() }.toSet()

    /** Troca cada URL de uma linha de log pela versão mascarada. */
    public fun maskUrlsIn(line: String): String = URL.replace(line) { maskUrl(it.value) }

    /** A URL sem query nem fragmento, com os segmentos sensíveis trocados por [MASK]. */
    public fun maskUrl(url: String): String {
        val bare = url.substringBefore('?').substringBefore('#')
        val schemeEnd = bare.indexOf("://")
        val pathStart = if (schemeEnd < 0) 0 else bare.indexOf('/', startIndex = schemeEnd + 3)

        return if (pathStart < 0) bare else bare.substring(0, pathStart) + maskPath(bare.substring(pathStart))
    }

    /** O caminho com os segmentos sensíveis trocados por [mask]. */
    public fun maskPath(path: String, mask: String = MASK): String {
        val segments = path.split("/")

        return segments.mapIndexed { index, segment -> if (isSensitive(segments, index)) mask else segment }.joinToString("/")
    }

    private fun isSensitive(segments: List<String>, index: Int): Boolean {
        val segment = segments[index].lowercase()
        val parent = segments.getOrNull(index - 1)?.lowercase()

        return segment.isNotEmpty() && segment !in words && parent in parents
    }

    public companion object {
        public const val MASK: String = "***"

        private val URL = Regex("""https?://[^\s"'<>]+""")
    }
}

/** Os cabeçalhos de credencial e de cookie de costume, mascarados em todo log (os mesmos do iOS). */
public val DEFAULT_REDACTED_HEADERS: Set<String> = setOf(
    "Authorization",
    "Cookie",
    "Set-Cookie",
    "X-API-Key",
    "X-Auth-Token",
    "X-Access-Token",
    "X-TOKEN",
    "Proxy-Authorization",
    "WWW-Authenticate"
)

/**
 * O log de rede de um build depurável que vai a testadores: método, URL, status e cabeçalhos, com os de credencial
 * apagados. Nunca o corpo, por padrão: ele leva a senha do login, o token que o servidor devolve e uploads inteiros
 * carregados em memória só para serem impressos. A URL sai pela [redaction].
 *
 * @param redactedHeaders os cabeçalhos apagados; some o do token do app se ele não for um dos de costume.
 */
public fun redactedHttpLogging(
    logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
    redaction: UrlRedaction = UrlRedaction(),
    redactedHeaders: Set<String> = DEFAULT_REDACTED_HEADERS,
    level: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.HEADERS
): HttpLoggingInterceptor = HttpLoggingInterceptor { line -> logger.log(redaction.maskUrlsIn(line)) }.apply {
    this.level = level

    redactedHeaders.forEach(::redactHeader)
}
