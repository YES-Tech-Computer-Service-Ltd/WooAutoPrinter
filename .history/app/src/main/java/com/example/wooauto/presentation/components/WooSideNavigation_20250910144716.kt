package com.example.wooauto.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
 
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.navigation.NavigationItem
import com.example.wooauto.presentation.navigation.NavEntry
import com.example.wooauto.presentation.navigation.AppNavConfig
import com.example.wooauto.presentation.navigation.SettingsSectionRoutes
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import com.example.wooauto.presentation.theme.WooAutoTheme

@Composable
fun WooSideNavigation(
    navController: NavController,
    items: List<NavEntry>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val stableItems = remember(items) { items.filter { it.visible } }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding() + 8.dp)
            .fillMaxHeight()
            .fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        stableItems.forEach { item ->
            val visible = item.visible && (item.visibleWhen?.invoke() ?: true)
            if (!visible) return@forEach
            val selected = currentRoute == item.route || (currentRoute?.startsWith("settings/") == true && item.route == com.example.wooauto.navigation.NavigationItem.Settings.route)
            NavigationDrawerItem(
                label = { 
                    Text(
                        text = stringResource(id = item.titleResId),
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = false }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = stringResource(id = item.titleResId),
                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    unselectedContainerColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )

            // 如果是 Settings 且被选中，展开子菜单（无图标、缩进样式）
            if (item.route == com.example.wooauto.navigation.NavigationItem.Settings.route && selected) {
                val subs = AppNavConfig.subEntriesForRoute(item.route)
                subs.sortedBy { it.order }.forEach { sub ->
                    if (!(sub.visible && (sub.visibleWhen?.invoke() ?: true))) return@forEach
                    val subSelected = currentRoute == SettingsSectionRoutes.routeFor(sub.route)
                    Row(modifier = Modifier
                        .padding(start = 26.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
                        .fillMaxWidth()
                    ) {
                        // 自定义更紧凑的子项（无图标，单行，选中加粗和轻背景）
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (subSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    shape = MaterialTheme.shapes.small
                                )
                                .clickable {
                                    val route = SettingsSectionRoutes.routeFor(sub.route)
                                    if (currentRoute != route) {
                                        navController.navigate(route) { launchSingleTop = true }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(id = sub.titleResId),
                                color = if (subSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = if (subSelected) MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    else MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Preview
@Composable
private fun WooSideNavigationPreview() {
    WooAutoTheme {
        val items = com.example.wooauto.presentation.navigation.AppNavConfig.sideNavEntries()
        WooSideNavigation(navController = rememberNavController(), items = items)
    }
}


