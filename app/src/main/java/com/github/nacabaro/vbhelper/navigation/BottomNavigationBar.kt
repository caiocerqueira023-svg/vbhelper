package com.github.nacabaro.vbhelper.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.ui.theme.VitalPurple

private val compactDestinations = listOf(
    NavigationItems.Storage,
    NavigationItems.Dex,
    NavigationItems.Home,
    NavigationItems.World,
)

private val overflowDestinations = listOf(
    NavigationItems.Items,
    NavigationItems.Battles,
    NavigationItems.Settings,
)

/** Compact phones keep four daily destinations visible; secondary tools live in More. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BottomNavigationBar(navController: NavController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    var showMore by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)))
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(SurfaceStroke.copy(alpha = 0.6f)))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            compactDestinations.forEach { item ->
                VitalNavItem(
                    icon = item.icon,
                    label = stringResource(item.label),
                    selected = currentRoute == item.route,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigatePrimary(item) }
                )
            }
            VitalNavItem(
                icon = R.drawable.baseline_settings_24,
                label = stringResource(R.string.nav_more),
                selected = overflowDestinations.any { currentRoute == it.route },
                modifier = Modifier.weight(1f),
                onClick = { showMore = true }
            )
        }
    }

    if (showMore) {
        ModalBottomSheet(onDismissRequest = { showMore = false }) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                Text(
                    text = stringResource(R.string.nav_more).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = VitalCyan,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                overflowDestinations.forEach { item ->
                    OverflowDestination(item = item) {
                        showMore = false
                        navController.navigatePrimary(item)
                    }
                }
            }
        }
    }
}

/** Expanded screens use a rail rather than stretching a phone navigation bar. */
@Composable
fun VitalNavigationRail(navController: NavController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    NavigationRail(
        modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        (compactDestinations + overflowDestinations).forEach { item ->
            val selected = currentRoute == item.route
            NavigationRailItem(
                selected = selected,
                onClick = { navController.navigatePrimary(item) },
                icon = { Icon(painterResource(item.icon), stringResource(item.label)) },
                label = { Text(stringResource(item.label), maxLines = 1) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = VitalCyan,
                    selectedTextColor = VitalCyan,
                    indicatorColor = VitalPurple.copy(alpha = 0.25f)
                )
            )
        }
    }
}

@Composable
private fun OverflowDestination(item: NavigationItems, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(item.icon), null, tint = VitalCyan, modifier = Modifier.size(24.dp))
        Text(stringResource(item.label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun RowScope.VitalNavItem(
    icon: Int,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) VitalPurple.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent,
        label = "navItemBackground"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) VitalCyan else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "navItemTint"
    )
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(backgroundColor)
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painterResource(icon), label, tint = iconTint, modifier = Modifier.size(24.dp))
        Text(label, color = iconTint, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun NavController.navigatePrimary(item: NavigationItems) {
    if (item == NavigationItems.Home) {
        navigate(item.route) {
            popUpTo(0) { inclusive = false }
            launchSingleTop = true
        }
    } else {
        navigate(item.route) {
            popUpTo(graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}
