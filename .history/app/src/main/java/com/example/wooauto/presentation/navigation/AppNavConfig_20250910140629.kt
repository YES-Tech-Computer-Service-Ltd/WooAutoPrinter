package com.example.wooauto.presentation.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.wooauto.navigation.NavigationItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Settings
import com.example.wooauto.R
import kotlinx.coroutines.flow.StateFlow

@Immutable
sealed class Badge {
    object None : Badge()
    object Dot : Badge()
    data class Count(val value: Int) : Badge()
}

@Immutable
data class NavEntry(
    val route: String,
    val titleResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val visible: Boolean = true,
    val visibleWhen: (() -> Boolean)? = null,
    val badgeFlow: StateFlow<Badge>? = null,
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
            selectedIcon = Icons.AutoMirrored.Filled.List,
            unselectedIcon = Icons.AutoMirrored.Outlined.List
        ),
        NavEntry(
            route = NavigationItem.Products.route,
            titleResId = R.string.products,
            selectedIcon = Icons.Filled.Inventory,
            unselectedIcon = Icons.Outlined.Inventory
        ),
        NavEntry(
            route = NavigationItem.Settings.route,
            titleResId = R.string.settings,
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings
        )
    )

    @Immutable
    data class SubEntry(
        val parentRoute: String,
        val route: String,
        val titleResId: Int,
        val selectedIcon: ImageVector? = null,
        val unselectedIcon: ImageVector? = null,
        val visible: Boolean = true,
        val visibleWhen: (() -> Boolean)? = null,
        val badgeFlow: StateFlow<Badge>? = null,
        val group: String? = null,
        val order: Int = 0,
    )

    @Immutable
    data class SecondarySidebarSpec(
        val enabled: Boolean = true,
        val fraction: Float = 0.14f,
        val minDp: Int = 120,
        val maxDp: Int = 240,
    )

    fun subEntriesForRoute(primaryRoute: String): List<SubEntry> {
        // 预设：当前不配置任何子菜单，保持现状
        return emptyList()
    }

    fun secondarySidebarSpecForRoute(primaryRoute: String): SecondarySidebarSpec = SecondarySidebarSpec()

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


