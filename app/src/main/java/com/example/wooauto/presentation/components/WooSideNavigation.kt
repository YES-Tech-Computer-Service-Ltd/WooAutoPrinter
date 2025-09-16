package com.example.wooauto.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.collectAsState
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
import com.example.wooauto.presentation.theme.WooAutoTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.example.wooauto.presentation.navigation.Badge

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
            val selected = currentRoute == item.route ||
                (currentRoute?.startsWith("settings/") == true && item.route == com.example.wooauto.navigation.NavigationItem.Settings.route) ||
                (currentRoute?.startsWith("orders/") == true && item.route == com.example.wooauto.navigation.NavigationItem.Orders.route)
            NavigationDrawerItem(
                label = { 
                    val apiConfigured by com.example.wooauto.data.local.WooCommerceConfig.isConfigured.collectAsState(initial = true)
                    val externalBadge = item.badgeFlow?.collectAsState(initial = Badge.None)?.value ?: Badge.None
                    val showBadgeDot = (!apiConfigured && item.route == NavigationItem.Settings.route) ||
                        (externalBadge is Badge.Dot) ||
                        (externalBadge is Badge.Count && externalBadge.value > 0)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(id = item.titleResId),
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = if (selected) MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    else MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (showBadgeDot) {
                            Spacer(modifier = Modifier.width(6.dp))
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(color = MaterialTheme.colorScheme.error, shape = CircleShape)
                            )
                        }
                    }
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
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )

            // 如果当前主项被选中，展开其二级菜单（支持 Settings 与 Orders）
            if (selected) {
                val subs = AppNavConfig.subEntriesForRoute(item.route)
                subs.sortedBy { it.order }.forEach { sub ->
                    if (!(sub.visible && (sub.visibleWhen?.invoke() ?: true))) return@forEach
                    // Settings 使用参数 section；Orders 使用 orders/{section}
                    val currentSection = when {
                        item.route == com.example.wooauto.navigation.NavigationItem.Settings.route -> {
                            navController.currentBackStackEntry?.arguments?.getString("section")
                                ?: if (currentRoute == com.example.wooauto.navigation.NavigationItem.Settings.route) "general" else null
                        }
                        item.route == com.example.wooauto.navigation.NavigationItem.Orders.route -> {
                            // 统一与 Settings 相同的取参方式，避免取到模式串 "{section}"
                            navController.currentBackStackEntry?.arguments?.getString("section")
                                ?: if (currentRoute == com.example.wooauto.navigation.NavigationItem.Orders.route) "active" else null
                        }
                        else -> null
                    }
                    val subSelected = currentSection == sub.route
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
                                    val route = when (item.route) {
                                        com.example.wooauto.navigation.NavigationItem.Settings.route -> SettingsSectionRoutes.routeFor(sub.route)
                                        com.example.wooauto.navigation.NavigationItem.Orders.route -> com.example.wooauto.presentation.navigation.Screen.OrdersSection.routeFor(sub.route)
                                        else -> null
                                    }
                                    if (route != null && currentRoute != route) {
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


