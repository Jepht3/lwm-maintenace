package com.jephte.remoteupdater.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jephte.remoteupdater.R
import com.jephte.remoteupdater.data.SiteRepository
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    userName: String,
    sites: List<SiteState>,
    jobs: Map<String, UpdateJob>,
    onOpenSite: (String) -> Unit,
    onAdd: () -> Unit,
    onOpenUpdates: (filter: Int) -> Unit,
) {
    val ui = LocalUi.current
    var confirmAll by remember { mutableStateOf(false) }
    val pending = sites.flatMap { it.pending }
    val upToDate = sites.count { it.data != null && it.updateCount == 0 && it.error == null }
    val active = jobs.values.filter { it.state == JobState.QUEUED || it.state == JobState.RUNNING }
    val batch = jobs.values.filter { j -> active.any { it.siteId == j.siteId } }
    val lastCheck = sites.maxOfOrNull { it.fetchedAt } ?: 0L

    PullToRefreshBox(
        isRefreshing = sites.any { it.loading },
        onRefresh = { vm.refreshAll(force = true) },
        modifier = Modifier.fillMaxSize().background(ui.bg),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp),
                ) {
                    AppLogo(40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(greeting(userName), fontSize = 13.sp, color = ui.textSubtle, fontWeight = FontWeight.Medium)
                        Text("Vos sites", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ui.text, letterSpacing = (-0.3).sp)
                    }
                    SquareIconButton(R.drawable.ic_ui_plus, "Ajouter un site", onAdd)
                }
            }

            if (sites.isEmpty()) {
                item { EmptyHome(onAdd) }
                return@LazyColumn
            }

            item {
                // Carte de synthèse (gris barre d'admin, couleur unie)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(ui.hero)
                        .border(1.dp, ui.heroBorder, RoundedCornerShape(24.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Ring(upToDate, sites.size)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (pending.isEmpty()) "Tout est à jour" else "${pending.size} mise${if (pending.size > 1) "s" else ""} à jour",
                                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
                            )
                            Text(
                                (if (pending.isEmpty()) "${sites.size} site(s) surveillé(s)" else "sur ${pending.map { it.siteId }.distinct().size} site(s)") +
                                    (if (lastCheck > 0) " · vérifié ${relativeTime(lastCheck)}" else ""),
                                color = ui.heroMuted, fontSize = 13.sp, lineHeight = 18.sp,
                            )
                        }
                    }
                    if (active.isNotEmpty()) {
                        val done = batch.count { it.state == JobState.DONE || it.state == JobState.FAILED }
                        val current = active.firstOrNull { it.state == JobState.RUNNING }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Spinner(ui.heroAccent, 16.dp)
                                Text("Mise à jour en cours", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("$done / ${batch.size}", color = ui.heroAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            ProgressBar(done.toFloat() / batch.size.coerceAtLeast(1), ui.heroAccent, ui.heroTrack, 8.dp)
                            if (current != null) Text("${current.siteName} · ${current.name}", color = ui.heroMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else if (pending.isNotEmpty()) {
                        PrimaryButton("Tout mettre à jour", { confirmAll = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_ui_download, height = 50.dp)
                    }
                }
            }

            item {
                val wp = pending.count { it.kind == JobKind.CORE || it.kind == JobKind.TRANSLATIONS }
                val pl = pending.count { it.kind == JobKind.PLUGIN }
                val th = pending.count { it.kind == JobKind.THEME }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(R.drawable.ic_ui_globe, wp, "WordPress", Modifier.weight(1f)) { onOpenUpdates(1) }
                    StatCard(R.drawable.ic_ui_plug, pl, "Extensions", Modifier.weight(1f)) { onOpenUpdates(2) }
                    StatCard(R.drawable.ic_ui_layout, th, "Thèmes", Modifier.weight(1f)) { onOpenUpdates(3) }
                }
            }

            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Sites connectés", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ui.text, modifier = Modifier.weight(1f))
                    Text("${sites.size} site${if (sites.size > 1) "s" else ""}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ui.accent)
                }
            }

            item {
                UiCard {
                    sites.forEachIndexed { i, s ->
                        SiteRow(s, jobs.values.filter { it.siteId == s.site.id }) { onOpenSite(s.site.id) }
                        if (i < sites.lastIndex) ListDivider(70.dp)
                    }
                }
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Tout mettre à jour ?") },
            text = { Text("${pending.size} mise(s) à jour vont être installées, une par une, sur ${pending.map { it.siteId }.distinct().size} site(s). Pensez à vos sauvegardes avant une mise à jour majeure de WordPress.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAll = false
                    vm.updateEverywhere()
                }) { Text("Mettre à jour") }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Annuler") } },
        )
    }
}

private fun greeting(name: String): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hello = if (h in 5..17) "Bonjour" else "Bonsoir"
    return if (name.isNotEmpty()) "$hello $name" else hello
}

/** Anneau « sites à jour / total ». */
@Composable
private fun Ring(value: Int, total: Int) {
    val ui = LocalUi.current
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(84.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val sz = Size(size.width - stroke, size.height - stroke)
            drawArc(ui.heroTrack, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
            if (total > 0 && value > 0) {
                drawArc(ui.heroAccent, -90f, 360f * value / total, false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value/$total", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("à jour", color = ui.heroMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatCard(@DrawableRes icon: Int, value: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    val ui = LocalUi.current
    UiCard(modifier, onClick = onClick, radius = 18.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(icon, ui.accentSoft, ui.accent, size = 32.dp, iconSize = 18.dp)
            Column {
                Text("$value", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (value > 0) ui.text else ui.textSubtle)
                Text(label, fontSize = 12.sp, color = ui.textSubtle, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SiteRow(s: SiteState, siteJobs: List<UpdateJob>, onClick: () -> Unit) {
    val ui = LocalUi.current
    val active = siteJobs.filter { it.state == JobState.QUEUED || it.state == JobState.RUNNING }
    val running = siteJobs.firstOrNull { it.state == JobState.RUNNING }
    val done = siteJobs.count { it.state == JobState.DONE || it.state == JobState.FAILED }
    val failed = siteJobs.count { it.state == JobState.FAILED }
    val d = s.data

    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    active.isNotEmpty() -> LetterTile(s.site.name, 42.dp, ui.accentSoft, ui.accent)
                    s.error != null -> LetterTile(s.site.name, 42.dp, ui.dangerSoft, ui.danger)
                    s.updateCount > 0 -> LetterTile(s.site.name, 42.dp, ui.warningSoft, ui.warning)
                    d != null -> LetterTile(s.site.name, 42.dp, ui.successSoft, ui.successText)
                    else -> LetterTile(s.site.name, 42.dp, ui.neutralSoft, ui.textMuted)
                }
                Column(Modifier.weight(1f)) {
                    Text(s.site.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ui.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        SiteRepository.hostOf(s.site.url) + (d?.let { if (it.info.multisite) " · Multisite (${it.info.sitesCount})" else " · WP ${it.core.current}" } ?: ""),
                        fontSize = 12.sp, color = ui.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                when {
                    active.isNotEmpty() -> StatusPill("$done / ${siteJobs.size}", ui.onAccentSoft, ui.accentSoft, spinning = true)
                    s.loading && d == null -> Spinner(ui.accent, 18.dp)
                    s.error != null -> StatusPill("Hors ligne", ui.dangerText, ui.dangerSoft, R.drawable.ic_ui_alert)
                    failed > 0 -> StatusPill("$failed échec(s)", ui.dangerText, ui.dangerSoft, R.drawable.ic_ui_alert)
                    s.updateCount > 0 -> StatusPill("${s.updateCount} en attente", ui.warning, ui.warningSoft)
                    d != null -> StatusPill("À jour", ui.successText, ui.successSoft, R.drawable.ic_ui_check)
                }
            }
            if (active.isNotEmpty()) {
                ProgressBar(done.toFloat() / siteJobs.size.coerceAtLeast(1), ui.accent, ui.track)
                Text(
                    if (running != null) "Mise à jour de ${running.name}…" else "En attente…",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ui.onAccentSoft,
                )
            }
        }
    }
}

@Composable
private fun EmptyHome(onAdd: () -> Unit) {
    val ui = LocalUi.current
    UiCard {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(R.drawable.ic_ui_globe, ui.accentSoft, ui.accent, size = 64.dp, iconSize = 30.dp)
            Spacer(Modifier.height(4.dp))
            Text("Connectez votre premier site", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = ui.text)
            Text(
                "Installez l'extension « Remote Updater » sur votre site WordPress, puis générez un code de connexion dans Réglages › Remote Updater.",
                fontSize = 14.sp, lineHeight = 20.sp, color = ui.textMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            PrimaryButton("Connecter un site", onAdd, Modifier.fillMaxWidth(), icon = R.drawable.ic_ui_plus)
        }
    }
}

/** « il y a 5 min » */
fun relativeTime(time: Long, now: Long = System.currentTimeMillis()): String {
    val s = (now - time) / 1000
    return when {
        s < 60 -> "à l'instant"
        s < 3600 -> "il y a ${s / 60} min"
        s < 86_400 -> "il y a ${s / 3600} h"
        s < 7 * 86_400 -> "il y a ${s / 86_400} j"
        else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.FRANCE).format(java.util.Date(time))
    }
}
