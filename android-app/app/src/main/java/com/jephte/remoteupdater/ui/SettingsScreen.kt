package com.jephte.remoteupdater.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jephte.remoteupdater.BuildConfig
import com.jephte.remoteupdater.R
import com.jephte.remoteupdater.data.AccountStore
import com.jephte.remoteupdater.data.AppLock
import com.jephte.remoteupdater.data.AppSettings
import com.jephte.remoteupdater.data.Notifier
import com.jephte.remoteupdater.data.ThemeMode
import com.jephte.remoteupdater.data.UpdateCheckWorker

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    settings: AppSettings,
    account: AccountStore,
    lock: AppLock,
    sitesCount: Int,
    biometricAvailable: Boolean,
    onChangePin: () -> Unit,
    onChangePassword: () -> Unit,
    onLockNow: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    onAddSite: () -> Unit,
) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val theme by settings.themeMode.collectAsState()
    val relock by settings.relockDelayMs.collectAsState()
    val notifications by settings.notifications.collectAsState()
    val checkHours by settings.checkHours.collectAsState()
    var biometric by remember { mutableStateOf(lock.biometricEnabled) }
    var picker by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }

    fun setNotifications(on: Boolean) {
        settings.setNotifications(on)
        UpdateCheckWorker.schedule(context)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        setNotifications(granted)
        if (!granted) vm.notify("Autorisez les notifications dans les réglages Android.")
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().background(ui.bg),
    ) {
        item { ScreenTitle("Réglages", Modifier.statusBarsPadding().padding(top = 20.dp, bottom = 6.dp)) }

        item {
            UiCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LetterTile(account.name.ifEmpty { "?" }, 52.dp, ui.accentSoft, ui.accent)
                    Column(Modifier.weight(1f)) {
                        Text(account.name, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = ui.text)
                        Text(account.phone, fontSize = 13.sp, color = ui.textSubtle)
                    }
                    StatusPill("$sitesCount site${if (sitesCount > 1) "s" else ""}", ui.onAccentSoft, ui.accentSoft)
                }
            }
        }

        item { SectionLabel("Sécurité") }
        item {
            UiCard {
                SettingRow(R.drawable.ic_ui_lock, "Code PIN", "4 chiffres pour déverrouiller l'app", onClick = onChangePin)
                ListDivider(62.dp)
                SettingRow(R.drawable.ic_ui_key, "Mot de passe", "Changer le mot de passe du compte", onClick = onChangePassword)
                ListDivider(62.dp)
                SettingRow(
                    R.drawable.ic_ui_fingerprint, "Empreinte digitale",
                    if (biometricAvailable) "Déverrouiller sans saisir le code" else "Aucune empreinte configurée sur ce téléphone",
                ) { UiSwitch(biometric && biometricAvailable, { biometric = it; lock.biometricEnabled = it }, enabled = biometricAvailable) }
                ListDivider(62.dp)
                SettingRow(
                    R.drawable.ic_ui_clock, "Verrouillage automatique",
                    AppSettings.RELOCK_OPTIONS.firstOrNull { it.first == relock }?.second, onClick = { picker = "relock" },
                )
            }
        }

        item { SectionLabel("Notifications") }
        item {
            UiCard {
                SettingRow(R.drawable.ic_ui_bell, "Alertes de mises à jour", "Nouvelles mises à jour et fin des mises à jour") {
                    UiSwitch(notifications && Notifier.canNotify(context), { on ->
                        if (on && Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else setNotifications(on)
                    })
                }
                ListDivider(62.dp)
                SettingRow(
                    R.drawable.ic_ui_refresh, "Vérification en arrière-plan",
                    AppSettings.CHECK_OPTIONS.firstOrNull { it.first == checkHours }?.second, onClick = { picker = "check" },
                )
            }
        }

        item { SectionLabel("Apparence") }
        item {
            UiCard {
                Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Triple(ThemeMode.SYSTEM, "Auto", R.drawable.ic_ui_monitor), Triple(ThemeMode.LIGHT, "Clair", R.drawable.ic_ui_sun), Triple(ThemeMode.DARK, "Sombre", R.drawable.ic_ui_moon))
                        .forEach { (mode, label, icon) ->
                            val active = theme == mode
                            Row(
                                Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                                    .background(if (active) ui.chipDark else androidx.compose.ui.graphics.Color.Transparent)
                                    .selectable(active, onClick = { settings.setThemeMode(mode) }, role = Role.RadioButton)
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Ico(icon, if (active) ui.onChipDark else ui.textMuted, 17.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(label, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, color = if (active) ui.onChipDark else ui.textMuted)
                            }
                        }
                }
            }
        }

        item { SectionLabel("Sites") }
        item {
            UiCard {
                SettingRow(R.drawable.ic_ui_plus, "Connecter un site", "QR code ou code de connexion", onClick = onAddSite)
                ListDivider(62.dp)
                SettingRow(R.drawable.ic_ui_package, "Extension WordPress", "« Remote Updater » doit être active sur chaque site")
            }
        }

        item { SectionLabel("Compte") }
        item {
            UiCard {
                SettingRow(R.drawable.ic_ui_lock, "Verrouiller maintenant", onClick = onLockNow)
                ListDivider(62.dp)
                SettingRow(R.drawable.ic_ui_logout, "Se déconnecter", "Le mot de passe sera demandé", onClick = { confirm = "logout" })
                ListDivider(62.dp)
                SettingRow(R.drawable.ic_ui_trash, "Supprimer le compte et les données", onClick = { confirm = "delete" }, danger = true)
            }
        }

        item {
            Text(
                "Remote Updater ${BuildConfig.VERSION_NAME} · WordPress 5.8+ · PHP 7.4+",
                fontSize = 12.sp, color = ui.textSubtle, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    when (picker) {
        "relock" -> Choice("Verrouillage automatique", AppSettings.RELOCK_OPTIONS, relock, { settings.setRelockDelay(it); picker = null }) { picker = null }
        "check" -> Choice("Vérification en arrière-plan", AppSettings.CHECK_OPTIONS, checkHours, {
            settings.setCheckHours(it); UpdateCheckWorker.schedule(context); picker = null
        }) { picker = null }
    }
    when (confirm) {
        "logout" -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Se déconnecter ?") },
            text = { Text("Vos sites restent enregistrés. Il faudra saisir votre numéro et votre mot de passe pour revenir.") },
            confirmButton = { TextButton(onClick = { confirm = null; onLogout() }) { Text("Se déconnecter") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Supprimer le compte ?") },
            text = { Text("Le compte, les sites connectés et l'historique seront définitivement effacés de ce téléphone. Pensez à révoquer les connexions dans WordPress.") },
            confirmButton = { TextButton(onClick = { confirm = null; onDeleteAccount() }) { Text("Supprimer", color = ui.danger) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun <T> Choice(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelect(value) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(value == selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Wp.Blue))
                        Spacer(Modifier.width(10.dp))
                        Text(label, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
