package com.jephte.remoteupdater.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.jephte.remoteupdater.MainActivity
import com.jephte.remoteupdater.data.SiteData
import com.jephte.remoteupdater.data.SiteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class PendingUpload(val type: String, val name: String, val bytes: ByteArray)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteDetailScreen(vm: AppViewModel, state: SiteState, onBack: () -> Unit, onRemoved: () -> Unit) {
    val id = state.site.id
    val data = state.data
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var uploadType by remember { mutableStateOf("plugin") }
    var pending by remember { mutableStateOf<PendingUpload?>(null) }

    // Sélecteur de fichier .zip pour la mise à jour manuelle
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val type = uploadType
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "package.zip"
                    val bytes = resolver.openInputStream(uri)!!.use { it.readBytes() }
                    PendingUpload(type, name, bytes)
                }.getOrNull()
            }
            when {
                file == null -> vm.notify("Impossible de lire le fichier.")
                !file.name.endsWith(".zip", ignoreCase = true) -> vm.notify("Choisissez un fichier .zip.")
                else -> pending = file
            }
        }
    }
    val pickZip: (String) -> Unit = { type ->
        uploadType = type
        (context as? MainActivity)?.expectExternalActivity()
        picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
    }

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { vm.refresh(id, force = true) },
        modifier = Modifier.fillMaxSize().background(colors.tint),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 14.dp),
                ) {
                    RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onBack)
                    Spacer(Modifier.weight(1f))
                    RoundIconButton(Icons.Rounded.Refresh, "Rechercher les mises à jour", { vm.refresh(id, force = true) })
                    Spacer(Modifier.width(10.dp))
                    Box {
                        RoundIconButton(Icons.Rounded.MoreVert, "Plus d'options", { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Renommer") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = {
                                    menu = false
                                    renaming = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Ouvrir l'administration") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                                onClick = {
                                    menu = false
                                    val target = data?.info?.adminUrl?.ifEmpty { null } ?: (state.site.url + "wp-admin/")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Retirer ce site", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menu = false
                                    confirm = "Retirer ce site de l'application ? Pensez aussi à révoquer la connexion dans WordPress." to onRemoved
                                },
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(top = 14.dp, start = 2.dp)) {
                    Text(state.site.name, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        SiteRepository.hostOf(state.site.url) + (data?.let {
                            " · WordPress ${it.info.wpVersion} · PHP ${it.info.phpVersion}" + if (it.info.multisite) " · Multisite (${it.info.sitesCount})" else ""
                        } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (data == null) {
                item {
                    SoftCard {
                        if (state.loading) Row(verticalAlignment = Alignment.CenterVertically) {
                            SmallSpinner()
                            Spacer(Modifier.width(12.dp))
                            Text("Chargement…")
                        } else Text(state.error ?: "Aucune donnée.", color = MaterialTheme.colorScheme.error)
                    }
                }
                return@LazyColumn
            }

            val coreUpdates = (if (data.core.updateAvailable) 1 else 0) + (if (data.translations.isNotEmpty()) 1 else 0)
            val pluginUpdates = data.plugins.count { it.updateAvailable }
            val themeUpdates = data.themes.count { it.updateAvailable }

            if (state.error != null || !data.info.fileModsAllowed) {
                item {
                    Text(
                        state.error ?: "Les modifications de fichiers sont désactivées sur ce site (DISALLOW_FILE_MODS).",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (state.updateCount > 0) {
                item {
                    Button(
                        onClick = { confirm = "Installer les ${state.updateCount} mises à jour sur ${state.site.name} ?" to { vm.updateEverything(id) } },
                        enabled = "all" !in state.busy && data.info.fileModsAllowed,
                        colors = ButtonDefaults.buttonColors(containerColor = WpColors.Blue),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if ("all" in state.busy) {
                            SmallSpinner(color = Color.White)
                            Spacer(Modifier.width(10.dp))
                            Text("Mise à jour en cours…", style = MaterialTheme.typography.titleSmall)
                        } else {
                            Icon(Icons.Rounded.SystemUpdateAlt, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Tout mettre à jour (${state.updateCount})", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            item {
                FilterRow(
                    listOf(
                        if (coreUpdates > 0) "WordPress · $coreUpdates" else "WordPress",
                        if (pluginUpdates > 0) "Extensions · $pluginUpdates" else "Extensions",
                        if (themeUpdates > 0) "Thèmes · $themeUpdates" else "Thèmes",
                    ),
                    tab,
                    {
                        tab = it
                        query = ""
                    },
                )
            }
            if (tab != 0 && (if (tab == 1) data.plugins.size else data.themes.size) > 5) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(if (tab == 1) "Rechercher une extension" else "Rechercher un thème") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true,
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            val q = query.trim().lowercase()
            val shown = if (q.isEmpty()) data else data.copy(
                plugins = data.plugins.filter { q in it.name.lowercase() || q in it.author.lowercase() },
                themes = data.themes.filter { q in it.name.lowercase() },
            )
            when (tab) {
                0 -> coreTab(vm, state, data) { msg, action -> confirm = msg to action }
                1 -> itemsTab(vm, state, shown, plugins = true) { pickZip("plugin") }
                else -> itemsTab(vm, state, shown, plugins = false) { pickZip("theme") }
            }
        }
    }

    confirm?.let { (message, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Rounded.SystemUpdateAlt, null) },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = {
                    confirm = null
                    action()
                }) { Text("Confirmer") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } },
        )
    }

    if (renaming) {
        var name by remember { mutableStateOf(state.site.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            icon = { Icon(Icons.Rounded.Edit, null) },
            title = { Text("Renommer le site") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nom affiché") }) },
            confirmButton = {
                Button(onClick = {
                    vm.renameSite(id, name)
                    renaming = false
                }, enabled = name.isNotBlank()) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Annuler") } },
        )
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { pending = null },
            icon = { Icon(Icons.Rounded.CloudUpload, null) },
            title = { Text("Mise à jour manuelle") },
            text = {
                Text(
                    "Téléverser « ${p.name} » (${formatSize(p.bytes.size.toLong())}) sur ${state.site.name} ?\n\n" +
                        if (p.type == "plugin") "Si cette extension est déjà installée, elle sera remplacée par cette version."
                        else "Si ce thème est déjà installé, il sera remplacé par cette version.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    pending = null
                    vm.uploadPackage(id, p.type, p.name, p.bytes)
                }) { Text("Téléverser") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Annuler") } },
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format("%.1f Mo", bytes / 1_048_576.0)
    bytes >= 1024 -> "${bytes / 1024} Ko"
    else -> "$bytes o"
}

/* ---------------- Onglet WordPress ---------------- */

private fun LazyListScope.coreTab(vm: AppViewModel, state: SiteState, data: SiteData, ask: (String, () -> Unit) -> Unit) {
    val id = state.site.id
    item {
        UpdateItemCard(
            overline = "Cœur de WordPress",
            title = "WordPress",
            subtitle = "Version ${data.core.current} installée",
            icon = Icons.Rounded.Language,
            newVersion = data.core.newVersion.takeIf { data.core.updateAvailable }?.let { "v$it" },
            secondary = if (data.core.updateAvailable) "Disponible" else null,
            busy = "core" in state.busy || "all" in state.busy,
            onUpdate = {
                ask("Mettre à jour WordPress vers ${data.core.newVersion} ? Faites une sauvegarde avant une mise à jour majeure.") { vm.updateCore(id) }
            },
        )
    }
    item {
        UpdateItemCard(
            overline = "Langues",
            title = "Traductions",
            subtitle = if (data.translations.isEmpty()) "Toutes les traductions sont à jour" else data.translations.joinToString { "${it.slug} (${it.language})" },
            icon = Icons.Rounded.Translate,
            newVersion = if (data.translations.isNotEmpty()) "${data.translations.size} paquet(s)" else null,
            secondary = null,
            busy = "translations" in state.busy || "all" in state.busy,
            onUpdate = { vm.updateTranslations(id) },
        )
    }
    item {
        SoftCard {
            Text("Serveur", fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            InfoRow("Version de PHP", data.info.phpVersion)
            InfoRow("Multisite", if (data.info.multisite) "Oui · ${data.info.sitesCount} sites" else "Non")
            InfoRow("Installation des mises à jour", if (data.info.fileModsAllowed) "Autorisée" else "Désactivée")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

/* ---------------- Onglets Extensions / Thèmes ---------------- */

private fun LazyListScope.itemsTab(vm: AppViewModel, state: SiteState, data: SiteData, plugins: Boolean, onUpload: () -> Unit) {
    val id = state.site.id
    val type = if (plugins) "plugin" else "theme"
    item { UploadCard(plugins, busy = "upload:$type" in state.busy, onClick = onUpload) }

    if (plugins) {
        val updates = data.plugins.count { it.updateAvailable }
        if (updates > 1) item { BulkButton("Mettre à jour les $updates extensions", "plugins" in state.busy || "all" in state.busy) { vm.updateAllPlugins(id) } }
        items(data.plugins, key = { it.file }) { p ->
            UpdateItemCard(
                overline = (if (p.active) "Active" else "Inactive") + if (p.author.isNotEmpty()) " · ${p.author}" else "",
                title = p.name,
                subtitle = "Version ${p.version} installée",
                avatar = p.name,
                newVersion = p.newVersion.takeIf { p.updateAvailable }?.let { "v$it" },
                secondary = if (p.active) "Active" else "Inactive",
                warning = when {
                    p.updateAvailable && !p.hasPackage -> "Téléchargement impossible (licence ?) — utilisez la mise à jour manuelle"
                    p.updateAvailable && p.requiresPhp != null -> "Requiert PHP ${p.requiresPhp}"
                    else -> null
                },
                busy = "plugin:${p.file}" in state.busy || "plugins" in state.busy || "all" in state.busy,
                onUpdate = { vm.updatePlugin(id, p.file) },
                autoUpdate = p.autoUpdate,
                onAutoUpdate = { vm.setAutoUpdate(id, "plugin", p.file, it) },
            )
        }
    } else {
        val updates = data.themes.count { it.updateAvailable }
        if (updates > 1) item { BulkButton("Mettre à jour les $updates thèmes", "themes" in state.busy || "all" in state.busy) { vm.updateAllThemes(id) } }
        items(data.themes, key = { it.slug }) { t ->
            UpdateItemCard(
                overline = (if (t.active) "Thème actif" else "Thème inactif") + if (t.author.isNotEmpty()) " · ${t.author}" else "",
                title = t.name,
                subtitle = "Version ${t.version} installée",
                avatar = t.name,
                newVersion = t.newVersion.takeIf { t.updateAvailable }?.let { "v$it" },
                secondary = if (t.active) "Actif" else "Inactif",
                warning = if (t.updateAvailable && !t.hasPackage) "Téléchargement impossible (licence ?) — utilisez la mise à jour manuelle" else null,
                busy = "theme:${t.slug}" in state.busy || "themes" in state.busy || "all" in state.busy,
                onUpdate = { vm.updateTheme(id, t.slug) },
                autoUpdate = t.autoUpdate,
                onAutoUpdate = { vm.setAutoUpdate(id, "theme", t.slug, it) },
            )
        }
    }
}

@Composable
private fun UploadCard(plugins: Boolean, busy: Boolean, onClick: () -> Unit) {
    SoftCard(onClick = if (busy) null else onClick) {
        Text("Mise à jour manuelle", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(if (busy) "Téléversement…" else "Fichier .zip", fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            if (plugins) "Remplacez une extension (premium, version précise…) par le .zip de votre choix."
            else "Remplacez un thème par le .zip de votre choix.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChipPill("Choisir un fichier", dark = true)
            Spacer(Modifier.width(8.dp))
            ChipPill(".zip", dark = false)
            Spacer(Modifier.weight(1f))
            if (busy) Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) { SmallSpinner() }
            else ArrowButton(onClick = onClick, icon = Icons.Rounded.CloudUpload, contentDescription = "Téléverser un .zip")
        }
    }
}

@Composable
private fun BulkButton(label: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.chipDark, contentColor = LocalAppColors.current.onChipDark),
        contentPadding = PaddingValues(vertical = 14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (busy) SmallSpinner(color = LocalAppColors.current.onChipDark)
        else Icon(Icons.Rounded.SystemUpdateAlt, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/** Carte d'élément (cœur, traduction, extension, thème) au style de la maquette. */
@Composable
private fun UpdateItemCard(
    overline: String,
    title: String,
    subtitle: String,
    newVersion: String?,
    secondary: String?,
    busy: Boolean,
    onUpdate: () -> Unit,
    icon: ImageVector? = null,
    avatar: String? = null,
    warning: String? = null,
    autoUpdate: Boolean? = null,
    onAutoUpdate: (Boolean) -> Unit = {},
) {
    val colors = LocalAppColors.current
    SoftCard {
        Row {
            Column(Modifier.weight(1f)) {
                Text(overline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(title, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            when {
                avatar != null -> LetterAvatar(avatar, size = 48.dp, shape = CircleShape)
                icon != null -> Box(
                    Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = WpColors.Blue) }
            }
        }
        warning?.let {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (newVersion != null) ChipPill(newVersion, dark = true) else ChipPill("À jour", dark = false, icon = Icons.Rounded.CheckCircle)
            if (secondary != null) {
                Spacer(Modifier.width(8.dp))
                ChipPill(secondary, dark = false)
            }
            Spacer(Modifier.weight(1f))
            when {
                busy -> Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) { SmallSpinner() }
                newVersion != null -> ArrowButton(onClick = onUpdate, icon = Icons.Rounded.Download, contentDescription = "Mettre à jour $title")
                else -> Box(
                    Modifier.size(54.dp).clip(CircleShape).background(colors.successContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.CheckCircle, "À jour", tint = colors.success) }
            }
        }
        if (autoUpdate != null) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.Rounded.Bolt, null, tint = if (autoUpdate) WpColors.Blue else MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Mise à jour automatique", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = autoUpdate, onCheckedChange = onAutoUpdate, modifier = Modifier.scale(0.8f))
            }
        }
    }
}
