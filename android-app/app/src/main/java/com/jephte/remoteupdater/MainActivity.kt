package com.jephte.remoteupdater

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.jephte.remoteupdater.data.AppLock
import com.jephte.remoteupdater.data.AppSettings
import com.jephte.remoteupdater.data.ThemeMode
import com.jephte.remoteupdater.data.UpdateCheckWorker
import com.jephte.remoteupdater.ui.AppRoot
import com.jephte.remoteupdater.ui.AppTheme
import com.jephte.remoteupdater.ui.AppViewModel
import com.jephte.remoteupdater.ui.OnboardingScreen
import com.jephte.remoteupdater.ui.PinMode
import com.jephte.remoteupdater.ui.PinScreen

class MainActivity : FragmentActivity() {

    private val vm: AppViewModel by viewModels()
    private lateinit var lock: AppLock
    private lateinit var settings: AppSettings

    /** L'app est verrouillée au lancement et après un délai en arrière-plan (réglable). */
    private var locked by mutableStateOf(true)
    private var onboarding by mutableStateOf(false)
    private var hasPin by mutableStateOf(false)
    private var backgroundedAt = 0L
    private var skipNextRelock = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Sortie de l'écran de démarrage : le logo grossit pendant que l'écran s'efface.
        splash.setOnExitAnimationListener { provider ->
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(provider.iconView, View.SCALE_X, 1f, 1.4f),
                    ObjectAnimator.ofFloat(provider.iconView, View.SCALE_Y, 1f, 1.4f),
                    ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f),
                )
                duration = 400L
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = provider.remove()
                })
                start()
            }
        }

        lock = AppLock(this)
        settings = AppSettings.get(this)
        hasPin = lock.hasPin
        locked = savedInstanceState?.getBoolean("locked", true) ?: true
        onboarding = !settings.onboardingDone && !hasPin
        UpdateCheckWorker.schedule(this)

        setContent {
            val themeMode by settings.themeMode.collectAsState()
            LaunchedEffect(locked, onboarding, hasPin, themeMode) {
                val lightIcons = locked || onboarding || !hasPin || themeMode == ThemeMode.DARK
                enableEdgeToEdge(
                    statusBarStyle = if (lightIcons) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
            }

            AppTheme(themeMode) {
                val stage = when {
                    onboarding -> "onboarding"
                    !hasPin -> "setup"
                    else -> "app"
                }
                AnimatedContent(
                    targetState = stage,
                    transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) },
                    label = "stage",
                ) { s ->
                    when (s) {
                        "onboarding" -> OnboardingScreen(onFinish = {
                            settings.onboardingDone = true
                            onboarding = false
                        })
                        "setup" -> PinScreen(
                            mode = PinMode.SETUP,
                            lock = lock,
                            biometricAvailable = biometricAvailable(),
                            onBiometric = {},
                            onDone = {
                                locked = false
                                hasPin = true
                            },
                        )
                        else -> Box(Modifier.fillMaxSize()) {
                            // L'app reste composée sous l'écran de verrouillage pour ne pas perdre
                            // l'état (fichier choisi, écran ouvert…) lors d'un reverrouillage.
                            AppRoot(
                                vm = vm,
                                settings = settings,
                                lock = lock,
                                biometricAvailable = biometricAvailable(),
                                onBiometric = ::showBiometricPrompt,
                                onLockNow = { locked = true },
                            )
                            if (locked) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clearAndSetSemantics {}
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) awaitPointerEvent().changes.forEach { it.consume() }
                                            }
                                        },
                                ) {
                                    PinScreen(
                                        mode = PinMode.UNLOCK,
                                        lock = lock,
                                        biometricAvailable = biometricAvailable(),
                                        onBiometric = ::showBiometricPrompt,
                                        onDone = { locked = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("locked", locked)
    }

    /** Appelé avant d'ouvrir le sélecteur de fichiers ou le scanner : pas de reverrouillage au retour. */
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
        if (hasPin && backgroundedAt > 0 &&
            SystemClock.elapsedRealtime() - backgroundedAt >= settings.relockDelayMs.value
        ) {
            locked = true
        }
    }

    private fun biometricAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    private fun showBiometricPrompt() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    lock.resetFailures()
                    locked = false
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Déverrouiller Remote Updater")
                .setSubtitle("Confirmez votre identité")
                .setNegativeButtonText("Utiliser le code PIN")
                .setAllowedAuthenticators(BIOMETRIC_WEAK)
                .build(),
        )
    }
}
