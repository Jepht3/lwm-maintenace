package com.jephte.remoteupdater.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Une mise à jour en attente, tous sites confondus. */
private data class PendingItem(
    val siteId: String,
    val siteName: String,
    val kind: Kind,
    val key: String,
    val name: String,
    val from: String,
    val to: String,
    val busy: Boolean,
) {
    enum class Kind(val label: String, val icon: ImageVector, val filter: Int) {
        CORE("WordPress", Icons.Rounded.Language, 1),
        TRANSLATION("Traductions", Icons.Rounded.Translate, 1),
        PLUGIN("Extension", Icons.Rounded.Extension, 2),
        THEME("Thème", Icons.Rounded.Palette, 3),
    }
}

private fun pendingFor(s: SiteState): List<PendingItem> {
    val d = s.data ?: return emptyList()
    val all = "all" in s.busy
    val name = s.site.name
    val out = mutableListOf<PendingItem>()
    if (d.core.updateAvailable) {
        out += PendingItem(s.site.id, name, PendingItem.Kind.CORE, "core", "WordPress", d.core.current, d.core.newVersion.orEmpty(), all || "core" in s.busy)
    }
    d.plugins.filter { it.updateAvailable }.forEach {
        out += PendingItem(s.site.id, name, PendingItem.Kind.PLUGIN, it.file, it.name, it.version, it.newVersion.orEmpty(), all || "plugins" in s.busy || "plugin:${it.file}" in s.busy)
    }
    d.themes.filter { it.updateAvailable }.forEach {
        out += PendingItem(s.site.id, name, PendingItem.Kind.THEME, it.slug, it.name, it.version, it.newVersion.orEmpty(), all || "themes" in s.busy || "theme:${it.slug}" in s.busy)
    }
    if (d.translations.isNotEmpty()) {
        out += PendingItem(
            s.site.id, name, PendingItem.Kind.TRANSLATION, "translations", "Traductions",
            d.translations.joinToString { it.slug }, "${d.translations.size} paquet(s)", all || "translations" in s.busy,
        )
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    vm: AppViewModel,
    sites: List<SiteState>,
    filter: Int,
    onFilter: (Int) -> Unit,
    onOpenSite: (String) -> Unit,
) {
    var confirmAll by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current

    val all = sites.flatMap { pendingFor(it) }
    val shown = all.filter { filter == 0 || it.kind.filter == filter }
    val counts = listOf(all.size, all.count { it.kind.filter == 1 }, all.count { it.kind.filter == 2 }, all.count { it.kind.filter == 3 })
    val labels = listOf("Tout", "WordPress", "Extensions", "Thèmes").mapIndexed { i, l -> if (counts[i] > 0) "$l · ${counts[i]}" else l }

    PullToRefreshBox(
        isRefreshing = sites.any { it.loading },
        onRefresh = { vm.refreshAll(force = true) },
        modifier = Modifier.fillMaxSize().background(colors.tint),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 18.dp, top = 14.dp),
                ) {
                    Text("Mises à jour", fontSize = 36.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    RoundIconButton(Icons.Rounded.Refresh, "Rechercher les mises à jour", { vm.refreshAll(force = true) })
                }
            }
            item {
                FilterRow(labels, filter, onFilter, Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp))
            }
            if (all.isNotEmpty()) {
                item {
                    Button(
                        onClick = { confirmAll = true },
                        enabled = sites.none { "all" in it.busy },
                        colors = ButtonDefaults.buttonColors(containerColor = WpColors.Blue),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    ) {
                        Icon(Icons.Rounded.SystemUpdateAlt, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tout mettre à jour (${all.size})", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (shown.isEmpty()) {
                item { AllGood(sites.isEmpty(), Modifier.padding(horizontal = 18.dp)) }
            } else {
                items(shown, key = { "${it.siteId}/${it.kind}/${it.key}" }) { item ->
                    PendingCard(item, Modifier.padding(horizontal = 18.dp), onOpenSite = { onOpenSite(item.siteId) }) {
                        when (item.kind) {
                            PendingItem.Kind.CORE -> vm.updateCore(item.siteId)
                            PendingItem.Kind.PLUGIN -> vm.updatePlugin(item.siteId, item.key)
                            PendingItem.Kind.THEME -> vm.updateTheme(item.siteId, item.key)
                            PendingItem.Kind.TRANSLATION -> vm.updateTranslations(item.siteId)
                        }
                    }
                }
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            icon = { Icon(Icons.Rounded.SystemUpdateAlt, null) },
            title = { Text("Tout mettre à jour ?") },
            text = { Text("${all.size} mise(s) à jour seront installées sur ${all.map { it.siteId }.distinct().size} site(s). Pensez à vos sauvegardes avant une mise à jour majeure de WordPress.") },
            confirmButton = {
                Button(onClick = {
                    confirmAll = false
                    vm.updateEverywhere()
                }) { Text("Mettre à jour") }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun PendingCard(item: PendingItem, modifier: Modifier, onOpenSite: () -> Unit, onUpdate: () -> Unit) {
    SoftCard(modifier, onClick = onOpenSite) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(item.siteName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(item.name, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (item.kind == PendingItem.Kind.TRANSLATION) item.from else "Version ${item.from} installée",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(item.kind.icon, null, tint = WpColors.Blue) }
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChipPill(if (item.kind == PendingItem.Kind.TRANSLATION) item.to else "v${item.to}", dark = true)
            Spacer(Modifier.width(8.dp))
            ChipPill(item.kind.label, dark = false)
            Spacer(Modifier.weight(1f))
            if (item.busy) Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) { SmallSpinner() }
            else ArrowButton(onClick = onUpdate, icon = Icons.Rounded.Download, contentDescription = "Mettre à jour ${item.name}")
        }
    }
}

@Composable
private fun AllGood(noSites: Boolean, modifier: Modifier) {
    val colors = LocalAppColors.current
    SoftCard(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Box(Modifier.size(80.dp).clip(CircleShape).background(colors.successContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.TaskAlt, null, tint = colors.success, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(if (noSites) "Aucun site connecté" else "Tout est à jour", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                if (noSites) "Ajoutez un site avec le bouton + pour suivre ses mises à jour."
                else "Aucune mise à jour en attente dans cette catégorie. Tirez vers le bas pour vérifier à nouveau.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
