package com.jephte.remoteupdater.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.core.content.ContextCompat
import com.jephte.remoteupdater.BuildConfig
import com.jephte.remoteupdater.data.AppLock
import com.jephte.remoteupdater.data.AppSettings
import com.jephte.remoteupdater.data.ThemeMode
import com.jephte.remoteupdater.data.UpdateCheckWorker

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    settings: AppSettings,
    lock: AppLock,
    sitesCount: Int,
    biometricAvailable: Boolean,
    onChangePin: () -> Unit,
    onLockNow: () -> Unit,
    onAddSite: () -> Unit,
) {
    val context = LocalContext.current
    val theme by settings.themeMode.collectAsState()
    val relock by settings.relockDelayMs.collectAsState()
    val notifications by settings.notifications.collectAsState()
    val checkHours by settings.checkHours.collectAsState()
    var biometric by remember { mutableStateOf(lock.biometricEnabled) }
    var picker by remember { mutableStateOf<String?>(null) }

    fun enableNotifications(enabled: Boolean) {
        settings.setNotifications(enabled)
        UpdateCheckWorker.schedule(context)
        if (enabled) vm.notify("Vous serez averti des nouvelles mises à jour.")
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enableNotifications(true) else vm.notify("Autorisez les notifications dans les réglages Android.")
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                "Réglages",
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.statusBarsPadding().padding(top = 24.dp, start = 2.dp, bottom = 4.dp),
            )
        }

        item {
            // Carte « profil » de l'application
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(LocalAppColors.current.heroGradient)
                    .padding(20.dp),
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    AppMark(size = 32.dp)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Remote Updater", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "$sitesCount site(s) connecté(s) · ${if (lock.hasPin) "protégé par code" else "non protégé"}",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(Icons.Rounded.Shield, null, tint = Color.White.copy(alpha = 0.9f))
            }
        }

        item { SectionTitle("Sécurité") }
        item {
            SettingsGroup {
                SettingRow(Icons.Rounded.LockReset, "Changer le code PIN", "Code à 6 chiffres", onClick = onChangePin)
                Divider()
                SettingRow(
                    Icons.Rounded.Fingerprint,
                    "Déverrouillage biométrique",
                    if (biometricAvailable) "Empreinte ou visage" else "Aucune biométrie configurée sur ce téléphone",
                    trailing = {
                        Switch(
                            checked = biometric && biometricAvailable,
                            enabled = biometricAvailable,
                            onCheckedChange = {
                                biometric = it
                                lock.biometricEnabled = it
                            },
                        )
                    },
                )
                Divider()
                SettingRow(
                    Icons.Rounded.LockClock,
                    "Verrouillage automatique",
                    AppSettings.RELOCK_OPTIONS.firstOrNull { it.first == relock }?.second ?: "",
                    onClick = { picker = "relock" },
                )
                Divider()
                SettingRow(Icons.Rounded.Lock, "Verrouiller maintenant", null, onClick = onLockNow)
            }
        }

        item { SectionTitle("Notifications") }
        item {
            SettingsGroup {
                SettingRow(
                    Icons.Rounded.Notifications,
                    "Alertes de mises à jour",
                    "Vérification automatique en arrière-plan",
                    trailing = {
                        Switch(
                            checked = notifications,
                            onCheckedChange = { on ->
                                if (on && Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    enableNotifications(on)
                                }
                            },
                        )
                    },
                )
                Divider()
                SettingRow(
                    Icons.Rounded.Schedule,
                    "Fréquence de vérification",
                    AppSettings.CHECK_OPTIONS.firstOrNull { it.first == checkHours }?.second ?: "",
                    enabled = notifications,
                    onClick = { picker = "check" },
                )
            }
        }

        item { SectionTitle("Apparence") }
        item {
            SettingsGroup {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeOption(Icons.Rounded.Brightness6, "Système", theme == ThemeMode.SYSTEM, Modifier.weight(1f)) { settings.setThemeMode(ThemeMode.SYSTEM) }
                    ThemeOption(Icons.Rounded.LightMode, "Clair", theme == ThemeMode.LIGHT, Modifier.weight(1f)) { settings.setThemeMode(ThemeMode.LIGHT) }
                    ThemeOption(Icons.Rounded.DarkMode, "Sombre", theme == ThemeMode.DARK, Modifier.weight(1f)) { settings.setThemeMode(ThemeMode.DARK) }
                }
            }
        }

        item { SectionTitle("Sites") }
        item {
            SettingsGroup {
                SettingRow(Icons.Rounded.AddLink, "Connecter un nouveau site", "QR code ou code de connexion", onClick = onAddSite)
                Divider()
                SettingRow(
                    Icons.Rounded.SettingsSuggest,
                    "Extension WordPress requise",
                    "« Remote Updater » doit être installée et activée sur chaque site",
                )
            }
        }

        item { SectionTitle("À propos") }
        item {
            SettingsGroup {
                SettingRow(Icons.Rounded.Info, "Version", "Remote Updater ${BuildConfig.VERSION_NAME}")
                Divider()
                SettingRow(Icons.Rounded.Language, "Compatibilité", "WordPress 5.8+ · PHP 7.4+ · Multisite")
            }
        }
    }

    when (picker) {
        "relock" -> ChoiceDialog(
            title = "Verrouillage automatique",
            options = AppSettings.RELOCK_OPTIONS,
            selected = relock,
            onSelect = {
                settings.setRelockDelay(it)
                picker = null
            },
            onDismiss = { picker = null },
        )
        "check" -> ChoiceDialog(
            title = "Fréquence de vérification",
            options = AppSettings.CHECK_OPTIONS,
            selected = checkHours,
            onSelect = {
                settings.setCheckHours(it)
                UpdateCheckWorker.schedule(context)
                picker = null
            },
            onDismiss = { picker = null },
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) { Column { content() } }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha), modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ThemeOption(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) primary else MaterialTheme.colorScheme.surfaceContainer)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 14.dp),
    ) {
        Icon(icon, null, tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .selectable(selected = value == selected, onClick = { onSelect(value) }, role = Role.RadioButton)
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
