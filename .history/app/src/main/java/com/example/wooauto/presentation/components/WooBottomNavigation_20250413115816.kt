package com.example.wooauto.presentation.components

import android.util.Log
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.navigation.NavigationItem
import com.example.wooauto.presentation.theme.WooAutoTheme

@Composable
fun WooBottomNavigation(navController: NavController) {
    // 添加详细日志
    Log.d("WooBottomNavigation", "开始初始化底部导航栏")
    
    val items = try {
        val navigationItems = NavigationItem.items
        Log.d("WooBottomNavigation", "获取到的导航项: ${navigationItems.size}")
        navigationItems
    } catch (e: Exception) {
        Log.e("WooBottomNavigation", "获取导航项时出错: ${e.message}", e)
        emptyList()
    }
    
    if (items.isEmpty()) {
        Log.e("WooBottomNavigation", "警告: 导航项列表为空!")
        return
    }
    
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        
        // 记录当前路由以便调试
        val currentRoute = currentDestination?.route
        Log.d("WooBottomNavigation", "当前路由: $currentRoute")
        
        // 检查是否在特殊屏幕上（这些屏幕不应该被视为导航栏的一部分）
        val isOnSpecialScreen = currentRoute?.let { route ->
            route.startsWith("printer_") || route == "printer_settings" || route == "website_settings"
        } ?: false
        
        Log.d("WooBottomNavigation", "是否在特殊屏幕上: $isOnSpecialScreen, 当前路由: $currentRoute")
        
        items.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { 
                it.route == item.route 
            } == true
            
            Log.d("WooBottomNavigation", "导航项 ${item.route} 是否选中: $selected")
            
            NavigationBarItem(
                selected = selected,
                onClick = {
                    Log.d("WooBottomNavigation", "点击了导航项: ${item.route}")
                    
                    // 如果在特殊屏幕上，先返回到主导航栏
                    if (isOnSpecialScreen) {
                        Log.d("WooBottomNavigation", "在特殊屏幕上，先返回到主导航")
                        navController.popBackStack()
                    }
                    
                    // 然后导航到目标
                    if (!selected || isOnSpecialScreen) {
                        navController.navigate(item.route) {
                            // 避免重复点击创建多个堆栈
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // 避免多次点击导致创建多个目标实例
                            launchSingleTop = true
                            // 切换时恢复状态
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.title
                    )
                },
                label = { Text(text = item.title) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                    indicatorColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}

@Preview
@Composable
fun WooBottomNavigationPreview() {
    WooAutoTheme {
        WooBottomNavigation(navController = rememberNavController())
    }
} 