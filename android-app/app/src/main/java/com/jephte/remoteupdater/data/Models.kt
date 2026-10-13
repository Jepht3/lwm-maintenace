package com.jephte.remoteupdater.data

import org.json.JSONArray
import org.json.JSONObject

/** Un site WordPress connecté à l'application. */
data class Site(
    val id: String,
    val name: String,
    val url: String,
    /** URL de base de l'API, ex. https://exemple.com/wp-json/wpru/v1/ */
    val restBase: String,
    val key: String,
)

data class Counts(
    val core: Int,
    val plugins: Int,
    val themes: Int,
    val translations: Int,
    val total: Int,
)

data class SiteInfo(
    val name: String,
    val url: String,
    val adminUrl: String,
    val wpVersion: String,
    val phpVersion: String,
    val multisite: Boolean,
    val sitesCount: Int,
    val fileModsAllowed: Boolean,
    val counts: Counts,
    val lastChecked: Long,
)

data class CoreInfo(
    val current: String,
    val updateAvailable: Boolean,
    val newVersion: String?,
)

data class PluginItem(
    val file: String,
    val name: String,
    val version: String,
    val author: String,
    val active: Boolean,
    val updateAvailable: Boolean,
    val newVersion: String?,
    val hasPackage: Boolean,
    val requiresPhp: String?,
    val tested: String?,
    val autoUpdate: Boolean,
    val isSelf: Boolean,
)

data class ThemeItem(
    val slug: String,
    val name: String,
    val version: String,
    val author: String,
    val active: Boolean,
    val updateAvailable: Boolean,
    val newVersion: String?,
    val hasPackage: Boolean,
    val autoUpdate: Boolean,
)

data class TranslationItem(
    val type: String,
    val slug: String,
    val language: String,
)

data class SiteData(
    val info: SiteInfo,
    val core: CoreInfo,
    val plugins: List<PluginItem>,
    val themes: List<ThemeItem>,
    val translations: List<TranslationItem>,
)

/** Résultat d'une mise à jour d'un élément (extension ou thème). */
data class ItemResult(
    val id: String,
    val name: String,
    val success: Boolean,
    val version: String?,
    val error: String?,
)

/* ------------------------------------------------------------------------
 * Parsing JSON (org.json, inclus dans Android)
 * --------------------------------------------------------------------- */

private fun JSONObject.str(name: String): String = optString(name, "").let { if (it == "null") "" else it }
private fun JSONObject.strOrNull(name: String): String? = if (isNull(name)) null else optString(name).ifEmpty { null }
private inline fun <T> JSONArray?.mapObjects(block: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).map { block(getJSONObject(it)) }

fun parseSiteInfo(o: JSONObject): SiteInfo {
    val c = o.optJSONObject("counts") ?: JSONObject()
    return SiteInfo(
        name = o.str("name"),
        url = o.str("url"),
        adminUrl = o.str("admin_url"),
        wpVersion = o.str("wp_version"),
        phpVersion = o.str("php_version"),
        multisite = o.optBoolean("multisite"),
        sitesCount = o.optInt("sites_count", 1),
        fileModsAllowed = o.optBoolean("file_mods", true),
        counts = Counts(
            core = c.optInt("core"),
            plugins = c.optInt("plugins"),
            themes = c.optInt("themes"),
            translations = c.optInt("translations"),
            total = c.optInt("total"),
        ),
        lastChecked = o.optLong("last_checked"),
    )
}

fun parseSiteData(o: JSONObject): SiteData {
    val core = o.getJSONObject("core")
    return SiteData(
        info = parseSiteInfo(o.getJSONObject("site")),
        core = CoreInfo(
            current = core.str("current"),
            updateAvailable = core.optBoolean("update_available"),
            newVersion = core.strOrNull("new_version"),
        ),
        plugins = o.optJSONArray("plugins").mapObjects {
            PluginItem(
                file = it.str("file"),
                name = it.str("name"),
                version = it.str("version"),
                author = it.str("author"),
                active = it.optBoolean("active"),
                updateAvailable = it.optBoolean("update_available"),
                newVersion = it.strOrNull("new_version"),
                hasPackage = it.optBoolean("package", true),
                requiresPhp = it.strOrNull("requires_php"),
                tested = it.strOrNull("tested"),
                autoUpdate = it.optBoolean("auto_update"),
                isSelf = it.optBoolean("self"),
            )
        },
        themes = o.optJSONArray("themes").mapObjects {
            ThemeItem(
                slug = it.str("slug"),
                name = it.str("name"),
                version = it.str("version"),
                author = it.str("author"),
                active = it.optBoolean("active"),
                updateAvailable = it.optBoolean("update_available"),
                newVersion = it.strOrNull("new_version"),
                hasPackage = it.optBoolean("package", true),
                autoUpdate = it.optBoolean("auto_update"),
            )
        },
        translations = o.optJSONArray("translations").mapObjects {
            TranslationItem(it.str("type"), it.str("slug"), it.str("language"))
        },
    )
}

fun parseItemResults(o: JSONObject, idField: String): List<ItemResult> =
    o.optJSONArray("results").mapObjects {
        ItemResult(
            id = it.str(idField),
            name = it.str("name"),
            success = it.optBoolean("success"),
            version = it.strOrNull("version"),
            error = it.strOrNull("error"),
        )
    }
