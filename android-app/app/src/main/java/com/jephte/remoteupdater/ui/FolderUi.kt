package com.jephte.remoteupdater.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jephte.remoteupdater.R
import com.jephte.remoteupdater.data.Folder
import com.jephte.remoteupdater.data.FolderStore

/** Couleur unie d'un dossier. */
fun folderColor(f: Folder): Color = Color(FolderStore.COLORS[f.color.coerceIn(0, FolderStore.COLORS.lastIndex)])

/** Tuile « dossier » teintée à la couleur du client. */
@Composable
fun FolderTile(f: Folder?, size: Dp = 36.dp) {
    val ui = LocalUi.current
    val c = f?.let { folderColor(it) } ?: ui.textMuted
    IconTile(
        if (f != null) R.drawable.ic_ui_folder_fill else R.drawable.ic_ui_folder,
        c.copy(alpha = if (ui.isDark) 0.22f else 0.12f), c, size = size, iconSize = size * 0.52f,
    )
}

/** Création ou modification d'un dossier (nom + couleur, suppression en mode édition). */
@Composable
fun FolderDialog(
    initial: Folder?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Int) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val ui = LocalUi.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableIntStateOf(initial?.color ?: 0) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer le dossier ?") },
            text = { Text("Les sites de « ${initial?.name} » ne seront pas retirés : ils passeront simplement « sans dossier ».") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Supprimer", color = ui.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouveau dossier" else "Modifier le dossier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nom du client") })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FolderStore.COLORS.forEachIndexed { i, argb ->
                        val selected = i == color
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color(argb))
                                .border(if (selected) 3.dp else 0.dp, if (selected) ui.text else Color.Transparent, CircleShape)
                                .clickable { color = i },
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Ico(R.drawable.ic_ui_check, Color.White, 16.dp) }
                    }
                }
                if (onDelete != null) {
                    Text(
                        "Supprimer ce dossier", color = ui.danger, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { confirmDelete = true }.padding(vertical = 6.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), color) }, enabled = name.isNotBlank()) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Choix du dossier d'un site (avec création à la volée). */
@Composable
fun FolderPickerDialog(
    folders: List<Folder>,
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
    onCreate: (name: String, color: Int) -> Unit,
) {
    val ui = LocalUi.current
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        FolderDialog(null, onDismiss = { creating = false }, onSave = { n, c -> creating = false; onCreate(n, c) })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Classer dans un dossier") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                (listOf<Folder?>(null) + folders).forEach { f ->
                    val selected = f?.id == current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (selected) ui.accentSoft else Color.Transparent)
                            .clickable { onPick(f?.id) }.padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        FolderTile(f, 32.dp)
                        Text(f?.name ?: "Sans dossier", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ui.text, modifier = Modifier.weight(1f))
                        if (selected) Ico(R.drawable.ic_ui_check, ui.accent, 18.dp)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { creating = true }.padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    IconTile(R.drawable.ic_ui_plus, ui.accentSoft, ui.accent, size = 32.dp, iconSize = 17.dp)
                    Text("Nouveau dossier…", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ui.accent)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
