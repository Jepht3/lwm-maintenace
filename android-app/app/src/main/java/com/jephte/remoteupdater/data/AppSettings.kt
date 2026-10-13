package com.jephte.remoteupdater.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Préférences de l'application, observables depuis l'interface. */
class AppSettings private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _relockDelayMs = MutableStateFlow(prefs.getLong("relock_delay", 30_000L))
    val relockDelayMs: StateFlow<Long> = _relockDelayMs

    private val _notifications = MutableStateFlow(prefs.getBoolean("notifications", true))
    val notifications: StateFlow<Boolean> = _notifications

    private val _checkHours = MutableStateFlow(prefs.getInt("check_hours", 12))
    val checkHours: StateFlow<Int> = _checkHours

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) = prefs.edit().putBoolean("onboarding_done", value).apply()

    /** Dernier total notifié, pour ne pas renvoyer la même notification. */
    var lastNotifiedTotal: Int
        get() = prefs.getInt("last_notified", 0)
        set(value) = prefs.edit().putInt("last_notified", value).apply()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    fun setRelockDelay(ms: Long) {
        _relockDelayMs.value = ms
        prefs.edit().putLong("relock_delay", ms).apply()
    }

    fun setNotifications(enabled: Boolean) {
        _notifications.value = enabled
        prefs.edit().putBoolean("notifications", enabled).apply()
    }

    fun setCheckHours(hours: Int) {
        _checkHours.value = hours
        prefs.edit().putInt("check_hours", hours).apply()
    }

    companion object {
        @Volatile private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }

        val RELOCK_OPTIONS = listOf(
            0L to "Immédiatement",
            30_000L to "Après 30 secondes",
            60_000L to "Après 1 minute",
            300_000L to "Après 5 minutes",
        )

        val CHECK_OPTIONS = listOf(6 to "Toutes les 6 h", 12 to "Toutes les 12 h", 24 to "Une fois par jour")
    }
}
