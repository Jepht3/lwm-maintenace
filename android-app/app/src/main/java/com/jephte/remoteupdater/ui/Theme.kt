package com.jephte.remoteupdater.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jephte.remoteupdater.R
import com.jephte.remoteupdater.data.ThemeMode

/*
 * Design « WordPress Pro » : couleurs unies (aucun dégradé), bleu admin WordPress
 * comme unique couleur d'action, vert / orange / rouge réservés aux états.
 */

@Immutable
data class UiColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val border: Color,
    val divider: Color,
    val text: Color,
    val textMuted: Color,
    val textSubtle: Color,
    /** Bouton principal (toujours le bleu WordPress). */
    val primary: Color,
    /** Liens et icônes actives. */
    val accent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val track: Color,
    val runningBg: Color,
    val runningBorder: Color,
    val success: Color,
    val successText: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerText: Color,
    val dangerSoft: Color,
    val dangerBorder: Color,
    val neutralSoft: Color,
    /** Carte de synthèse et écrans d'authentification (gris barre d'admin). */
    val hero: Color,
    val heroBorder: Color,
    val heroMuted: Color,
    val heroTrack: Color,
    val heroAccent: Color,
    val heroKey: Color,
    val chipDark: Color,
    val onChipDark: Color,
)

object Wp {
    val Blue = Color(0xFF2271B1)
    val Blue60 = Color(0xFF135E96)
    val Blue30 = Color(0xFF72AEE6)
    val Gray100 = Color(0xFF101517)
    val Gray90 = Color(0xFF1D2327)
    val Gray80 = Color(0xFF2C3338)
    val Gray70 = Color(0xFF3C434A)
    val Gray60 = Color(0xFF50575E)
    val Gray50 = Color(0xFF646970)
    val Gray20 = Color(0xFFA7AAAD)
    val Gray10 = Color(0xFFC3C4C7)
    val Gray5 = Color(0xFFDCDCDE)
    val Gray2 = Color(0xFFF0F0F1)
    val Gray0 = Color(0xFFF6F7F7)
    val Red = Color(0xFFD63638)
}

val LightUi = UiColors(
    isDark = false,
    bg = Wp.Gray0,
    surface = Color.White,
    border = Color(0xFFE4E6E8),
    divider = Color(0xFFEEEFF0),
    text = Wp.Gray90,
    textMuted = Wp.Gray60,
    textSubtle = Wp.Gray50,
    primary = Wp.Blue,
    accent = Wp.Blue,
    accentSoft = Color(0xFFF0F6FC),
    onAccentSoft = Wp.Blue60,
    track = Color(0xFFE6EFF8),
    runningBg = Color(0xFFF7FAFD),
    runningBorder = Color(0xFFC5D9ED),
    success = Color(0xFF008A20),
    successText = Color(0xFF007017),
    successSoft = Color(0xFFEDFAEF),
    warning = Color(0xFF996800),
    warningSoft = Color(0xFFFCF9E8),
    danger = Wp.Red,
    dangerText = Color(0xFFB32D2E),
    dangerSoft = Color(0xFFFCF0F1),
    dangerBorder = Color(0xFFF1ADAE),
    neutralSoft = Wp.Gray2,
    hero = Wp.Gray90,
    heroBorder = Wp.Gray90,
    heroMuted = Wp.Gray10,
    heroTrack = Wp.Gray70,
    heroAccent = Wp.Blue30,
    heroKey = Wp.Gray80,
    chipDark = Wp.Gray90,
    onChipDark = Color.White,
)

val DarkUi = UiColors(
    isDark = true,
    bg = Wp.Gray100,
    surface = Wp.Gray90,
    border = Wp.Gray80,
    divider = Wp.Gray80,
    text = Wp.Gray2,
    textMuted = Wp.Gray20,
    textSubtle = Wp.Gray20,
    primary = Wp.Blue,
    accent = Wp.Blue30,
    accentSoft = Color(0xFF0E3A5C),
    onAccentSoft = Color(0xFF9EC2E6),
    track = Wp.Gray80,
    runningBg = Color(0xFF12293D),
    runningBorder = Color(0xFF0E3A5C),
    success = Color(0xFF68DE7C),
    successText = Color(0xFF68DE7C),
    successSoft = Color(0xFF0B2E14),
    warning = Color(0xFFF0C33C),
    warningSoft = Color(0xFF3A2A00),
    danger = Color(0xFFF86368),
    dangerText = Color(0xFFFFABAF),
    dangerSoft = Color(0xFF3A1416),
    dangerBorder = Color(0xFF5C2023),
    neutralSoft = Wp.Gray80,
    hero = Wp.Gray90,
    heroBorder = Wp.Gray80,
    heroMuted = Wp.Gray20,
    heroTrack = Wp.Gray80,
    heroAccent = Wp.Blue30,
    heroKey = Wp.Gray80,
    chipDark = Wp.Gray2,
    onChipDark = Wp.Gray90,
)

val LocalUi = staticCompositionLocalOf { LightUi }

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.pjs_var,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Plus Jakarta Sans (police variable embarquée, licence OFL). */
val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

/** JetBrains Mono pour les numéros de version. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Mono = FontFamily(
    Font(R.font.jbm_var, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

private val AppTypography = Typography().let { t ->
    fun TextStyle.j() = copy(fontFamily = Jakarta)
    Typography(
        displayLarge = t.displayLarge.j(), displayMedium = t.displayMedium.j(), displaySmall = t.displaySmall.j(),
        headlineLarge = t.headlineLarge.j(), headlineMedium = t.headlineMedium.j(), headlineSmall = t.headlineSmall.j(),
        titleLarge = t.titleLarge.j().copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.j().copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.j().copy(fontWeight = FontWeight.Bold),
        bodyLarge = t.bodyLarge.j(), bodyMedium = t.bodyMedium.j(), bodySmall = t.bodySmall.j(),
        labelLarge = t.labelLarge.j().copy(fontWeight = FontWeight.Bold),
        labelMedium = t.labelMedium.j().copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.j().copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun AppTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ui = if (dark) DarkUi else LightUi
    val scheme = if (dark) {
        darkColorScheme(
            primary = Wp.Blue30, onPrimary = Wp.Gray100, background = ui.bg, surface = ui.surface,
            surfaceContainerHigh = ui.surface, surfaceContainer = ui.surface, onSurface = ui.text,
            onSurfaceVariant = ui.textMuted, outline = ui.border, outlineVariant = ui.divider, error = ui.danger,
        )
    } else {
        lightColorScheme(
            primary = Wp.Blue, onPrimary = Color.White, background = ui.bg, surface = ui.surface,
            surfaceContainerHigh = ui.surface, surfaceContainer = ui.surface, onSurface = ui.text,
            onSurfaceVariant = ui.textMuted, outline = ui.border, outlineVariant = ui.divider, error = ui.danger,
        )
    }
    CompositionLocalProvider(LocalUi provides ui) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(20.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}

/** Tons d'avatar (fond doux + texte) dérivés du nom. */
fun avatarTone(seed: String, ui: UiColors): Pair<Color, Color> {
    val tones = listOf(
        ui.accentSoft to ui.accent,
        ui.successSoft to ui.successText,
        ui.warningSoft to ui.warning,
        ui.neutralSoft to ui.textMuted,
    )
    return tones[(seed.hashCode() and 0x7fffffff) % tones.size]
}
