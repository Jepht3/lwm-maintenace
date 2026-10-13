package com.jephte.remoteupdater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jephte.remoteupdater.BuildConfig
import com.jephte.remoteupdater.R

/** Bandeau « Nouvelle version de l'app » avec téléchargement en direct. */
@Composable
fun AppUpdateCard(state: AppUpdateState, onInstall: () -> Unit) {
    val ui = LocalUi.current
    val r = state.release
    UiCard(background = ui.runningBg, borderColor = ui.runningBorder) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(R.drawable.ic_ui_download, ui.accentSoft, ui.accent, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text("Nouvelle version ${r.version}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ui.text)
                    Text(
                        r.changelog.firstOrNull() ?: "Vous avez la version ${BuildConfig.VERSION_NAME}.",
                        fontSize = 12.sp, color = ui.textSubtle, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val p = state.progress
            if (p != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProgressBar(p, ui.accent, ui.track)
                    Text("Téléchargement… ${(p * 100).toInt()} %", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ui.onAccentSoft)
                }
            } else {
                state.error?.let { Text(it, fontSize = 12.sp, color = ui.dangerText) }
                PrimaryButton(if (state.error != null) "Réessayer" else "Installer la mise à jour", onInstall, Modifier.fillMaxWidth(), icon = R.drawable.ic_ui_download, height = 44.dp)
            }
        }
    }
}
