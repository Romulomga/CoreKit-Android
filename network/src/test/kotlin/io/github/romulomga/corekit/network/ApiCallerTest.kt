package io.github.romulomga.corekit.network

import java.net.SocketTimeoutException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiCallerTest {
    private sealed class AppError : Exception() {
        object Offline : AppError()

        object Timeout : AppError()

        object SessionExpired : AppError()

        object Unknown : AppError()
    }

    private class Unauthorized : Exception()

    private var raised = 0

    private val caller = ApiCaller(
        map = { error ->
            when (error) {
                is AppError -> error
                is OfflineException -> AppError.Offline
                is SocketTimeoutException -> AppError.Timeout
                is Unauthorized, is MissingTokenException -> AppError.SessionExpired
                else -> AppError.Unknown
            }
        },
        isSessionExpired = { it == AppError.SessionExpired },
        onSessionExpired = { raised++ }
    )

    private suspend fun mapped(error: Throwable): AppError = try {
        caller.call { throw error }
    } catch (app: AppError) {
        app
    }

    @Test
    fun everyFailureBecomesTheAppsError() = runTest {
        assertEquals(AppError.Offline, mapped(OfflineException()))
        assertEquals(AppError.Timeout, mapped(SocketTimeoutException()))
        assertEquals(AppError.Unknown, mapped(IllegalStateException()))
        assertEquals(0, raised)
    }

    @Test
    fun anExpiredSessionCallsTheCentralLogoutOnce() = runTest {
        assertEquals(AppError.SessionExpired, mapped(Unauthorized()))
        assertEquals(1, raised)
    }

    /** O fim da sessão cancelou o pedido, e o 401 chegou mesmo assim: a resposta não é de sessão nenhuma. */
    @Test
    fun aCallCancelledMeanwhileReportsNothing() = runTest {
        val call = launch {
            caller.call {
                currentCoroutineContext().job.cancel()

                throw Unauthorized()
            }
        }

        call.join()

        assertTrue(call.isCancelled)
        assertEquals(0, raised)
    }

    @Test
    fun aSuccessPassesThrough() = runTest {
        assertEquals(42, caller.call { 42 })
    }
}
