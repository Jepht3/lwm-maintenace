package com.jephte.remoteupdater.data

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Déverrouillage rapide par code PIN à 4 chiffres (haché PBKDF2 + sel).
 * Après [MAX_FAILS] erreurs, le PIN est bloqué : il faut se reconnecter
 * avec le numéro de téléphone et le mot de passe du compte.
 */
class AppLock(context: Context) {

    private val prefs = context.getSharedPreferences("lock", Context.MODE_PRIVATE)

    val hasPin: Boolean get() = prefs.contains(KEY_HASH)

    /** Vrai quand le PIN a été bloqué après trop d'erreurs. */
    val pinBlocked: Boolean get() = prefs.getInt(KEY_FAILS, 0) >= MAX_FAILS

    val failures: Int get() = prefs.getInt(KEY_FAILS, 0)

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIO, false)
        set(value) = prefs.edit().putBoolean(KEY_BIO, value).apply()

    /** Session ouverte (connexion par mot de passe effectuée et non déconnectée). */
    var loggedIn: Boolean
        get() = prefs.getBoolean(KEY_SESSION, false)
        set(value) = prefs.edit().putBoolean(KEY_SESSION, value).apply()

    fun setPin(pin: String) {
        require(pin.length == PIN_LENGTH && pin.all { it.isDigit() })
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .putInt(KEY_FAILS, 0)
            .apply()
    }

    /** Vérifie le PIN sans compter d'échec (changement de PIN). */
    fun matches(pin: String): Boolean {
        val salt = Base64.decode(prefs.getString(KEY_SALT, "") ?: "", Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString(KEY_HASH, "") ?: "", Base64.NO_WRAP)
        return expected.isNotEmpty() && MessageDigest.isEqual(hash(pin, salt), expected)
    }

    /** Déverrouillage : compte les échecs ; au 5ᵉ, la session est fermée. */
    fun checkPin(pin: String): Boolean {
        if (pinBlocked) return false
        if (matches(pin)) {
            resetFailures()
            return true
        }
        val fails = failures + 1
        prefs.edit().putInt(KEY_FAILS, fails).apply()
        if (fails >= MAX_FAILS) loggedIn = false
        return false
    }

    fun resetFailures() {
        prefs.edit().putInt(KEY_FAILS, 0).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 60_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    companion object {
        const val PIN_LENGTH = 4
        const val MAX_FAILS = 5
        private const val KEY_HASH = "pin4_hash"
        private const val KEY_SALT = "pin4_salt"
        private const val KEY_BIO = "biometric"
        private const val KEY_FAILS = "pin_fails"
        private const val KEY_SESSION = "session"
    }
}
