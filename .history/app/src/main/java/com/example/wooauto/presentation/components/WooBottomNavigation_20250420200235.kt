package com.example.wooauto.presentation.components

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.Box
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
    
    // 使用remember和try-catch安全地获取导航项
    val items = remember {
        try {
            val navigationItems = NavigationItem.items
            Log.d("WooBottomNavigation", "获取到的导航项: ${navigationItems.size}")
            if (navigationItems.isEmpty()) {
                Log.w("WooBottomNavigation", "警告: NavigationItem.items返回空列表")
            }
            navigationItems
        } catch (e: Exception) {
            Log.e("WooBottomNavigation", "获取导航项时出错: ${e.message}", e)
            emptyList()
        }
    }
    
    if (items.isEmpty()) {
        Log.e("WooBottomNavigation", "警告: 导航项列表为空!")
        // 返回一个空的Box而不是直接返回，以防止布局崩溃
        Box {}
        return
    }
    
    try {
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
            
            // 添加null检查，确保每个item不为null
            items.filterNotNull().forEach { item ->
                // 安全地访问item的属性，添加额外的null检查以提高稳定性
                val itemRoute = item.route
                if (itemRoute.isNotEmpty()) {  // 确保route不为空
                    val selected = currentDestination?.hierarchy?.any { 
                        it.route == itemRoute 
                    } == true
                    
                    Log.d("WooBottomNavigation", "导航项 $itemRoute 是否选中: $selected")
                    
                    // 带有key的包装，防止不必要的重组
                    androidx.compose.runtime.key(itemRoute) {
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                Log.d("WooBottomNavigation", "点击了导航项: $itemRoute")
                                
                                // 强制导航到目标，采用更强制的方式确保导航可以执行
                                try {
                                    // 尝试先清除可能的导航回退栈，更彻底地确保导航可以切换
                                    if (currentRoute != itemRoute) {
                                        Log.d("WooBottomNavigation", "正在强制导航从 $currentRoute 到 $itemRoute")
                                        
                                        // 清除回退栈，然后重新导航
                                        navController.navigate(itemRoute) {
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
                            label = { 
                                // 使用安全的方式获取标题
                                Text(text = try { item.title } catch(e: Exception) { itemRoute })
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                } else {
                    Log.e("WooBottomNavigation", "警告: 跳过了一个具有空路由的导航项")
                }
            }
        }
    } catch (e: Exception) {
        // 捕获并记录任何在渲染过程中发生的错误
        Log.e("WooBottomNavigation", "渲染底部导航栏时发生错误: ${e.message}", e)
        // 返回一个空的Box以避免UI完全崩溃
        Box {}
    }
}

@Preview
@Composable
fun WooBottomNavigationPreview() {
    WooAutoTheme {
        WooBottomNavigation(navController = rememberNavController())
    }
} 