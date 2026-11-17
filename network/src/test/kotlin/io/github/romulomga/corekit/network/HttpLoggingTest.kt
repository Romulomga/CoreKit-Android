package io.github.romulomga.corekit.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** O log de rede nunca leva o token, os corpos nem o dado pessoal da URL. */
class HttpLoggingTest {
    private val server = MockWebServer()
    private val lines = mutableListOf<String>()

    private val client = OkHttpClient.Builder()
        .addInterceptor(
            redactedHttpLogging(
                logger = { lines += it },
                redaction = UrlRedaction(sensitiveParents = setOf("phone-number", "code")),
                redactedHeaders = DEFAULT_REDACTED_HEADERS + "X-Session"
            )
        )
        .build()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    @Test
    fun theTokenAndTheBodiesNeverReachTheLog() {
        server.enqueue(MockResponse.Builder().code(200).body("""{"token":"renewed"}""").build())

        val request = Request.Builder()
            .url(server.url("/v1/auth/refresh"))
            .header("X-Session", "current-token")
            .header("Authorization", "Bearer current-token")
            .post("""{"password":"hunter2"}""".toRequestBody())
            .build()

        client.newCall(request).execute().use { it.body.string() }

        val log = lines.joinToString("\n")

        assertTrue("o request aparece", log.contains("/v1/auth/refresh"))
        assertTrue("o nome do cabeçalho do app fica, o valor não", log.contains("X-Session: ██"))
        assertFalse("o token enviado", log.contains("current-token"))
        assertFalse("o token devolvido no corpo", log.contains("renewed"))
        assertFalse("a senha no corpo", log.contains("hunter2"))
    }

    @Test
    fun theQueryAndTheTypedDataOfTheUrlNeverReachTheLog() {
        repeat(2) { server.enqueue(MockResponse.Builder().code(200).body("{}").build()) }

        listOf("/v1/users/exists?email=ana%40example.com", "/v1/phone-number/11987654321/code/482913").forEach { path ->
            client.newCall(Request.Builder().url(server.url(path)).build()).execute().use { it.body.string() }
        }

        val log = lines.joinToString("\n")

        assertTrue("os dois requests aparecem", log.contains("/v1/users/exists") && log.contains("/phone-number/***/code/***"))
        assertFalse("o e-mail da query", log.contains("ana") || log.contains("email="))
        assertFalse("o telefone no caminho", log.contains("11987654321"))
        assertFalse("o código no caminho", log.contains("482913"))
    }
}
