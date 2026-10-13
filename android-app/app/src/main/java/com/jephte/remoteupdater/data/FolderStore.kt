package com.jephte.remoteupdater.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Dossier (client) regroupant plusieurs sites. */
data class Folder(
    val id: String,
    val name: String,
    /** Index dans la palette [FolderStore.COLORS]. */
    val color: Int = 0,
)

/** Stockage local des dossiers clients. */
class FolderStore(context: Context) {

    private val prefs = context.getSharedPreferences("folders", Context.MODE_PRIVATE)

    fun load(): List<Folder> {
        val arr = JSONArray(prefs.getString("folders", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Folder(o.getString("id"), o.getString("name"), o.optInt("color"))
        }
    }

    fun save(folders: List<Folder>) {
        val arr = JSONArray()
        folders.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
        prefs.edit().putString("folders", arr.toString()).apply()
    }

    companion object {
        /** Couleurs unies proposées pour les dossiers (ARGB). */
        val COLORS = listOf(
            0xFF2271B1, // bleu WordPress
            0xFF00A32A, // vert
            0xFFB26200, // orange
            0xFFD63638, // rouge
            0xFF8C5383, // violet
            0xFF50575E, // gris
        )
    }
}
