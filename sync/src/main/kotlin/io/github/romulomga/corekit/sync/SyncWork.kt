package io.github.romulomga.corekit.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

/**
 * O sync em segundo plano, como extra (o app sincroniza ao abrir, ao puxar e quando a rede volta). O WorkManager cria
 * o Worker pela classe, então o Worker é do app: um `CoroutineWorker` que pega o motor (Hilt `@HiltWorker`, ou o
 * `Application`) e devolve [SyncEngine.runAsWork]. Daqui vêm o agendamento (único por nome, só com rede, espera
 * crescente) e esse resultado. Diferente do iOS, não exige nada no manifesto do app: a biblioteca traz o que precisa.
 */
public object SyncWork {
    /** Um sync agora (ou assim que houver rede); um pedido com o mesmo nome a caminho fica. */
    public fun enqueueNow(context: Context, worker: Class<out ListenableWorker>, uniqueName: String) {
        val request = OneTimeWorkRequest.Builder(worker)
            .setConstraints(networkRequired())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
    }

    /** Um sync de tempos em tempos (no mínimo 15 min, a regra do WorkManager); o agendamento de antes fica. */
    public fun enqueuePeriodic(context: Context, worker: Class<out ListenableWorker>, uniqueName: String, every: Duration = 1.hours) {
        val request = PeriodicWorkRequest.Builder(worker, every.toJavaDuration())
            .setConstraints(networkRequired())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(uniqueName, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** O fim da sessão: nada mais roda em nome de quem saiu. */
    public fun cancel(context: Context, uniqueName: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName)
    }

    private fun networkRequired(): Constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private const val BACKOFF_SECONDS = 30L
}

/** Uma rodada para o Worker do app: deu certo, ou tenta de novo pela espera do WorkManager. */
public suspend fun SyncEngine<*, *>.runAsWork(): ListenableWorker.Result =
    if (sync() == null) ListenableWorker.Result.success() else ListenableWorker.Result.retry()
