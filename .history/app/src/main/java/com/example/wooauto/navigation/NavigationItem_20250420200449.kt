package com.example.wooauto.navigation

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
// 导入自动镜像版本的图标
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
// 导入产品相关图标
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.wooauto.R

private const val TAG = "NavigationItem"

sealed class NavigationItem(
    val route: String,
    val titleResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    // Composable内不支持try-catch，使用安全的方式获取标题
    val title: String
        @Composable
        get() {
            // 直接使用stringResource，如果出错应用会在别处捕获
            return stringResource(id = titleResId)
        }
        
    // 非Composable获取标题的方法，用于日志或调试
    fun getTitleOrRoute(): String {
        return try {
            // 在非Composable环境下使用静态名称
            when (this) {
                is Orders -> "订单"
                is Products -> "产品"
                is Settings -> "设置"
                else -> route
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取标题失败: ${e.message}")
            route
        }
    }
        
    object Orders : NavigationItem(
        route = "orders",
        titleResId = R.string.nav_orders,
        selectedIcon = Icons.AutoMirrored.Filled.List,
        unselectedIcon = Icons.AutoMirrored.Outlined.List
    )
    
    object Products : NavigationItem(
        route = "products",
        titleResId = R.string.nav_products,
        selectedIcon = Icons.Filled.Inventory,
        unselectedIcon = Icons.Outlined.Inventory
    )
    
    object Settings : NavigationItem(
        route = "settings",
        titleResId = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
    
    companion object {
        // 确保items列表初始化成功
        val items: List<NavigationItem> by lazy {
            try {
                val navigationItems = listOf(Orders, Products, Settings)
                Log.d(TAG, "导航项列表初始化成功: ${navigationItems.size}项")
                navigationItems
            } catch (e: Exception) {
                Log.e(TAG, "导航项列表初始化失败: ${e.message}", e)
                // 提供默认值以防初始化失败
                listOf(Settings)
            }
        }
        
        // 获取默认路由
        fun getDefaultRoute(): String {
            return try {
                Orders.route
            } catch (e: Exception) {
                Log.e(TAG, "获取默认路由失败: ${e.message}", e)
                "orders" // 硬编码默认路由作为后备
            }
        }
    }
} 