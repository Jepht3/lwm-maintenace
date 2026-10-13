package com.jephte.remoteupdater.data

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Compte local de l'application : nom, numéro de téléphone et mot de passe.
 * Pas de serveur : tout est stocké sur le téléphone, le mot de passe sous
 * forme de hash PBKDF2 salé (jamais en clair).
 */
class AccountStore(context: Context) {

    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)

    val hasAccount: Boolean get() = prefs.contains(KEY_HASH)
    val name: String get() = prefs.getString(KEY_NAME, "").orEmpty()
    val phone: String get() = prefs.getString(KEY_PHONE, "").orEmpty()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIO, false)
        set(value) = prefs.edit().putBoolean(KEY_BIO, value).apply()

    /** Crée le compte. Retourne un message d'erreur ou null. */
    fun register(name: String, phone: String, password: String): String? {
        val p = normalizePhone(phone) ?: return "Numéro de téléphone invalide."
        if (name.isBlank()) return "Indiquez votre nom."
        passwordProblem(password)?.let { return it }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_NAME, name.trim())
            .putString(KEY_PHONE, p)
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash(password, salt), Base64.NO_WRAP))
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCKED_UNTIL, 0)
            .apply()
        return null
    }

    /** Millisecondes restantes avant de pouvoir réessayer (0 = autorisé). */
    fun remainingLockout(): Long = (prefs.getLong(KEY_LOCKED_UNTIL, 0) - System.currentTimeMillis()).coerceAtLeast(0)

    /** Vérifie téléphone + mot de passe. Retourne null si OK, sinon le message d'erreur. */
    fun login(phone: String, password: String): String? {
        val wait = remainingLockout()
        if (wait > 0) return "Trop de tentatives. Réessayez dans ${(wait + 999) / 1000} s."
        val ok = normalizePhone(phone) == this.phone && checkPassword(password)
        if (ok) {
            resetFailures()
            return null
        }
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val edit = prefs.edit().putInt(KEY_FAILS, fails)
        if (fails >= MAX_FAILS) {
            // 30 s, puis 1 min, 2 min, 4 min… (plafonné à 1 h)
            val delay = (30_000L shl (fails - MAX_FAILS).coerceAtMost(7)).coerceAtMost(3_600_000L)
            edit.putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + delay)
        }
        edit.apply()
        val left = MAX_FAILS - fails
        return if (left > 0) "Numéro ou mot de passe incorrect ($left essai(s) avant blocage temporaire)."
        else "Numéro ou mot de passe incorrect. Accès bloqué temporairement."
    }

    fun changePassword(current: String, new: String): String? {
        if (!checkPassword(current)) return "Mot de passe actuel incorrect."
        passwordProblem(new)?.let { return it }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash(new, salt), Base64.NO_WRAP))
            .apply()
        return null
    }

    fun updateName(name: String) {
        if (name.isNotBlank()) prefs.edit().putString(KEY_NAME, name.trim()).apply()
    }

    fun resetFailures() {
        prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCKED_UNTIL, 0).apply()
    }

    /** Supprime le compte (les sites sont effacés séparément). */
    fun delete() {
        prefs.edit().clear().apply()
    }

    private fun checkPassword(password: String): Boolean {
        val salt = Base64.decode(prefs.getString(KEY_SALT, "") ?: "", Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString(KEY_HASH, "") ?: "", Base64.NO_WRAP)
        return expected.isNotEmpty() && MessageDigest.isEqual(hash(password, salt), expected)
    }

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    companion object {
        private const val KEY_NAME = "name"
        private const val KEY_PHONE = "phone"
        private const val KEY_HASH = "pwd_hash"
        private const val KEY_SALT = "pwd_salt"
        private const val KEY_BIO = "biometric"
        private const val KEY_FAILS = "fails"
        private const val KEY_LOCKED_UNTIL = "locked_until"
        private const val MAX_FAILS = 5

        /** « +33 6 12 34 56 78 » → « +33612345678 » ; null si invalide. */
        fun normalizePhone(raw: String): String? {
            val digits = raw.filter { it.isDigit() }
            val plus = raw.trim().startsWith("+") || raw.trim().startsWith("00")
            val d = if (raw.trim().startsWith("00")) digits.drop(2) else digits
            if (d.length !in 8..15) return null
            return (if (plus) "+" else "") + d
        }

        fun passwordProblem(p: String): String? = when {
            p.length < 8 -> "Le mot de passe doit contenir au moins 8 caractères."
            p.none { it.isDigit() } || p.none { it.isLetter() } -> "Utilisez au moins une lettre et un chiffre."
            else -> null
        }

        /** Force du mot de passe de 0 à 4 (indicateur visuel). */
        fun strength(p: String): Int {
            var s = 0
            if (p.length >= 8) s++
            if (p.length >= 12) s++
            if (p.any { it.isDigit() } && p.any { it.isLetter() }) s++
            if (p.any { !it.isLetterOrDigit() } || (p.any { it.isUpperCase() } && p.any { it.isLowerCase() })) s++
            return s
        }
    }
}
