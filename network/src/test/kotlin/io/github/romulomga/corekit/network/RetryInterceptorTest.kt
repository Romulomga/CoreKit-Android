package io.github.romulomga.corekit.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RetryInterceptorTest {
    private val server = MockWebServer()

    private fun client(policy: RetryPolicy = RetryPolicy.DEFAULT) = OkHttpClient.Builder().addInterceptor(
        RetryInterceptor(policy, sleep = {
        })
    ).build()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun enqueue(code: Int) = server.enqueue(MockResponse.Builder().code(code).build())

    private fun get(path: String = "/v1/x") = Request.Builder().url(server.url(path)).build()

    @Test
    fun aReadRetriesA5xxUpToTheLimit() {
        repeat(3) { enqueue(503) }

        val response = client().newCall(get()).execute()

        assertEquals(503, response.code)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun aReadRecoversOnTheSecondAttempt() {
        enqueue(500)
        enqueue(200)

        assertEquals(200, client().newCall(get()).execute().code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun mutationsAreNeverRetried() {
        enqueue(503)

        client().newCall(Request.Builder().url(server.url("/v1/posts")).post("{}".toRequestBody()).build()).execute()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun aReadWithASideEffectOptsOutAndTheHeaderNeverLeaves() {
        enqueue(503)

        client().newCall(Request.Builder().url(server.url("/v1/read")).header(NO_RETRY_HEADER, "true").build()).execute()

        assertEquals(1, server.requestCount)
        assertNull(server.takeRequest().headers[NO_RETRY_HEADER])
    }

    @Test
    fun theAppChoosesThePolicy() {
        repeat(5) { enqueue(503) }

        client(RetryPolicy(maxAttempts = 5)).newCall(get()).execute()

        assertEquals(5, server.requestCount)
    }

    @Test
    fun theNonePolicyTriesOnce() {
        enqueue(503)

        client(RetryPolicy.NONE).newCall(get()).execute()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun theWaitDoublesOn429AndIsCapped() {
        val policy = RetryPolicy(jitter = 0.0..0.0)

        assertEquals(1_000, policy.delayFor(0, 500))
        assertEquals(2_000, policy.delayFor(0, 429))
        assertEquals(30_000, policy.delayFor(10, 500))
        assertTrue(RetryPolicy().delayFor(0, 429) >= 1_500)
    }
}
