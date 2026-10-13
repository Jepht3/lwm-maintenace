package com.jephte.remoteupdater.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jephte.remoteupdater.R
import com.jephte.remoteupdater.data.AccountStore
import com.jephte.remoteupdater.data.AppLock
import com.jephte.remoteupdater.data.AppSettings

private data class Tab(val label: String, @DrawableRes val icon: Int)

private val tabs = listOf(
    Tab("Accueil", R.drawable.ic_ui_home),
    Tab("Mises à jour", R.drawable.ic_ui_download),
    Tab("Historique", R.drawable.ic_ui_history),
    Tab("Réglages", R.drawable.ic_ui_sliders),
)

/** Application déverrouillée : 4 onglets + écrans secondaires. */
@Composable
fun AppRoot(
    vm: AppViewModel,
    settings: AppSettings,
    account: AccountStore,
    lock: AppLock,
    biometricAvailable: Boolean,
    onBiometric: () -> Unit,
    onLockNow: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val ui = LocalUi.current
    val sites by vm.sites.collectAsState()
    val folders by vm.folders.collectAsState()
    val history by vm.history.collectAsState()
    val jobs by vm.jobs.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // "" = onglets ; "add" ; "pin" ; "password" ; sinon identifiant du site ouvert
    var route by rememberSaveable { mutableStateOf("") }
    var updatesFilter by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long) }
    }
    BackHandler(enabled = route.isNotEmpty() || tab != 0) { if (route.isNotEmpty()) route = "" else tab = 0 }

    val pendingCount = sites.sumOf { it.updateCount }
    val running = jobs.values.any { it.state == JobState.RUNNING || it.state == JobState.QUEUED }

    Box(Modifier.fillMaxSize().background(ui.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        if (targetState.isNotEmpty()) (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(260))) togetherWith fadeOut(tween(160))
                        else fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(180)))
                    },
                    label = "route",
                ) { r ->
                    when (r) {
                        "" -> AnimatedContent(tab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
                            when (t) {
                                0 -> HomeScreen(
                                    vm, account.name, sites, folders, jobs,
                                    onOpenSite = { route = it },
                                    onAdd = { route = "add" },
                                    onOpenUpdates = { updatesFilter = it; tab = 1 },
                                )
                                1 -> UpdatesScreen(vm, sites, jobs, updatesFilter, { updatesFilter = it }, onOpenSite = { route = it })
                                2 -> HistoryScreen(vm, history)
                                else -> SettingsScreen(
                                    vm, settings, account, lock, sites.size, biometricAvailable,
                                    onChangePin = { route = "pin" },
                                    onChangePassword = { route = "password" },
                                    onLockNow = onLockNow,
                                    onLogout = onLogout,
                                    onDeleteAccount = onDeleteAccount,
                                    onAddSite = { route = "add" },
                                    onAbout = { route = "about" },
                                )
                            }
                        }
                        "add" -> AddSiteScreen(vm, onBack = { route = "" }, onAdded = { route = ""; tab = 0 })
                        "pin" -> PinScreen(
                            PinMode.CHANGE, lock, account.name, biometricAvailable, onBiometric,
                            onDone = { route = ""; vm.notify("Code PIN modifié") },
                            onCancel = { route = "" },
                        )
                        "about" -> AboutScreen(vm, onBack = { route = "" })
                        "password" -> ChangePasswordScreen(account, onBack = { route = "" }, onDone = { route = ""; vm.notify("Mot de passe modifié") })
                        else -> {
                            val state = sites.firstOrNull { it.site.id == r }
                            if (state == null) LaunchedEffect(Unit) { route = "" }
                            else SiteDetailScreen(vm, state, jobs, onBack = { route = "" }, onRemoved = { vm.removeSite(state.site.id); route = "" })
                        }
                    }
                }
            }
            if (route.isEmpty()) BottomNav(tab, pendingCount, running) { tab = it }
        }
        // Fond sous la barre d'état : le contenu qui défile ne passe pas sous l'heure.
        if (route.isEmpty()) Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(ui.bg))

        SnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (route.isEmpty()) 96.dp else 80.dp),
        ) { data ->
            Snackbar(data, containerColor = Wp.Gray90, contentColor = Color.White, actionColor = Wp.Blue30, dismissActionContentColor = Wp.Gray10, shape = RoundedCornerShape(14.dp))
        }
    }
}

@Composable
private fun BottomNav(selected: Int, badge: Int, running: Boolean, onSelect: (Int) -> Unit) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxWidth().background(ui.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ui.border))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEachIndexed { i, t ->
                val active = i == selected
                val fg by animateColorAsState(if (active) ui.accent else ui.textMuted, label = "fg")
                val pill by animateColorAsState(if (active) ui.accentSoft else Color.Transparent, label = "pill")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .selectable(active, onClick = { onSelect(i) }, role = Role.Tab, interactionSource = remember { MutableInteractionSource() }, indication = null)
                        .padding(vertical = 4.dp),
                ) {
                    Box {
                        Box(Modifier.width(56.dp).height(32.dp).clip(CircleShape).background(pill), contentAlignment = Alignment.Center) {
                            if (i == 1 && running) Spinner(fg, 20.dp) else Ico(t.icon, fg, 22.dp, t.label)
                        }
                        if (i == 1 && badge > 0) {
                            Box(
                                Modifier.align(Alignment.TopCenter).offset(x = 15.dp, y = (-5).dp)
                                    .widthIn(min = 20.dp).height(20.dp).clip(CircleShape).background(Wp.Red)
                                    .border(2.dp, ui.surface, CircleShape).padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (badge > 99) "99+" else "$badge", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                    Text(t.label, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, color = fg, maxLines = 1)
                }
            }
        }
    }
}
