package com.jephte.remoteupdater.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Une action effectuée depuis l'application (mise à jour, téléversement…). */
data class LogEntry(
    val time: Long,
    val site: String,
    val text: String,
    val ok: Boolean,
)

/** Historique local des actions (200 dernières entrées). */
class ActivityLog(context: Context) {

    private val prefs = context.getSharedPreferences("activity", Context.MODE_PRIVATE)

    fun load(): List<LogEntry> {
        val arr = JSONArray(prefs.getString("entries", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            LogEntry(o.getLong("t"), o.getString("s"), o.getString("m"), o.getBoolean("ok"))
        }
    }

    fun save(entries: List<LogEntry>) {
        val arr = JSONArray()
        entries.take(MAX).forEach {
            arr.put(JSONObject().put("t", it.time).put("s", it.site).put("m", it.text).put("ok", it.ok))
        }
        prefs.edit().putString("entries", arr.toString()).apply()
    }

    companion object {
        const val MAX = 200
    }
}
