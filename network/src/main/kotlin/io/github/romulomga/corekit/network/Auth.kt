package io.github.romulomga.corekit.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/** Se um request leva o token: sempre, só quando há sessão, ou nunca (login, rotas públicas). */
public enum class AuthRequirement { REQUIRED, OPTIONAL, NONE }

/** Qual request leva o token: a regra é do app (gêmeo do `AuthPolicy` do iOS). */
public fun interface AuthPolicy {
    public fun requirement(request: Request): AuthRequirement

    public companion object {
        /** Todo request exige o token. */
        public val ALL: AuthPolicy = AuthPolicy { AuthRequirement.REQUIRED }
    }
}

/** Sem token num request que exige: falha antes de sair, e o app leva ao logout central. */
public class MissingTokenException : IOException("Sem token de sessão")

/**
 * Põe o token, lido a cada request (nunca guardado no cliente: a sessão muda sem o OkHttp saber). Num request que a
 * [policy] diz que não leva, tira o cabeçalho, para um token velho nunca vazar para uma rota pública.
 *
 * @param token o token atual, ou `null`/vazio sem sessão.
 * @param headerName o cabeçalho (`Authorization` por padrão; o legado de um app pode usar outro).
 * @param prefix antes do token (`"Bearer "`), vazio por padrão.
 */
public class AuthInterceptor(
    private val token: () -> String?,
    private val policy: AuthPolicy = AuthPolicy.ALL,
    public val headerName: String = "Authorization",
    private val prefix: String = ""
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val current = token()

        val authorized = when (policy.requirement(request)) {
            AuthRequirement.NONE -> request.newBuilder().removeHeader(headerName).build()

            AuthRequirement.OPTIONAL -> if (current.isNullOrEmpty()) request else request.withToken(current)

            AuthRequirement.REQUIRED -> {
                if (current.isNullOrEmpty()) throw MissingTokenException()

                request.withToken(current)
            }
        }

        return chain.proceed(authorized)
    }

    private fun Request.withToken(token: String) = newBuilder().header(headerName, prefix + token).build()
}
