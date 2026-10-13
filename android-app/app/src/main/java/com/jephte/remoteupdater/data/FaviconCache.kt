package com.jephte.remoteupdater.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Favicons des sites connectés : téléchargées une fois depuis le site lui-même
 * (icône WordPress, sinon /favicon.ico), puis gardées sur le téléphone 7 jours.
 */
object FaviconCache {

    private val memory = mutableMapOf<String, Bitmap?>()
    private val lock = Mutex()
    private const val MAX_AGE_MS = 7L * 24 * 3600 * 1000

    /** Bitmap déjà en mémoire (évite un clignotement au défilement). */
    fun peek(siteUrl: String): Bitmap? = memory[siteUrl]

    suspend fun load(context: Context, siteUrl: String, iconUrl: String?): Bitmap? = lock.withLock {
        if (memory.containsKey(siteUrl) && memory[siteUrl] != null) return memory[siteUrl]
        val bmp = withContext(Dispatchers.IO) { loadFromDiskOrNetwork(context, siteUrl, iconUrl) }
        memory[siteUrl] = bmp
        bmp
    }

    private fun loadFromDiskOrNetwork(context: Context, siteUrl: String, iconUrl: String?): Bitmap? {
        val dir = File(context.cacheDir, "favicons").apply { mkdirs() }
        val file = File(dir, md5(siteUrl) + ".png")
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS) {
            BitmapFactory.decodeFile(file.path)?.let { return it }
        }
        val candidates = buildList {
            if (!iconUrl.isNullOrBlank()) add(iconUrl)
            add(siteUrl.trimEnd('/') + "/favicon.ico")
        }
        for (u in candidates) {
            val bmp = download(u) ?: continue
            runCatching { file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            return bmp
        }
        return null
    }

    private fun download(url: String): Bitmap? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "RemoteUpdater-Android/1.0")
        try {
            if (conn.responseCode !in 200..299) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.size > 2_000_000) return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.takeIf { it.width >= 8 && it.height >= 8 }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
