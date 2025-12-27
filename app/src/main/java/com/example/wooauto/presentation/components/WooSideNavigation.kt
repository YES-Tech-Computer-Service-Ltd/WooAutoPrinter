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
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.wooauto.presentation.navigation.Screen

enum class SideNavMode { Expanded, Rail, MiniRail }

@Composable
fun WooSideNavigation(
    navController: NavController,
    items: List<NavEntry>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    mode: SideNavMode = SideNavMode.Expanded
) {
    val stableItems = remember(items) { items.filter { it.visible } }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    if (mode == SideNavMode.Expanded) {
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
                val apiConfigured by com.example.wooauto.data.local.WooCommerceConfig.isConfigured.collectAsState(initial = true)
                val externalBadge = item.badgeFlow?.collectAsState(initial = Badge.None)?.value ?: Badge.None
                val showBadgeDot = (!apiConfigured && item.route == NavigationItem.Settings.route) ||
                    (externalBadge is Badge.Dot) ||
                    (externalBadge is Badge.Count && externalBadge.value > 0)

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clickable(
                            indication = null,
                            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()
                        ) {
                            if (!selected) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = false }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = stringResource(id = item.titleResId),
                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(10.dp))
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
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(color = MaterialTheme.colorScheme.error, shape = CircleShape)
                            )
                        }
                    }
                }

                // 展开二级菜单
                if (selected) {
                    val subs = AppNavConfig.subEntriesForRoute(item.route)
                    subs.sortedBy { it.order }.forEach subLoop@ { sub ->
                        if (!(sub.visible && (sub.visibleWhen?.invoke() ?: true))) return@subLoop
                        val currentSection = when {
                            item.route == com.example.wooauto.navigation.NavigationItem.Settings.route -> {
                                navController.currentBackStackEntry?.arguments?.getString("section")
                                    ?: if (currentRoute == com.example.wooauto.navigation.NavigationItem.Settings.route) "general" else null
                            }
                            item.route == com.example.wooauto.navigation.NavigationItem.Orders.route -> {
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
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (subSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent,
                                        shape = MaterialTheme.shapes.small
                                    )
                                    .clickable(
                                        indication = null,
                                        interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()
                                    ) {
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
    } else {
        // Rail / MiniRail：仅图标，紧凑纵向
        val itemPadding = if (mode == SideNavMode.Rail) 10.dp else 8.dp
        val iconSize = if (mode == SideNavMode.Rail) 24.dp else 22.dp
        Column(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(top = contentPadding.calculateTopPadding() + 8.dp)
                .fillMaxHeight()
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            stableItems.forEach { item ->
                val visible = item.visible && (item.visibleWhen?.invoke() ?: true)
                if (!visible) return@forEach
                val selected = currentRoute == item.route ||
                    (currentRoute?.startsWith("settings/") == true && item.route == com.example.wooauto.navigation.NavigationItem.Settings.route) ||
                    (currentRoute?.startsWith("orders/") == true && item.route == com.example.wooauto.navigation.NavigationItem.Orders.route)

                val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                val iconTint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                Box(
                    modifier = Modifier
                        .padding(vertical = itemPadding)
                        .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                        .background(bgColor, RoundedCornerShape(12.dp))
                        .clickable {
                            if (!selected) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = false }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            }
                        }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = stringResource(id = item.titleResId),
                        tint = iconTint,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * 二级侧栏（用于 Rail/MiniRail + Secondary Sidebar 方案）
 * - 根据当前选中的一级路由（Orders/Settings）展示对应的二级菜单
 * - 通过 navBackStackEntry.arguments 的 section 来决定当前高亮
 */
@Composable
fun WooSecondaryNavigation(
    navController: NavController,
    primaryRoute: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val subs = remember(primaryRoute) {
        AppNavConfig.subEntriesForRoute(primaryRoute).sortedBy { it.order }
    }

    if (subs.isEmpty()) return

    val currentSection: String? = when (primaryRoute) {
        NavigationItem.Settings.route -> {
            navBackStackEntry?.arguments?.getString("section")
                ?: if (currentRoute == NavigationItem.Settings.route) "general" else null
        }
        NavigationItem.Orders.route -> {
            navBackStackEntry?.arguments?.getString("section")
                ?: if (currentRoute == NavigationItem.Orders.route) "active" else null
        }
        else -> null
    }

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
        subs.forEach { sub ->
            if (!(sub.visible && (sub.visibleWhen?.invoke() ?: true))) return@forEach
            val subSelected = currentSection == sub.route

            val rowBg = if (subSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent
            val textColor = if (subSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

            Surface(
                shape = MaterialTheme.shapes.small,
                color = rowBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clickable(
                        indication = null,
                        interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()
                    ) {
                        val route = when (primaryRoute) {
                            NavigationItem.Settings.route -> SettingsSectionRoutes.routeFor(sub.route)
                            NavigationItem.Orders.route -> Screen.OrdersSection.routeFor(sub.route)
                            else -> null
                        }
                        if (route != null) {
                            val isAlreadySelected = currentSection == sub.route
                            if (!isAlreadySelected) {
                                navController.navigate(route) { launchSingleTop = true }
                            }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sub.selectedIcon != null && sub.unselectedIcon != null) {
                        Icon(
                            imageVector = if (subSelected) sub.selectedIcon else sub.unselectedIcon,
                            contentDescription = stringResource(id = sub.titleResId),
                            tint = textColor
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Text(
                        text = stringResource(id = sub.titleResId),
                        color = textColor,
                        style = if (subSelected) {
                            MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
        WooSideNavigation(navController = rememberNavController(), items = items, mode = SideNavMode.Expanded)
    }
}


