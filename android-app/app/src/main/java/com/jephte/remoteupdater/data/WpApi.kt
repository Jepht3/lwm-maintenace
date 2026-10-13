package com.jephte.remoteupdater.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class ApiException(message: String, val status: Int = 0) : Exception(message)

/** Client de l'API REST de l'extension « Remote Updater » (namespace wpru/v1). */
class WpApi(private val restBase: String, private val key: String) {

    constructor(site: Site) : this(site.restBase, site.key)

    suspend fun status(): SiteInfo = parseSiteInfo(request("GET", "status"))

    suspend fun updates(refresh: Boolean = false): SiteData =
        parseSiteData(request("GET", "updates", query = if (refresh) "refresh=1" else null, readTimeoutSec = 90))

    /** [files] vide = toutes les extensions ayant une mise à jour. */
    suspend fun updatePlugins(files: List<String>): List<ItemResult> =
        parseItemResults(
            request("POST", "update/plugins", JSONObject().put("plugins", JSONArray(files)), readTimeoutSec = LONG_TIMEOUT),
            "file",
        )

    /** [slugs] vide = tous les thèmes ayant une mise à jour. */
    suspend fun updateThemes(slugs: List<String>): List<ItemResult> =
        parseItemResults(
            request("POST", "update/themes", JSONObject().put("themes", JSONArray(slugs)), readTimeoutSec = LONG_TIMEOUT),
            "slug",
        )

    suspend fun updateCore(): String =
        request("POST", "update/core", JSONObject(), readTimeoutSec = LONG_TIMEOUT).optString("message")

    suspend fun updateTranslations(): String =
        request("POST", "update/translations", JSONObject(), readTimeoutSec = LONG_TIMEOUT).optString("message")

    /** Mise à jour manuelle : envoie un .zip qui remplace l'extension / le thème installé. */
    suspend fun uploadPackage(type: String, fileName: String, bytes: ByteArray): UploadResult {
        val o = request(
            "POST",
            "upload",
            multipart = Multipart(mapOf("type" to type), "package", fileName, bytes),
            readTimeoutSec = LONG_TIMEOUT,
        )
        return UploadResult(
            name = o.optString("name"),
            oldVersion = o.optString("old_version").takeIf { it.isNotEmpty() && it != "null" },
            version = o.optString("version").takeIf { it.isNotEmpty() && it != "null" },
            message = o.optString("message"),
        )
    }

    suspend fun setAutoUpdate(type: String, item: String, enabled: Boolean) {
        request("POST", "auto-update", JSONObject().put("type", type).put("item", item).put("enabled", enabled))
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        query: String? = null,
        multipart: Multipart? = null,
        readTimeoutSec: Int = 30,
    ): JSONObject = withContext(Dispatchers.IO) {
        var url = restBase + path
        if (query != null) url += (if ('?' in url) "&" else "?") + query

        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw ApiException("Adresse du site invalide.")
        }

        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = readTimeoutSec * 1000
            conn.setRequestProperty("X-WPRU-Key", key)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "RemoteUpdater-Android/1.0")
            conn.useCaches = false
            if (multipart != null) {
                multipart.writeTo(conn)
            } else if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            // Certains hébergeurs ajoutent du texte avant le JSON (avertissements PHP…)
            val json = text.indexOf('{').let { if (it >= 0) text.substring(it) else text }
            val obj = try {
                JSONObject(json)
            } catch (e: Exception) {
                throw ApiException(
                    when (code) {
                        404 -> "API introuvable. L'extension Remote Updater est-elle activée ?"
                        in 200..299 -> "Réponse inattendue du site."
                        else -> "Erreur HTTP $code."
                    },
                    code,
                )
            }

            if (code !in 200..299) {
                val msg = obj.optString("message").ifEmpty { "Erreur HTTP $code." }
                throw ApiException(
                    when (obj.optString("code")) {
                        "rest_no_route" -> "API introuvable. L'extension Remote Updater est-elle activée ?"
                        else -> msg
                    },
                    code,
                )
            }
            obj
        } catch (e: ApiException) {
            throw e
        } catch (e: UnknownHostException) {
            throw ApiException("Site injoignable (adresse inconnue).")
        } catch (e: SocketTimeoutException) {
            throw ApiException("Le site met trop de temps à répondre. L'opération continue peut-être sur le serveur : actualisez dans un instant.")
        } catch (e: SSLException) {
            throw ApiException("Erreur de certificat HTTPS : ${e.message}")
        } catch (e: IOException) {
            throw ApiException("Erreur réseau : ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val LONG_TIMEOUT = 300
    }
}

data class UploadResult(
    val name: String,
    val oldVersion: String?,
    val version: String?,
    val message: String,
)

/** Corps multipart/form-data minimal (champs texte + un fichier). */
class Multipart(
    private val fields: Map<String, String>,
    private val fileField: String,
    private val fileName: String,
    private val bytes: ByteArray,
) {
    fun writeTo(conn: HttpURLConnection) {
        val boundary = "----RemoteUpdater" + System.currentTimeMillis()
        val safeName = fileName.replace("\"", "")
        val head = buildString {
            fields.forEach { (k, v) ->
                append("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n")
            }
            append("--$boundary\r\nContent-Disposition: form-data; name=\"$fileField\"; filename=\"$safeName\"\r\n")
            append("Content-Type: application/zip\r\n\r\n")
        }.toByteArray()
        val tail = "\r\n--$boundary--\r\n".toByteArray()

        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.setFixedLengthStreamingMode(head.size.toLong() + bytes.size + tail.size)
        conn.outputStream.use {
            it.write(head)
            it.write(bytes)
            it.write(tail)
        }
    }
}
