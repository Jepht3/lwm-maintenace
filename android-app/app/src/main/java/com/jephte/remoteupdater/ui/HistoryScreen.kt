package com.jephte.remoteupdater.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Lundi de la semaine contenant [t]. */
private fun startOfWeek(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(t)
    firstDayOfWeek = Calendar.MONDAY
    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
}.timeInMillis

/** Historique : sélecteur de jour, graphique de la semaine, statistiques et liste des actions. */
@Composable
fun HistoryScreen(vm: AppViewModel, history: List<LogEntry>) {
    val colors = LocalAppColors.current
    val today = startOfDay(System.currentTimeMillis())
    var weekStart by rememberSaveable { mutableLongStateOf(startOfWeek(today)) }
    var selectedDay by rememberSaveable { mutableIntStateOf(((today - startOfWeek(today)) / DAY).toInt()) }
    var confirmClear by remember { mutableStateOf(false) }

    val days = (0 until 7).map { weekStart + it * DAY }
    val perDay = days.map { d -> history.filter { it.time >= d && it.time < d + DAY } }
    val week = perDay.flatten()
    val dayEntries = perDay[selectedDay]
    val dayDate = days[selectedDay]

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 14.dp),
            ) {
                RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Semaine précédente", { weekStart -= 7 * DAY })
                Spacer(Modifier.weight(1f))
                if (weekStart < startOfWeek(today)) {
                    RoundIconButton(Icons.AutoMirrored.Rounded.ArrowForward, "Semaine suivante", { weekStart += 7 * DAY })
                    Spacer(Modifier.width(10.dp))
                }
                RoundIconButton(Icons.Outlined.DeleteSweep, "Effacer l'historique", { confirmClear = true })
            }
        }
        item {
            Text("Votre activité", fontSize = 36.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                (if (dayDate == today) "Aujourd'hui, " else "") + SimpleDateFormat("EEEE d MMMM yyyy", FR).format(Date(dayDate)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            // Sélecteur de jour (Lun → Dim)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                days.forEachIndexed { i, d ->
                    val active = i == selectedDay
                    val future = d > today
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(colors.accentCool.copy(alpha = if (future) 0.25f else 0.55f))
                            .clickable(enabled = !future) { selectedDay = i }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            SimpleDateFormat("EEE", FR).format(Date(d)).trimEnd('.').replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(if (active) colors.chipDark else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                SimpleDateFormat("d", FR).format(Date(d)),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) colors.onChipDark else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
        item {
            // Graphique de la semaine
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(colors.accentCool.copy(alpha = 0.7f))
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("Mises à jour de la semaine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "${week.size} action(s) · ${"%.1f".format(FR, week.size / 7f)} par jour en moyenne",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(colors.chipDark),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${dayEntries.size}", color = colors.onChipDark, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(18.dp))
                WeekBars(perDay.map { it.size }, selectedDay, onSelect = { if (days[it] <= today) selectedDay = it })
            }
        }
        item {
            val ok = week.count { it.ok }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigNumberTile(ok, "réussies cette semaine", colors.accentWarm, Modifier.weight(1f))
                BigNumberTile(week.size - ok, "échecs cette semaine", colors.accentCool, Modifier.weight(1f))
            }
        }
        item {
            SectionHeader(if (dayDate == today) "Aujourd'hui" else SimpleDateFormat("EEEE d MMMM", FR).format(Date(dayDate)).replaceFirstChar { it.uppercase() })
        }
        if (dayEntries.isEmpty()) {
            item {
                Text(
                    if (history.isEmpty()) "Les mises à jour effectuées depuis l'application apparaîtront ici."
                    else "Aucune action ce jour-là.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        } else {
            items(dayEntries) { e -> HistoryCard(e) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Effacer l'historique ?") },
            text = { Text("Toutes les actions enregistrées sur ce téléphone seront supprimées.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearHistory()
                }) { Text("Effacer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}

/** Barres arrondies : jour sélectionné en bleu plein, les autres hachurées. */
@Composable
private fun WeekBars(values: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val hatch = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val labels = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().height(220.dp)) {
        values.forEachIndexed { i, v ->
            val target = 0.12f + 0.88f * (v.toFloat() / max)
            val fraction by animateFloatAsState(target, tween(600), label = "bar")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Canvas(Modifier.fillMaxWidth().fillMaxHeight(fraction)) {
                        val r = CornerRadius(size.width / 2, size.width / 2)
                        if (i == selected) {
                            drawRoundRect(WpColors.Blue, cornerRadius = r)
                        } else {
                            val path = Path().apply {
                                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, r))
                            }
                            clipPath(path) {
                                val step = 7.dp.toPx()
                                var x = -size.height
                                while (x < size.width) {
                                    drawLine(hatch, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.2.dp.toPx())
                                    x += step
                                }
                            }
                            drawRoundRect(hatch.copy(alpha = 0.25f), size = Size(size.width, size.height), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    labels[i],
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun BigNumberTile(value: Int, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(color)
            .padding(18.dp),
    ) {
        Text(value.toString().padStart(2, '0'), fontSize = 48.sp, lineHeight = 50.sp, fontWeight = FontWeight.Normal)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryCard(e: LogEntry) {
    val colors = LocalAppColors.current
    SoftCard(Modifier.background(Color.Transparent)) {
        Row {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(if (e.ok) colors.successContainer else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (e.ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                    null,
                    tint = if (e.ok) colors.success else MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    SimpleDateFormat("HH:mm", FR).format(Date(e.time)) + " · " + e.site,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(e.text, style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
