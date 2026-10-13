package com.jephte.remoteupdater.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jephte.remoteupdater.R
import kotlinx.coroutines.delay

/** État visuel d'une ligne de mise à jour. */
sealed interface RowState {
    data object Available : RowState
    data class Queued(val position: Int) : RowState
    data class Running(val startedAt: Long) : RowState
    data class Done(val version: String) : RowState
    data class Failed(val message: String) : RowState
    data class UpToDate(val detail: String, val auto: Boolean = false) : RowState
}

/** Calcule l'état d'une ligne à partir de la file d'attente. */
fun rowStateFor(job: UpdateJob?, jobs: Collection<UpdateJob>, updateAvailable: Boolean, upToDateDetail: String, auto: Boolean = false): RowState {
    if (job != null) {
        return when (job.state) {
            JobState.RUNNING -> RowState.Running(job.startedAt)
            JobState.QUEUED -> {
                val ahead = jobs.count {
                    it.siteId == job.siteId && it !== job &&
                        (it.state == JobState.RUNNING || (it.state == JobState.QUEUED && it.kind.order <= job.kind.order && it.id < job.id))
                }
                RowState.Queued(ahead + 1)
            }
            JobState.DONE -> RowState.Done(job.to)
            JobState.FAILED -> RowState.Failed(job.message ?: "Échec de la mise à jour")
        }
    }
    return if (updateAvailable) RowState.Available else RowState.UpToDate(upToDateDetail, auto)
}

@Composable
fun UpdateRow(
    name: String,
    from: String,
    to: String,
    state: RowState,
    onUpdate: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    trailingUpToDate: (@Composable () -> Unit)? = null,
) {
    val ui = LocalUi.current
    val running = state is RowState.Running
    Column(
        modifier
            .fillMaxWidth()
            .background(if (running) ui.runningBg else androidx.compose.ui.graphics.Color.Transparent)
            .animateContentSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Vignette de gauche : reflète l'état
            when (state) {
                is RowState.Running -> androidx.compose.foundation.layout.Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ui.surface)
                        .border(1.dp, ui.runningBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { Spinner(ui.accent, 20.dp) }
                is RowState.Queued -> IconTile(R.drawable.ic_ui_clock, ui.neutralSoft, ui.textMuted)
                is RowState.Done -> IconTile(R.drawable.ic_ui_check, ui.successSoft, ui.success)
                is RowState.Failed -> IconTile(R.drawable.ic_ui_alert, ui.dangerSoft, ui.danger)
                else -> if (leading != null) leading() else if (icon != null) IconTile(icon, ui.accentSoft, ui.accent) else LetterTile(name, 40.dp)
            }

            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ui.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when (state) {
                    is RowState.Done -> Text("$to installée", style = MonoStyle, color = ui.successText, maxLines = 1)
                    is RowState.UpToDate -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.auto) Ico(R.drawable.ic_ui_zap, ui.accent, 12.dp)
                        Text(state.detail, fontSize = 12.sp, color = ui.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    else -> Text(
                        if (to.isNotEmpty()) "$from → $to" else from,
                        style = MonoStyle,
                        color = if (running) ui.onAccentSoft else ui.textSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            when (state) {
                RowState.Available -> SecondaryButton("Mettre à jour", onUpdate, height = 38.dp, color = ui.accent)
                is RowState.Queued -> StatusPill("En file · ${state.position}ᵉ", ui.textMuted, ui.neutralSoft)
                is RowState.Running -> Text("En cours · ${elapsed(state.startedAt)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ui.onAccentSoft)
                is RowState.Done -> Text("Terminé", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ui.successText)
                is RowState.Failed -> SecondaryButton("Réessayer", onRetry, height = 36.dp, color = ui.danger)
                is RowState.UpToDate -> if (trailingUpToDate != null) trailingUpToDate() else StatusPill("À jour", ui.successText, ui.successSoft, R.drawable.ic_ui_check)
            }
        }
        if (running) IndeterminateBar(ui.accent, ui.track)
        if (state is RowState.Failed) {
            Text(
                state.message,
                fontSize = 12.sp,
                color = ui.dangerText,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ui.dangerSoft).padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/** Chronomètre « 12 s », « 1 min 05 » mis à jour chaque seconde. */
@Composable
fun elapsed(startedAt: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val s = ((now - startedAt) / 1000).coerceAtLeast(0)
    return if (s < 60) "$s s" else "${s / 60} min ${"%02d".format(s % 60)}"
}
