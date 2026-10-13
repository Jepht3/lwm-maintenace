package com.jephte.remoteupdater.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.jephte.remoteupdater.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Nouvelle version de l'application publiée sur GitHub. */
data class AppRelease(
    val version: String,
    val versionCode: Int,
    val apkUrl: String,
    val apkSha256: String,
    val changelog: List<String>,
)

/**
 * Mise à jour de l'application elle-même (hors Play Store) : lit latest.json joint à la
 * dernière version GitHub, télécharge l'APK, vérifie son empreinte puis ouvre l'installeur
 * Android (qui demande toujours confirmation).
 */
object AppUpdater {

    private const val MANIFEST = "https://github.com/Jepht3/lwm-maintenace/releases/latest/download/latest.json"

    /** Version plus récente que celle installée, ou null. */
    suspend fun check(): AppRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val o = JSONObject(get(MANIFEST).toString(Charsets.UTF_8))
            val code = o.getInt("version_code")
            if (code <= BuildConfig.VERSION_CODE) return@runCatching null
            val log = o.optJSONArray("changelog")
            AppRelease(
                version = o.getString("version"),
                versionCode = code,
                apkUrl = o.getString("apk_url"),
                apkSha256 = o.getString("apk_sha256").lowercase(),
                changelog = if (log == null) emptyList() else (0 until log.length()).map { log.getString(it) },
            )
        }.getOrNull()
    }

    /** Télécharge et vérifie l'APK. [onProgress] reçoit une fraction 0..1. */
    suspend fun download(context: Context, release: AppRelease, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "remote-updater-${release.version}.apk")
        val conn = open(release.apkUrl)
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        read += n
                        if (total != null) onProgress(read.toFloat() / total)
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash != release.apkSha256) {
                file.delete()
                throw ApiException("Le fichier téléchargé est corrompu (empreinte invalide).")
            }
        } finally {
            conn.disconnect()
        }
        file
    }

    /** Android exige que l'utilisateur autorise l'app à installer d'autres applis. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "RemoteUpdater-Android/${BuildConfig.VERSION_NAME}")
            if (responseCode !in 200..299) throw ApiException("Téléchargement impossible (HTTP $responseCode).")
        }

    private fun get(url: String): ByteArray {
        val conn = open(url)
        return try {
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}
