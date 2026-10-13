package com.jephte.remoteupdater

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.jephte.remoteupdater.data.AccountStore
import com.jephte.remoteupdater.data.AppLock
import com.jephte.remoteupdater.data.AppSettings
import com.jephte.remoteupdater.data.Notifier
import com.jephte.remoteupdater.data.ThemeMode
import com.jephte.remoteupdater.data.UpdateCheckWorker
import com.jephte.remoteupdater.ui.AppRoot
import com.jephte.remoteupdater.ui.AppTheme
import com.jephte.remoteupdater.ui.AppViewModel
import com.jephte.remoteupdater.ui.LoginScreen
import com.jephte.remoteupdater.ui.PinMode
import com.jephte.remoteupdater.ui.PinScreen
import com.jephte.remoteupdater.ui.RegisterScreen
import com.jephte.remoteupdater.ui.WelcomeScreen

class MainActivity : FragmentActivity() {

    private val vm: AppViewModel by viewModels()
    private lateinit var account: AccountStore
    private lateinit var lock: AppLock
    private lateinit var settings: AppSettings

    /** Incrémenté à chaque changement d'état d'authentification (force la recomposition). */
    private var authVersion by mutableStateOf(0)
    private var locked by mutableStateOf(true)
    private var registering by mutableStateOf(false)
    private var backgroundedAt = 0L
    private var skipNextRelock = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setOnExitAnimationListener { p ->
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(p.iconView, View.SCALE_X, 1f, 1.3f),
                    ObjectAnimator.ofFloat(p.iconView, View.SCALE_Y, 1f, 1.3f),
                    ObjectAnimator.ofFloat(p.view, View.ALPHA, 1f, 0f),
                )
                duration = 350L
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = p.remove()
                })
                start()
            }
        }

        account = AccountStore(this)
        lock = AppLock(this)
        settings = AppSettings.get(this)
        locked = savedInstanceState?.getBoolean("locked", true) ?: true
        UpdateCheckWorker.schedule(this)

        setContent {
            val themeMode by settings.themeMode.collectAsState()
            @Suppress("UNUSED_EXPRESSION") authVersion
            val stage = when {
                !account.hasAccount -> if (registering) "register" else "welcome"
                !lock.loggedIn -> "login"
                !lock.hasPin -> "pin-setup"
                else -> "app"
            }
            val darkChrome = stage != "register" && stage != "login" && (stage != "app" || locked) || themeMode == ThemeMode.DARK
            LaunchedEffect(darkChrome) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkChrome) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
            }

            // Demande d'autorisation des notifications à la première ouverture de l'app
            val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                settings.setNotifications(granted)
                UpdateCheckWorker.schedule(this)
            }
            LaunchedEffect(stage) {
                if (stage == "app" && Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(this@MainActivity) && !settings.notificationAsked) {
                    settings.notificationAsked = true
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            AppTheme(themeMode) {
                AnimatedContent(stage, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "stage") { s ->
                    when (s) {
                        "welcome" -> WelcomeScreen(onCreate = { registering = true })
                        "register" -> RegisterScreen(
                            account,
                            onBack = { registering = false },
                            onDone = {
                                registering = false
                                lock.loggedIn = true
                                locked = false
                                authVersion++
                            },
                        )
                        "login" -> LoginScreen(
                            account,
                            pinBlocked = lock.pinBlocked,
                            onLoggedIn = {
                                lock.loggedIn = true
                                lock.resetFailures()
                                locked = false
                                authVersion++
                            },
                            onReset = ::resetEverything,
                        )
                        "pin-setup" -> PinScreen(
                            PinMode.SETUP, lock, account.name, biometricAvailable(), onBiometric = {},
                            onDone = {
                                locked = false
                                authVersion++
                            },
                        )
                        else -> Box(Modifier.fillMaxSize()) {
                            // L'app reste composée sous l'écran de verrouillage : rien n'est perdu au reverrouillage.
                            AppRoot(
                                vm, settings, account, lock, biometricAvailable(), ::showBiometricPrompt,
                                onLockNow = { locked = true },
                                onLogout = {
                                    lock.loggedIn = false
                                    locked = true
                                    authVersion++
                                },
                                onDeleteAccount = ::resetEverything,
                            )
                            if (locked) {
                                Box(
                                    Modifier.fillMaxSize().clearAndSetSemantics {}.pointerInput(Unit) {
                                        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                                    },
                                ) {
                                    PinScreen(
                                        PinMode.UNLOCK, lock, account.name, biometricAvailable(), ::showBiometricPrompt,
                                        onDone = { locked = false },
                                        onUsePassword = {
                                            lock.loggedIn = false
                                            authVersion++
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Efface le compte, le PIN, les sites et l'historique. */
    private fun resetEverything() {
        vm.wipeAll()
        lock.clear()
        account.delete()
        registering = false
        locked = true
        authVersion++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("locked", locked)
    }

    /** Avant d'ouvrir le sélecteur de fichiers ou le scanner : pas de reverrouillage au retour. */
    fun expectExternalActivity() {
        skipNextRelock = true
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (skipNextRelock) {
            skipNextRelock = false
            return
        }
        if (lock.hasPin && backgroundedAt > 0 && SystemClock.elapsedRealtime() - backgroundedAt >= settings.relockDelayMs.value) {
            locked = true
        }
    }

    private fun biometricAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    private fun showBiometricPrompt() {
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    lock.resetFailures()
                    locked = false
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Déverrouiller Remote Updater")
                .setSubtitle("Confirmez votre identité")
                .setNegativeButtonText("Utiliser le code PIN")
                .setAllowedAuthenticators(BIOMETRIC_WEAK)
                .build(),
        )
    }
}
