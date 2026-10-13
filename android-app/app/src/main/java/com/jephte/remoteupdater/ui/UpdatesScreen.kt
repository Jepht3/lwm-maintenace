package com.jephte.remoteupdater.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jephte.remoteupdater.R

private fun JobKind.filterIndex() = when (this) {
    JobKind.CORE, JobKind.TRANSLATIONS -> 1
    JobKind.PLUGIN -> 2
    JobKind.THEME -> 3
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    vm: AppViewModel,
    sites: List<SiteState>,
    jobs: Map<String, UpdateJob>,
    filter: Int,
    onFilter: (Int) -> Unit,
    onOpenSite: (String) -> Unit,
) {
    val ui = LocalUi.current
    // Éléments décochés (par défaut tout est sélectionné)
    var deselected by rememberSaveable { mutableStateOf(setOf<String>()) }

    val pending = sites.flatMap { it.pending }
    // Une mise à jour en file / en cours / terminée reste visible tant que la série n'est pas effacée
    val tracked = jobs.values.filter { j -> pending.none { it.id == j.id } }
    val all = pending + tracked
    val shown = all.filter { filter == 0 || it.kind.filterIndex() == filter }
    val counts = (0..3).map { f -> pending.count { f == 0 || it.kind.filterIndex() == f } }
    val labels = listOf("Tout", "WordPress", "Extensions", "Thèmes").mapIndexed { i, l -> if (counts[i] > 0) "$l · ${counts[i]}" else l }

    val active = jobs.values.filter { it.state == JobState.QUEUED || it.state == JobState.RUNNING }
    val batch = jobs.values.filter { j -> active.any { it.siteId == j.siteId } }
    val selectable = shown.filter { jobs[it.id]?.let { j -> j.state == JobState.DONE || j.state == JobState.FAILED } ?: true }
        .filter { p -> pending.any { it.id == p.id } }
    val selected = selectable.filter { it.id !in deselected }

    Column(Modifier.fillMaxSize().background(ui.bg)) {
        PullToRefreshBox(
            isRefreshing = sites.any { it.loading },
            onRefresh = { vm.refreshAll(force = true) },
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScreenTitle("Mises à jour", Modifier.weight(1f))
                        SquareIconButton(R.drawable.ic_ui_refresh, "Rechercher les mises à jour", { vm.refreshAll(force = true) })
                    }
                }

                if (active.isNotEmpty()) {
                    item {
                        val done = batch.count { it.state == JobState.DONE || it.state == JobState.FAILED }
                        val running = active.firstOrNull { it.state == JobState.RUNNING }
                        UiCard(borderColor = ui.runningBorder) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Spinner(ui.accent, 18.dp)
                                    Text("Mise à jour en cours", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ui.text, modifier = Modifier.weight(1f))
                                    Text("$done / ${batch.size}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ui.onAccentSoft)
                                }
                                ProgressBar(done.toFloat() / batch.size.coerceAtLeast(1), ui.accent, ui.track, 8.dp)
                                if (running != null) {
                                    Text("${running.siteName} · ${running.name} ${running.from} → ${running.to}", fontSize = 12.sp, color = ui.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                item { FilterPills(labels, filter, onFilter) }

                if (shown.isEmpty()) {
                    item { AllUpToDate(sites.isEmpty()) }
                } else {
                    shown.groupBy { it.siteId }.forEach { (siteId, items) ->
                        item(key = "h-$siteId") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                sites.firstOrNull { it.site.id == siteId }?.let { st -> androidx.compose.foundation.layout.Box(Modifier.padding(top = 6.dp, start = 4.dp)) { SiteAvatar(st, 24.dp) } }
                                SectionLabel(items.first().siteName, Modifier.weight(1f))
                                Text(
                                    "Ouvrir", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ui.accent,
                                    modifier = Modifier.padding(top = 6.dp).toggleable(false, onValueChange = { onOpenSite(siteId) }, role = Role.Button).padding(4.dp),
                                )
                            }
                        }
                        item(key = "c-$siteId") {
                            UiCard {
                                items.forEachIndexed { i, item ->
                                    val job = jobs[item.id]
                                    val st = rowStateFor(job, jobs.values, true, "")
                                    val canSelect = st is RowState.Available
                                    UpdateRow(
                                        item.name, item.from, item.to, st,
                                        onUpdate = { vm.updateOne(item) },
                                        onRetry = { vm.retry(item) },
                                        icon = when (item.kind) {
                                            JobKind.CORE -> R.drawable.ic_ui_globe
                                            JobKind.TRANSLATIONS -> R.drawable.ic_ui_translate
                                            JobKind.THEME -> R.drawable.ic_ui_layout
                                            JobKind.PLUGIN -> null
                                        },
                                        leading = if (canSelect) {
                                            {
                                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                                    Checkbox(
                                                        checked = item.id !in deselected,
                                                        onCheckedChange = { c -> deselected = if (c) deselected - item.id else deselected + item.id },
                                                        colors = CheckboxDefaults.colors(checkedColor = Wp.Blue, uncheckedColor = ui.textSubtle),
                                                    )
                                                }
                                            }
                                        } else null,
                                    )
                                    if (i < items.lastIndex) ListDivider(66.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectable.isNotEmpty()) {
            Column(Modifier.background(ui.bg).padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp)) {
                PrimaryButton(
                    if (selected.size == selectable.size) "Tout mettre à jour (${selected.size})" else "Mettre à jour la sélection (${selected.size})",
                    { vm.enqueue(selected) },
                    Modifier.fillMaxWidth(),
                    icon = R.drawable.ic_ui_download,
                    enabled = selected.isNotEmpty(),
                    height = 54.dp,
                )
            }
        }
    }
}

@Composable
private fun AllUpToDate(noSites: Boolean) {
    val ui = LocalUi.current
    UiCard {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTile(R.drawable.ic_ui_check, ui.successSoft, ui.success, size = 64.dp, iconSize = 30.dp)
            Text(if (noSites) "Aucun site connecté" else "Tout est à jour", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = ui.text)
            Text(
                if (noSites) "Ajoutez un site avec le bouton + de l'accueil." else "Aucune mise à jour en attente. Tirez vers le bas pour vérifier à nouveau.",
                fontSize = 14.sp, color = ui.textMuted, textAlign = TextAlign.Center,
            )
        }
    }
}
