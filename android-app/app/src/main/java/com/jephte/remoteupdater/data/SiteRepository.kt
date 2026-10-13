package com.jephte.remoteupdater.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stockage local des sites connectés. Les clés d'accès sont chiffrées
 * avec une clé AES conservée dans l'Android Keystore.
 */
class SiteRepository(context: Context) {

    private val prefs = context.getSharedPreferences("sites", Context.MODE_PRIVATE)

    fun load(): List<Site> {
        val raw = prefs.getString(KEY_SITES, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val key = runCatching { Crypto.decrypt(o.getString("key")) }.getOrNull() ?: return@mapNotNull null
            Site(
                id = o.getString("id"),
                name = o.getString("name"),
                url = o.getString("url"),
                restBase = o.getString("rest"),
                key = key,
                folderId = o.optString("folder").ifEmpty { null },
            )
        }
    }

    fun save(sites: List<Site>) {
        val arr = JSONArray()
        sites.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("url", it.url)
                    .put("rest", it.restBase)
                    .put("key", Crypto.encrypt(it.key))
                    .put("folder", it.folderId ?: ""),
            )
        }
        prefs.edit().putString(KEY_SITES, arr.toString()).apply()
    }

    companion object {
        private const val KEY_SITES = "sites"

        /**
         * Décode le code affiché par l'extension : "wpru:" + base64url(JSON {url, rest, key, name}).
         * Retourne null si le code est invalide.
         */
        fun parseConnectionCode(code: String): Site? {
            val trimmed = code.trim()
            if (!trimmed.startsWith("wpru:")) return null
            return runCatching {
                val json = String(
                    Base64.decode(trimmed.removePrefix("wpru:"), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
                )
                val o = JSONObject(json)
                val url = o.getString("url")
                val key = o.getString("key")
                val rest = o.optString("rest").ifEmpty { restFromUrl(url) }
                Site(
                    id = UUID.randomUUID().toString(),
                    name = o.optString("name").ifEmpty { hostOf(url) },
                    url = url,
                    restBase = if (rest.endsWith("/")) rest else "$rest/",
                    key = key,
                )
            }.getOrNull()
        }

        /** Connexion manuelle : adresse du site + clé d'accès. */
        fun manualSite(url: String, key: String): Site? {
            var u = url.trim()
            if (u.isEmpty() || key.isBlank()) return null
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
            if (!u.endsWith("/")) u += "/"
            return Site(UUID.randomUUID().toString(), hostOf(u), u, restFromUrl(u), key.trim())
        }

        private fun restFromUrl(url: String) = url.trimEnd('/') + "/wp-json/wpru/v1/"

        fun hostOf(url: String): String =
            url.removePrefix("https://").removePrefix("http://").trimEnd('/')
    }
}

private object Crypto {
    private const val ALIAS = "remote_updater_sites"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, data, 0, 12))
        return String(cipher.doFinal(data, 12, data.size - 12))
    }
}
