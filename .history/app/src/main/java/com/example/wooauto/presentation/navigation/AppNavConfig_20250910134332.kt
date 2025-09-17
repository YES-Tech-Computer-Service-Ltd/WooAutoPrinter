package com.example.wooauto.presentation.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.wooauto.navigation.NavigationItem
import androidx.compose.material.icons.automirrored.filled.List as AutoListFilled
import androidx.compose.material.icons.automirrored.outlined.List as AutoListOutlined
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Inventory as OutlinedInventory
import androidx.compose.material.icons.outlined.Settings as OutlinedSettings
import com.example.wooauto.R

@Immutable
data class NavEntry(
    val route: String,
    val titleResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val visible: Boolean = true,
)

@Immutable
data class TopBarSpec(
    val titleResId: Int,
    val alignStart: Boolean = true,
    val showStatusStrip: Boolean = true,
    val showUnreadButton: Boolean = false
)

object AppNavConfig {
    // 侧栏与路由共用同一配置（最小改动：沿用现有 Route）
    fun sideNavEntries(): List<NavEntry> = listOf(
        NavEntry(
            route = NavigationItem.Orders.route,
            titleResId = R.string.orders,
            selectedIcon = AutoListFilled,
            unselectedIcon = AutoListOutlined
        ),
        NavEntry(
            route = NavigationItem.Products.route,
            titleResId = R.string.products,
            selectedIcon = Inventory,
            unselectedIcon = OutlinedInventory
        ),
        NavEntry(
            route = NavigationItem.Settings.route,
            titleResId = R.string.settings,
            selectedIcon = Settings,
            unselectedIcon = OutlinedSettings
        )
    )

    fun topBarSpecForRoute(route: String): TopBarSpec = when (route) {
        NavigationItem.Orders.route -> TopBarSpec(
            titleResId = R.string.orders,
            alignStart = true,
            showUnreadButton = true
        )
        NavigationItem.Products.route -> TopBarSpec(
            titleResId = R.string.products,
            alignStart = true
        )
        NavigationItem.Settings.route -> TopBarSpec(
            titleResId = R.string.settings,
            alignStart = true
        )
        else -> TopBarSpec(
            titleResId = R.string.app_name,
            alignStart = true
        )
    }
}


