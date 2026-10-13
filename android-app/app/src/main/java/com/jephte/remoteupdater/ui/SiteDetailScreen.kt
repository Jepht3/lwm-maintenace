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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.jephte.remoteupdater.MainActivity
import com.jephte.remoteupdater.R
import com.jephte.remoteupdater.data.SiteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class PendingUpload(val type: String, val name: String, val bytes: ByteArray)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteDetailScreen(vm: AppViewModel, state: SiteState, jobs: Map<String, UpdateJob>, onBack: () -> Unit, onRemoved: () -> Unit) {
    val ui = LocalUi.current
    val id = state.site.id
    val data = state.data
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    val folders by vm.folders.collectAsState()
    val folder = folders.firstOrNull { it.id == state.site.folderId }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmCore by remember { mutableStateOf<UpdateJob?>(null) }
    var uploadType by remember { mutableStateOf("plugin") }
    var pending by remember { mutableStateOf<PendingUpload?>(null) }

    val siteJobs = jobs.values.filter { it.siteId == id }
    val active = siteJobs.filter { it.state == JobState.QUEUED || it.state == JobState.RUNNING }
    val finished = siteJobs.count { it.state == JobState.DONE || it.state == JobState.FAILED }
    val pendingItems = state.pending

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val type = uploadType
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val r = context.contentResolver
                    val name = r.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "package.zip"
                    PendingUpload(type, name, r.openInputStream(uri)!!.use { it.readBytes() })
                }.getOrNull()
            }
            when {
                file == null -> vm.notify("Impossible de lire le fichier.")
                !file.name.endsWith(".zip", ignoreCase = true) -> vm.notify("Choisissez un fichier .zip.")
                else -> pending = file
            }
        }
    }
    fun pickZip(type: String) {
        uploadType = type
        (context as? MainActivity)?.expectExternalActivity()
        picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
    }

    Column(Modifier.fillMaxSize().background(ui.bg)) {
        Column(Modifier.background(ui.surface)) {
            TopBar(
                state.site.name,
                (folder?.let { "${it.name} · " } ?: "") + SiteRepository.hostOf(state.site.url) + (data?.let { " · WP ${it.info.wpVersion} · PHP ${it.info.phpVersion.substringBeforeLast('.')}" } ?: ""),
                onBack,
            ) {
                GhostIconButton(R.drawable.ic_ui_refresh, "Rechercher les mises à jour", { vm.refresh(id, force = true) })
                Box {
                    GhostIconButton(R.drawable.ic_ui_more, "Plus d'options", { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Renommer") }, leadingIcon = { Ico(R.drawable.ic_ui_edit, ui.text, 20.dp) }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text("Classer dans un dossier") }, leadingIcon = { Ico(R.drawable.ic_ui_folder, ui.text, 20.dp) }, onClick = { menu = false; moving = true })
                        DropdownMenuItem(
                            text = { Text("Ouvrir l'administration") },
                            leadingIcon = { Ico(R.drawable.ic_ui_external, ui.text, 20.dp) },
                            onClick = {
                                menu = false
                                context.startActivity(Intent(Intent.ACTION_VIEW, (data?.info?.adminUrl?.ifEmpty { null } ?: (state.site.url + "wp-admin/")).toUri()))
                            },
                        )
                        DropdownMenuItem(text = { Text("Retirer ce site", color = ui.danger) }, leadingIcon = { Ico(R.drawable.ic_ui_trash, ui.danger, 20.dp) }, onClick = { menu = false; confirmRemove = true })
                    }
                }
            }
            if (data != null) {
                val core = pendingItems.count { it.kind == JobKind.CORE || it.kind == JobKind.TRANSLATIONS }
                val pl = pendingItems.count { it.kind == JobKind.PLUGIN }
                val th = pendingItems.count { it.kind == JobKind.THEME }
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                    Segmented(listOf("Extensions", "Thèmes", "WordPress"), tab, { tab = it; query = "" }, listOf(pl, th, core))
                }
            }
            ListDivider()
        }

        PullToRefreshBox(
            isRefreshing = state.loading && data != null,
            onRefresh = { vm.refresh(id, force = true) },
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (data == null) {
                    item {
                        UiCard {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (state.loading) {
                                    Spinner(ui.accent)
                                    Text("Chargement du site…", color = ui.text)
                                } else {
                                    Ico(R.drawable.ic_ui_alert, ui.danger)
                                    Text(state.error ?: "Aucune donnée.", color = ui.dangerText)
                                }
                            }
                        }
                    }
                    return@LazyColumn
                }
                if (state.error != null) item { ErrorBox(state.error) }
                if (!data.info.fileModsAllowed) item { ErrorBox("Les modifications de fichiers sont désactivées sur ce site (DISALLOW_FILE_MODS).") }

                when (tab) {
                    0, 1 -> {
                        val plugins = tab == 0
                        item {
                            UiCard(onClick = if ("upload:${if (plugins) "plugin" else "theme"}" in state.busy) null else ({ pickZip(if (plugins) "plugin" else "theme") })) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val uploading = "upload:${if (plugins) "plugin" else "theme"}" in state.busy
                                    if (uploading) Box(Modifier.padding(9.dp)) { Spinner(ui.accent) } else IconTile(R.drawable.ic_ui_upload, ui.accentSoft, ui.accent, size = 38.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(if (uploading) "Téléversement en cours…" else "Mise à jour manuelle", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ui.text)
                                        Text(if (plugins) "Téléverser le .zip d'une extension" else "Téléverser le .zip d'un thème", fontSize = 12.sp, color = ui.textSubtle)
                                    }
                                    Ico(R.drawable.ic_ui_chevron_right, ui.textSubtle, 18.dp)
                                }
                            }
                        }
                        val all = if (plugins) data.plugins.map { Triple(it.file, it.name, it) } else data.themes.map { Triple(it.slug, it.name, it) }
                        if (all.size > 6) {
                            item {
                                UiTextField(query, { query = it }, if (plugins) "Rechercher une extension" else "Rechercher un thème", leading = R.drawable.ic_ui_search, placeholder = "Nom…")
                            }
                        }
                        val q = query.trim().lowercase()
                        val updatable = if (plugins) data.plugins.count { it.updateAvailable } else data.themes.count { it.updateAvailable }
                        item {
                            SectionLabel(
                                (if (updatable > 0) "$updatable mise${if (updatable > 1) "s" else ""} à jour · " else "") +
                                    "${all.size} ${if (plugins) "extension" else "thème"}${if (all.size > 1) "s" else ""}",
                            )
                        }
                        item {
                            UiCard {
                                val rows = if (plugins) data.plugins.filter { q.isEmpty() || q in it.name.lowercase() } else emptyList()
                                val themeRows = if (!plugins) data.themes.filter { q.isEmpty() || q in it.name.lowercase() } else emptyList()
                                if (plugins) {
                                    rows.forEachIndexed { i, p ->
                                        val job = jobs["$id|${JobKind.PLUGIN.name}|${p.file}"]
                                        val st = rowStateFor(job, jobs.values, p.updateAvailable, "v${p.version}" + if (p.autoUpdate) " · mise à jour auto" else (if (p.active) "" else " · inactive"), p.autoUpdate)
                                        val j = job ?: UpdateJob(id, state.site.name, JobKind.PLUGIN, p.file, p.name, p.version, p.newVersion.orEmpty())
                                        UpdateRow(
                                            p.name, p.version, p.newVersion.orEmpty(), st,
                                            onUpdate = { vm.updateOne(j) }, onRetry = { vm.retry(j) },
                                            trailingUpToDate = { UiSwitch(p.autoUpdate, { vm.setAutoUpdate(id, "plugin", p.file, it) }) },
                                        )
                                        if (i < rows.lastIndex) ListDivider(66.dp)
                                    }
                                } else {
                                    themeRows.forEachIndexed { i, t ->
                                        val job = jobs["$id|${JobKind.THEME.name}|${t.slug}"]
                                        val st = rowStateFor(job, jobs.values, t.updateAvailable, "v${t.version}" + if (t.active) " · thème actif" else "", t.autoUpdate)
                                        val j = job ?: UpdateJob(id, state.site.name, JobKind.THEME, t.slug, t.name, t.version, t.newVersion.orEmpty())
                                        UpdateRow(
                                            t.name, t.version, t.newVersion.orEmpty(), st,
                                            onUpdate = { vm.updateOne(j) }, onRetry = { vm.retry(j) },
                                            trailingUpToDate = { UiSwitch(t.autoUpdate, { vm.setAutoUpdate(id, "theme", t.slug, it) }) },
                                        )
                                        if (i < themeRows.lastIndex) ListDivider(66.dp)
                                    }
                                }
                            }
                        }
                        item {
                            Text(
                                "L'interrupteur active la mise à jour automatique par WordPress.",
                                fontSize = 12.sp, color = ui.textSubtle, modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                    else -> {
                        item { SectionLabel("Cœur et langues") }
                        item {
                            UiCard {
                                val coreJob = jobs["$id|${JobKind.CORE.name}|core"]
                                val cj = coreJob ?: UpdateJob(id, state.site.name, JobKind.CORE, "core", "WordPress", data.core.current, data.core.newVersion.orEmpty())
                                UpdateRow(
                                    "WordPress", data.core.current, data.core.newVersion.orEmpty(),
                                    rowStateFor(coreJob, jobs.values, data.core.updateAvailable, "Version ${data.core.current}"),
                                    onUpdate = { confirmCore = cj }, onRetry = { vm.retry(cj) }, icon = R.drawable.ic_ui_globe,
                                )
                                ListDivider(66.dp)
                                val trJob = jobs["$id|${JobKind.TRANSLATIONS.name}|translations"]
                                val tj = trJob ?: UpdateJob(id, state.site.name, JobKind.TRANSLATIONS, "translations", "Traductions", "${data.translations.size} paquet(s)", data.translations.map { it.language }.distinct().joinToString())
                                UpdateRow(
                                    "Traductions", "${data.translations.size} paquet(s)", data.translations.map { it.language }.distinct().joinToString(),
                                    rowStateFor(trJob, jobs.values, data.translations.isNotEmpty(), "Toutes les langues sont à jour"),
                                    onUpdate = { vm.updateOne(tj) }, onRetry = { vm.retry(tj) }, icon = R.drawable.ic_ui_translate,
                                )
                            }
                        }
                        item { SectionLabel("Serveur") }
                        item {
                            UiCard {
                                InfoLine("WordPress", data.info.wpVersion)
                                ListDivider(16.dp)
                                InfoLine("PHP", data.info.phpVersion)
                                ListDivider(16.dp)
                                InfoLine("Multisite", if (data.info.multisite) "Oui · ${data.info.sitesCount} sites" else "Non")
                                ListDivider(16.dp)
                                InfoLine("Installation des mises à jour", if (data.info.fileModsAllowed) "Autorisée" else "Désactivée")
                            }
                        }
                    }
                }
            }
        }

        // Barre d'action fixe : progression de la file ou « Tout mettre à jour »
        if (active.isNotEmpty() || pendingItems.isNotEmpty() || finished > 0) {
            Column(Modifier.background(ui.surface)) {
                ListDivider()
                Row(
                    Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (active.isNotEmpty()) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("$finished sur ${siteJobs.size} terminée${if (finished > 1) "s" else ""}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ui.text)
                            ProgressBar(finished.toFloat() / siteJobs.size.coerceAtLeast(1), ui.accent, ui.track)
                        }
                        if (active.any { it.state == JobState.QUEUED }) SecondaryButton("Arrêter", { vm.cancelQueue(id) })
                    } else if (pendingItems.isNotEmpty()) {
                        PrimaryButton(
                            "Tout mettre à jour (${pendingItems.size})",
                            { vm.updateEverything(id) },
                            Modifier.fillMaxWidth(),
                            icon = R.drawable.ic_ui_download,
                            enabled = data?.info?.fileModsAllowed != false,
                        )
                    } else {
                        val failed = siteJobs.count { it.state == JobState.FAILED }
                        Ico(if (failed == 0) R.drawable.ic_ui_check else R.drawable.ic_ui_alert, if (failed == 0) ui.success else ui.danger)
                        Text(
                            if (failed == 0) "Toutes les mises à jour sont terminées" else "$failed mise(s) à jour en échec",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ui.text, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    confirmCore?.let { j ->
        AlertDialog(
            onDismissRequest = { confirmCore = null },
            title = { Text("Mettre à jour WordPress ?") },
            text = { Text("WordPress va passer de ${j.from} à ${j.to}. Faites une sauvegarde avant une mise à jour majeure.") },
            confirmButton = { TextButton(onClick = { confirmCore = null; vm.updateOne(j) }) { Text("Mettre à jour") } },
            dismissButton = { TextButton(onClick = { confirmCore = null }) { Text("Annuler") } },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Retirer ce site ?") },
            text = { Text("Le site disparaîtra de l'application. Pensez aussi à révoquer la connexion dans WordPress (Réglages › Remote Updater).") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; onRemoved() }) { Text("Retirer", color = ui.danger) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Annuler") } },
        )
    }
    if (renaming) {
        var name by remember { mutableStateOf(state.site.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Renommer le site") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nom affiché") }) },
            confirmButton = { TextButton(onClick = { vm.renameSite(id, name); renaming = false }, enabled = name.isNotBlank()) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Annuler") } },
        )
    }
    if (moving) {
        FolderPickerDialog(
            folders, state.site.folderId,
            onDismiss = { moving = false },
            onPick = { moving = false; vm.moveSite(id, it) },
            onCreate = { n, c -> moving = false; vm.moveSite(id, vm.createFolder(n, c)) },
        )
    }
    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Mise à jour manuelle") },
            text = {
                Text(
                    "Téléverser « ${p.name} » (${formatSize(p.bytes.size.toLong())}) sur ${state.site.name} ?\n\n" +
                        if (p.type == "plugin") "Si cette extension est déjà installée, elle sera remplacée par cette version."
                        else "Si ce thème est déjà installé, il sera remplacé par cette version.",
                )
            },
            confirmButton = { TextButton(onClick = { pending = null; vm.uploadPackage(id, p.type, p.name, p.bytes) }) { Text("Téléverser") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val ui = LocalUi.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
        Text(label, fontSize = 14.sp, color = ui.textMuted, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ui.text)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format("%.1f Mo", bytes / 1_048_576.0)
    bytes >= 1024 -> "${bytes / 1024} Ko"
    else -> "$bytes o"
}
