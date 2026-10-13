package com.jephte.remoteupdater.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jephte.remoteupdater.R
import com.jephte.remoteupdater.data.LogEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FR = Locale.FRANCE
private const val DAY = 86_400_000L

private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dayLabel(day: Long, today: Long): String = when (day) {
    today -> "Aujourd'hui"
    today - DAY -> "Hier"
    else -> SimpleDateFormat("EEEE d MMMM", FR).format(Date(day)).replaceFirstChar { it.uppercase() }
}

@Composable
fun HistoryScreen(vm: AppViewModel, history: List<LogEntry>) {
    val ui = LocalUi.current
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }

    val today = startOfDay(System.currentTimeMillis())
    val days = (6 downTo 0).map { today - it * DAY }
    val perDay = days.map { d -> history.count { it.time >= d && it.time < d + DAY } }
    val week = history.filter { it.time >= days.first() }
    val shown = history.filter { filter == 0 || (filter == 1 && it.ok) || (filter == 2 && !it.ok) }
    val grouped = shown.groupBy { startOfDay(it.time) }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().background(ui.bg),
    ) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Historique", Modifier.weight(1f))
                if (history.isNotEmpty()) SquareIconButton(R.drawable.ic_ui_trash, "Effacer l'historique", { confirmClear = true })
            }
        }
        item {
            UiCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("7 derniers jours", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ui.text, modifier = Modifier.weight(1f))
                        Text(
                            "${SimpleDateFormat("d MMM", FR).format(Date(days.first()))} – ${SimpleDateFormat("d MMM", FR).format(Date(today))}",
                            fontSize = 12.sp, color = ui.textSubtle,
                        )
                    }
                    val max = (perDay.maxOrNull() ?: 0).coerceAtLeast(1)
                    Row(Modifier.fillMaxWidth().height(86.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        days.forEachIndexed { i, d ->
                            val isToday = d == today
                            val frac by animateFloatAsState(0.06f + 0.94f * perDay[i] / max, tween(600), label = "bar")
                            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                    Box(
                                        Modifier.fillMaxWidth().fillMaxHeight(frac).clip(RoundedCornerShape(6.dp))
                                            .background(if (isToday) ui.accent else ui.track),
                                    )
                                }
                                Text(
                                    SimpleDateFormat("EEEEE", FR).format(Date(d)).uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isToday) ui.text else ui.textSubtle,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                    Row {
                        Stat(week.size, "mises à jour", ui.text, Modifier.weight(1f))
                        Stat(week.count { it.ok }, "réussies", ui.successText, Modifier.weight(1f))
                        Stat(week.count { !it.ok }, "échecs", ui.dangerText, Modifier.weight(1f))
                    }
                }
            }
        }
        item { FilterPills(listOf("Tout", "Réussies", "Échecs"), filter, { filter = it }) }

        if (shown.isEmpty()) {
            item {
                UiCard {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconTile(R.drawable.ic_ui_history, ui.neutralSoft, ui.textMuted, size = 56.dp, iconSize = 26.dp)
                        Text("Rien pour l'instant", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ui.text)
                        Text("Les mises à jour lancées depuis l'app apparaîtront ici.", fontSize = 14.sp, color = ui.textMuted)
                    }
                }
            }
        }
        grouped.forEach { (day, entries) ->
            item(key = "d$day") { SectionLabel(dayLabel(day, today)) }
            item(key = "c$day") {
                UiCard {
                    entries.forEachIndexed { i, e ->
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(if (e.ok) ui.successSoft else ui.dangerSoft),
                                contentAlignment = Alignment.Center,
                            ) { Ico(if (e.ok) R.drawable.ic_ui_check else R.drawable.ic_ui_close, if (e.ok) ui.success else ui.danger, 16.dp) }
                            Column(Modifier.weight(1f)) {
                                Text(e.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ui.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text("${e.site} · ${SimpleDateFormat("HH:mm", FR).format(Date(e.time))}", fontSize = 12.sp, color = ui.textSubtle)
                            }
                        }
                        if (i < entries.lastIndex) ListDivider(60.dp)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Effacer l'historique ?") },
            text = { Text("Toutes les actions enregistrées sur ce téléphone seront supprimées.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearHistory() }) { Text("Effacer", color = ui.danger) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Stat(value: Int, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val ui = LocalUi.current
    Column(modifier) {
        Text("$value", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 12.sp, color = ui.textSubtle)
    }
}
