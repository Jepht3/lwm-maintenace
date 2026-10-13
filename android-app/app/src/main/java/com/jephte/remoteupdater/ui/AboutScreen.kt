package com.jephte.remoteupdater.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.jephte.remoteupdater.BuildConfig
import com.jephte.remoteupdater.MainActivity
import com.jephte.remoteupdater.R
import com.jephte.remoteupdater.data.AppUpdater

/** À propos : version installée, recherche de mise à jour de l'app, liens utiles. */
@Composable
fun AboutScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val update by vm.appUpdate.collectAsState()
    val check by vm.appCheck.collectAsState()

    fun open(url: String) {
        (context as? MainActivity)?.expectExternalActivity()
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    Column(Modifier.fillMaxSize().background(ui.bg)) {
        TopBar("À propos", null, onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppLogo(72.dp)
                Text("Remote Updater", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = ui.text, modifier = Modifier.padding(top = 8.dp))
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontSize = 14.sp, color = ui.textSubtle)
            }

            val u = update
            if (u != null) {
                AppUpdateCard(u) { vm.installAppUpdate() }
            } else {
                UiCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            when {
                                check.running -> Box(Modifier.padding(10.dp)) { Spinner(ui.accent) }
                                check.error != null -> IconTile(R.drawable.ic_ui_alert, ui.dangerSoft, ui.danger, size = 40.dp)
                                check.checkedAt > 0 -> IconTile(R.drawable.ic_ui_check, ui.successSoft, ui.successText, size = 40.dp)
                                else -> IconTile(R.drawable.ic_ui_refresh, ui.accentSoft, ui.accent, size = 40.dp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when {
                                        check.running -> "Recherche en cours…"
                                        check.error != null -> "Vérification impossible"
                                        check.checkedAt > 0 -> "Vous avez la dernière version"
                                        else -> "Mises à jour de l'app"
                                    },
                                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ui.text,
                                )
                                Text(
                                    when {
                                        check.error != null -> check.error!!
                                        check.checkedAt > 0 -> "Vérifié ${relativeTime(check.checkedAt)}"
                                        else -> "L'app vérifie aussi toute seule au démarrage et en arrière-plan."
                                    },
                                    fontSize = 12.sp, color = if (check.error != null) ui.dangerText else ui.textSubtle,
                                )
                            }
                        }
                        SecondaryButton(
                            "Vérifier les mises à jour", { vm.checkAppUpdate(manual = true) },
                            Modifier.fillMaxWidth(), icon = R.drawable.ic_ui_refresh, enabled = !check.running,
                        )
                    }
                }
            }

            SectionLabel("Informations")
            UiCard {
                SettingRow(R.drawable.ic_ui_globe, "Page de l'application", "lewebmax.com/remote-updater", onClick = { open(AppUpdater.PAGE_URL) }) {
                    Ico(R.drawable.ic_ui_external, ui.textSubtle, 18.dp)
                }
                ListDivider(62.dp)
                SettingRow(R.drawable.ic_ui_history, "Toutes les versions", "Nouveautés de chaque version", onClick = { open(AppUpdater.RELEASES_URL) }) {
                    Ico(R.drawable.ic_ui_external, ui.textSubtle, 18.dp)
                }
                ListDivider(62.dp)
                SettingRow(R.drawable.ic_ui_package, "Extension WordPress", "À installer sur chaque site ; elle se met ensuite à jour depuis WordPress")
            }

            Text(
                "© 2026 LEWEBMAX · WordPress 5.8+ · PHP 7.4+",
                fontSize = 12.sp, color = ui.textSubtle, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 4.dp),
            )
        }
    }
}
