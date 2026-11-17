package io.github.romulomga.corekit.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.IOException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import okhttp3.Interceptor
import okhttp3.Response

/** Há rede agora, e quando ela volta: a tela de leitura que falhou offline tenta de novo sozinha. */
public interface NetworkMonitor {
    public fun isOnline(): Boolean

    public val isOnlineUpdates: Flow<Boolean>
}

/** O [NetworkMonitor] do aparelho. O app o cria uma vez (no Hilt, um `@Provides @Singleton`). */
public class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override val isOnlineUpdates: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isOnline())
            }
        }

        trySend(isOnline())
        connectivity.registerDefaultNetworkCallback(callback)

        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

/** Sem rede nenhuma: o request falha na hora, sem sair e sem esperar o backoff. */
public class OfflineException : IOException("Sem conexão com a internet")

/** Gêmeo do `ReachabilityInterceptor` do iOS: com o [monitor] offline, nada sai. */
public class ConnectivityInterceptor(private val monitor: NetworkMonitor) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!monitor.isOnline()) throw OfflineException()

        return chain.proceed(chain.request())
    }
}
