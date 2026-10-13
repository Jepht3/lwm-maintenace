package com.jephte.remoteupdater.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jephte.remoteupdater.MainActivity
import com.jephte.remoteupdater.R
import java.util.concurrent.TimeUnit

/**
 * Vérification périodique en arrière-plan : interroge chaque site et
 * envoie une notification quand de nouvelles mises à jour sont disponibles.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = AppSettings.get(applicationContext)
        if (!settings.notifications.value) return Result.success()

        val sites = SiteRepository(applicationContext).load()
        val perSite = sites.mapNotNull { site ->
            runCatching { site.name to WpApi(site).status().counts.total }.getOrNull()
        }.filter { it.second > 0 }

        val total = perSite.sumOf { it.second }
        if (total > 0 && total != settings.lastNotifiedTotal) {
            Notifier.updatesAvailable(applicationContext, total, perSite)
        }
        settings.lastNotifiedTotal = total
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "updates"
        private const val NOTIFICATION_ID = 1
        private const val WORK_NAME = "update-check"

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Mises à jour disponibles", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Alerte quand vos sites WordPress ont des mises à jour en attente."
                    },
                )
            }
        }

        /** Programme (ou reprogramme) la vérification selon les réglages. */
        fun schedule(context: Context) {
            val settings = AppSettings.get(context)
            val wm = WorkManager.getInstance(context)
            if (!settings.notifications.value) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(settings.checkHours.value.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
