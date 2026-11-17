package io.github.romulomga.corekit.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AuthAndConnectivityTest {
    private val server = MockWebServer()

    private var token: String? = "abc"

    /** `/public` não leva token, `/help` leva se houver sessão, o resto exige. */
    private val policy = AuthPolicy { request ->
        when (request.url.pathSegments.firstOrNull()) {
            "public" -> AuthRequirement.NONE
            "help" -> AuthRequirement.OPTIONAL
            else -> AuthRequirement.REQUIRED
        }
    }

    private fun client(online: Boolean = true) = OkHttpClient.Builder()
        .addInterceptor(ConnectivityInterceptor(FakeMonitor(online)))
        .addInterceptor(AuthInterceptor(token = { token }, policy = policy, headerName = "X-Session", prefix = "Bearer "))
        .build()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun call(path: String, online: Boolean = true, stale: String? = null) {
        server.enqueue(MockResponse.Builder().code(200).build())

        val request = Request.Builder().url(server.url(path)).apply { stale?.let { header("X-Session", it) } }.build()

        client(online).newCall(request).execute().close()
    }

    @Test
    fun aRequiredRouteCarriesTheTokenInTheAppsHeader() {
        call("/posts")

        assertEquals("Bearer abc", server.takeRequest().headers["X-Session"])
    }

    @Test
    fun aPublicRouteNeverCarriesAToken() {
        call("/public/terms", stale = "old-token")

        assertNull(server.takeRequest().headers["X-Session"])
    }

    @Test
    fun anOptionalRouteGoesWithoutATokenWhenThereIsNoSession() {
        token = null

        call("/help")

        assertNull(server.takeRequest().headers["X-Session"])
    }

    @Test
    fun aRequiredRouteWithoutATokenFailsBeforeLeaving() {
        token = null

        try {
            call("/posts")
            fail("devia lançar")
        } catch (_: MissingTokenException) {
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun offlineNothingLeaves() {
        try {
            call("/posts", online = false)
            fail("devia lançar")
        } catch (_: OfflineException) {
        }

        assertEquals(0, server.requestCount)
    }

    private class FakeMonitor(private val online: Boolean) : NetworkMonitor {
        override fun isOnline(): Boolean = online

        override val isOnlineUpdates: Flow<Boolean> = flowOf(online)
    }
}
