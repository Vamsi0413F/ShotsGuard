package com.example.shortsguard

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Backup for the service-off alert: every ~15 minutes, check whether the accessibility service is
 * still enabled and notify if it was on before and is now off.
 */
class AlertWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (prefs.serviceWasOn && !Util.isServiceEnabled(applicationContext)) {
            Status.createChannels(applicationContext)
            Status.showServiceOff(applicationContext)
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "service_check",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
