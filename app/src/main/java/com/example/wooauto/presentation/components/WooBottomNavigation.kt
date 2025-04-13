package com.example.wooauto.presentation.components

import android.os.Handler
import android.os.Looper
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
                    
                    // 强制导航到目标，采用更强制的方式确保导航可以执行
                    try {
                        // 尝试先清除可能的导航回退栈，更彻底地确保导航可以切换
                        if (currentRoute != item.route) {
                            Log.d("WooBottomNavigation", "正在强制导航从 $currentRoute 到 ${item.route}")
                            
                            // 清除回退栈，然后重新导航
                            navController.navigate(item.route) {
                                // 弹出到起始目的地，但不保存任何状态
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = false
                                    saveState = false
                                }
                                // 确保是单一顶部实例
                                launchSingleTop = true
                                // 不恢复状态，强制创建新实例
                                restoreState = false
                            }
                            
                            // 延迟记录，检查导航是否成功
                            Handler(Looper.getMainLooper()).postDelayed({
                                val newRoute = navController.currentBackStackEntry?.destination?.route
                                Log.d("WooBottomNavigation", "导航后的当前路由: $newRoute")
                            }, 100)
                        } else {
                            Log.d("WooBottomNavigation", "已经在目标路由上，无需导航")
                        }
                    } catch (e: Exception) {
                        Log.e("WooBottomNavigation", "导航时发生错误", e)
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