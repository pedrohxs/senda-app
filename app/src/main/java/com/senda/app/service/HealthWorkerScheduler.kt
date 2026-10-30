package com.senda.app.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Agenda a verificação periódica de saúde da proteção.
 *
 * O intervalo mínimo aceito pelo WorkManager é de 15 minutos, o que é
 * suficiente: o watchdog interno do serviço continua detectando a queda da
 * acessibilidade a cada 30 segundos.
 *
 * O agendamento roda fora da main thread de propósito: `WorkManager.getInstance`
 * inicializa um banco próprio e, feito no `Application.onCreate`, atrasava a
 * abertura do app a ponto de gerar ANR "No response to onStartJob" quando o job
 * disparava junto com o boot.
 */
object HealthWorkerScheduler {

    private const val UNIQUE_WORK_NAME = "senda-health-check"
    private const val INTERVAL_MINUTES = 15L

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "senda-health-scheduler").apply { isDaemon = true }
    }

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        executor.execute {
            val request = PeriodicWorkRequestBuilder<SendaHealthWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()

            runCatching {
                WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }
        }
    }
}
