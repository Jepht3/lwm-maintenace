package com.jephte.remoteupdater.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jephte.remoteupdater.data.AppLock
import com.jephte.remoteupdater.data.AppSettings

private data class NavItem(val label: String, val icon: ImageVector, val iconSelected: ImageVector)

private val navItems = listOf(
    NavItem("Accueil", Icons.Outlined.Home, Icons.Rounded.Home),
    NavItem("Mises à jour", Icons.Outlined.SystemUpdateAlt, Icons.Rounded.SystemUpdateAlt),
    NavItem("Historique", Icons.Outlined.History, Icons.Rounded.History),
    NavItem("Réglages", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

/**
 * Racine de l'application déverrouillée : 3 onglets (Sites, Mises à jour, Réglages)
 * avec barre de navigation flottante, et écrans secondaires (site, ajout, code PIN).
 */
@Composable
fun AppRoot(
    vm: AppViewModel,
    settings: AppSettings,
    lock: AppLock,
    biometricAvailable: Boolean,
    onBiometric: () -> Unit,
    onLockNow: () -> Unit,
) {
    val sites by vm.sites.collectAsState()
    val history by vm.history.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // "" = onglets ; "add" ; "pin" ; sinon identifiant du site ouvert
    var route by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    var updatesFilter by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long) }
    }
    BackHandler(enabled = route.isNotEmpty() || tab != 0) {
        if (route.isNotEmpty()) route = "" else tab = 0
    }

    val totalUpdates = sites.sumOf { it.updateCount }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                if (targetState.isNotEmpty()) {
                    (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280))) togetherWith fadeOut(tween(180))
                } else {
                    fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)))
                }
            },
            label = "route",
        ) { r ->
            when (r) {
                "" -> AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                    label = "tab",
                ) { t ->
                    when (t) {
                        0 -> SitesScreen(
                            vm = vm,
                            sites = sites,
                            onOpen = { route = it },
                            onAdd = { route = "add" },
                            onOpenSettings = { tab = 3 },
                            onOpenUpdates = {
                                updatesFilter = it
                                tab = 1
                            },
                        )
                        1 -> UpdatesScreen(vm, sites, updatesFilter, onFilter = { updatesFilter = it }, onOpenSite = { route = it })
                        2 -> HistoryScreen(vm, history)
                        else -> SettingsScreen(
                            vm = vm,
                            settings = settings,
                            lock = lock,
                            sitesCount = sites.size,
                            biometricAvailable = biometricAvailable,
                            onChangePin = { route = "pin" },
                            onLockNow = onLockNow,
                            onAddSite = { route = "add" },
                        )
                    }
                }
                "add" -> AddSiteScreen(vm = vm, onBack = { route = "" }, onAdded = {
                    route = ""
                    tab = 0
                })
                "pin" -> PinScreen(
                    mode = PinMode.CHANGE,
                    lock = lock,
                    biometricAvailable = biometricAvailable,
                    onBiometric = onBiometric,
                    onDone = {
                        route = ""
                        vm.notify("Code PIN modifié.")
                    },
                    onCancel = { route = "" },
                )
                else -> {
                    val state = sites.firstOrNull { it.site.id == r }
                    if (state == null) {
                        LaunchedEffect(Unit) { route = "" }
                    } else {
                        SiteDetailScreen(
                            vm = vm,
                            state = state,
                            onBack = { route = "" },
                            onRemoved = {
                                vm.removeSite(state.site.id)
                                route = ""
                            },
                        )
                    }
                }
            }
        }

        // Barre de navigation flottante (visible uniquement sur les onglets)
        AnimatedVisibility(
            visible = route.isEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomBar(selected = tab, updates = totalUpdates, onSelect = { tab = it }, onAdd = { route = "add" })
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (route.isEmpty()) 96.dp else 8.dp),
        )
    }
}

/** Barre de navigation blanche : 4 onglets + bouton central « + » (ajouter un site). */
@Composable
private fun BottomBar(selected: Int, updates: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .navigationBarsPadding()
                .height(78.dp)
                .padding(horizontal = 8.dp),
        ) {
            NavButton(navItems[0], selected == 0, 0, { onSelect(0) }, Modifier.weight(1f))
            NavButton(navItems[1], selected == 1, updates, { onSelect(1) }, Modifier.weight(1f))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(
                    onClick = onAdd,
                    shape = CircleShape,
                    color = WpColors.Blue,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(58.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, "Ajouter un site", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
            }
            NavButton(navItems[2], selected == 2, 0, { onSelect(2) }, Modifier.weight(1f))
            NavButton(navItems[3], selected == 3, 0, { onSelect(3) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavButton(item: NavItem, selected: Boolean, badge: Int, onClick: () -> Unit, modifier: Modifier) {
    val color by animateColorAsState(
        if (selected) WpColors.Blue else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "navColor",
    )
    val dot by animateDpAsState(
        if (selected) 5.dp else 0.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "dot",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            )
            .padding(vertical = 6.dp),
    ) {
        Box {
            Icon(if (selected) item.iconSelected else item.icon, item.label, tint = color, modifier = Modifier.size(26.dp))
            if (badge > 0) {
                // Pastille rouge, comme le compteur de mises à jour de l'admin WordPress
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 9.dp, y = (-5).dp)
                        .size(if (badge > 9) 20.dp else 17.dp)
                        .clip(CircleShape)
                        .background(WpColors.Red),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (badge > 99) "99+" else "$badge", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            item.label,
            color = color,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(dot).clip(CircleShape).background(WpColors.Blue))
    }
}
