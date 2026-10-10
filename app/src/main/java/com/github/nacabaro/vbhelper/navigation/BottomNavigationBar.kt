package com.github.nacabaro.vbhelper.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.ui.theme.VitalPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke

internal val primaryDestinations = listOf(NavigationItems.Home, NavigationItems.Storage, NavigationItems.Dex, NavigationItems.World)
private val overflowDestinations = listOf(NavigationItems.Digiline, NavigationItems.Quests, NavigationItems.Items,
    NavigationItems.Battles, NavigationItems.Settings)

private fun topLevelRoute(route: String?): String? = when {
    route == null -> null
    route.startsWith("Card/") || route.startsWith("CardAdventure/") -> NavigationItems.Dex.route
    route.startsWith("TechniqueLoadout/") || route == NavigationItems.Adventure.route -> NavigationItems.Storage.route
    route.startsWith("WorldChat/") || route.startsWith("WorldConversation/") || route == NavigationItems.WorldRecruits.route -> NavigationItems.World.route
    route.startsWith("WildContact/") || route.startsWith("FarmGroup/") -> NavigationItems.Digiline.route
    route.startsWith("ApplyItem/") -> NavigationItems.Items.route
    route in listOf(NavigationItems.Credits.route, NavigationItems.Lorebook.route, NavigationItems.Viewer.route) -> NavigationItems.Settings.route
    else -> route
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BottomNavigationBar(navController: NavController) {
    val entry = navController.currentBackStackEntryAsState().value
    val route = topLevelRoute(entry?.destination?.route)
    var showMore by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().navigationBarsPadding()
        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(SurfaceStroke.copy(alpha = .6f)))
        Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 6.dp, vertical = 8.dp)) {
        primaryDestinations.forEach { item ->
            CompactNavItem(
                selected = route == item.route,
                onClick = { navController.navigatePrimary(item) },
                icon = item.icon,
                label = stringResource(item.label),
                modifier = Modifier.weight(1f),
            )
        }
        CompactNavItem(
            selected = overflowDestinations.any { route == it.route }, onClick = { showMore = true },
            icon = R.drawable.baseline_settings_24,
            label = stringResource(R.string.nav_more), modifier = Modifier.weight(1f),
        )
        }
    }
    if (showMore) ModalBottomSheet(onDismissRequest = { showMore = false }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text(stringResource(R.string.nav_more), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })
            overflowDestinations.forEach { item ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) {
                    showMore = false
                    navController.navigatePrimary(item)
                }.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(item.icon), null, tint = VitalCyan, modifier = Modifier.size(24.dp))
                    Text(stringResource(item.label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun CompactNavItem(icon: Int, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val tint = if (selected) VitalCyan else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.padding(horizontal = 2.dp).clip(MaterialTheme.shapes.small)
        .background(if (selected) VitalPurple.copy(alpha = .22f) else androidx.compose.ui.graphics.Color.Transparent)
        .selectable(selected = selected, role = Role.Tab, onClick = onClick)
        .heightIn(min = 56.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall, maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun VitalNavigationRail(navController: NavController) {
    val route = topLevelRoute(navController.currentBackStackEntryAsState().value?.destination?.route)
    NavigationRail(modifier = Modifier.statusBarsPadding().navigationBarsPadding(), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (primaryDestinations + overflowDestinations).forEach { item ->
                NavigationRailItem(
                    selected = route == item.route, onClick = { navController.navigatePrimary(item) },
                    icon = { Icon(painterResource(item.icon), null) },
                    label = { Text(stringResource(item.label), style = MaterialTheme.typography.labelMedium, maxLines = 2) },
                    colors = NavigationRailItemDefaults.colors(selectedIconColor = VitalCyan, selectedTextColor = VitalCyan,
                        indicatorColor = VitalPurple.copy(alpha = .22f)),
                )
            }
        }
    }
}

internal fun NavController.navigatePrimary(item: NavigationItems) {
    navigate(item.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
