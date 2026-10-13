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
import com.jephte.remoteupdater.MainActivity
import com.jephte.remoteupdater.R

/** Notifications de l'application : nouvelles mises à jour et fin d'une série de mises à jour. */
object Notifier {

    private const val CHANNEL_AVAILABLE = "updates"
    private const val CHANNEL_RESULTS = "results"
    private const val ID_AVAILABLE = 1
    private const val ID_RESULTS_BASE = 100
    private const val ID_APP_UPDATE = 2

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_AVAILABLE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_AVAILABLE, "Mises à jour disponibles", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Alerte quand vos sites WordPress ont des mises à jour en attente."
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_RESULTS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RESULTS, "Résultats des mises à jour", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Fin d'une série de mises à jour lancée depuis l'application."
                },
            )
        }
    }

    private fun openApp(context: Context, request: Int) = PendingIntent.getActivity(
        context,
        request,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** « 9 mises à jour disponibles » (vérification en arrière-plan). */
    fun updatesAvailable(context: Context, total: Int, perSite: List<Pair<String, Int>>) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_AVAILABLE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2271B1.toInt())
            .setContentTitle("$total mise(s) à jour disponible(s)")
            .setContentText(perSite.joinToString { "${it.first} (${it.second})" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(perSite.joinToString("\n") { "${it.first} : ${it.second}" }))
            .setContentIntent(openApp(context, 0))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_AVAILABLE, n)
    }

    /** « Remote Updater 1.3.1 est disponible » (nouvelle version de l'app). */
    fun appUpdate(context: Context, release: AppRelease) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_AVAILABLE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2271B1.toInt())
            .setContentTitle("Remote Updater ${release.version} est disponible")
            .setContentText(release.changelog.firstOrNull() ?: "Ouvrez l'app pour installer la nouvelle version.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(release.changelog.joinToString("\n") { "• $it" }))
            .setContentIntent(openApp(context, 2))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_APP_UPDATE, n)
    }

    /** « Boutique Jephté : 8 mises à jour terminées » (fin de la file d'un site). */
    fun batchFinished(context: Context, siteName: String, ok: Int, failed: Int, siteKey: String) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val title = if (failed == 0) "$siteName est à jour" else "$siteName : $failed échec(s)"
        val text = if (failed == 0) "$ok mise(s) à jour installée(s) avec succès."
        else "$ok réussie(s), $failed en échec. Ouvrez l'app pour réessayer."
        val n = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2271B1.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(context, 1))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_RESULTS_BASE + (siteKey.hashCode() and 0xffff), n)
    }
}
