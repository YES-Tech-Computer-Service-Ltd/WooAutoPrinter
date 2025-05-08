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
import androidx.compose.runtime.remember
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
//    Log.d("WooBottomNavigation", "开始初始化底部导航栏")
    
    // 使用remember缓存导航项列表
    val navigationItems = remember {
        val items = NavigationItem.items
//        Log.d("WooBottomNavigation", "获取到的导航项: ${items.size}")
        if (items.isEmpty()) {
            Log.w("WooBottomNavigation", "警告: NavigationItem.items返回空列表")
        }
        items
    }
    
    // 检查列表是否为空，如果为空则显示空盒子
    if (navigationItems.isEmpty()) {
        Log.e("WooBottomNavigation", "警告: 导航项列表为空!")
        Box {}
        return
    }
    
    // 正常渲染导航栏
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        
        // 记录当前路由以便调试
        val currentRoute = currentDestination?.route
//        Log.d("WooBottomNavigation", "当前路由: $currentRoute")
        
        // 检查是否在特殊屏幕上（这些屏幕不应该被视为导航栏的一部分）
        val isOnSpecialScreen = currentRoute?.let { route ->
            route.startsWith("printer_") || route == "printer_settings" || route == "website_settings"
        } ?: false
        
//        Log.d("WooBottomNavigation", "是否在特殊屏幕上: $isOnSpecialScreen, 当前路由: $currentRoute")
        
        // 显示每个导航项目，使用明确的类型以避免Kotlin编译器混淆
        navigationItems.forEach { item: NavigationItem -> 
            // 安全地访问item的属性
            val itemRoute = item.route
            if (itemRoute.isNotEmpty()) {  // 确保route不为空
                val selected = currentDestination?.hierarchy?.any { 
                    it.route == itemRoute 
                } == true

//                Log.d("WooBottomNavigation", "导航项 $itemRoute 是否选中: $selected")
                
                // 使用key包装每个导航项
                androidx.compose.runtime.key(itemRoute) {
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
//                            Log.d("WooBottomNavigation", "点击了导航项: $itemRoute")
                            
                            // 确保导航到目标
                            if (currentRoute != itemRoute) {
//                                Log.d("WooBottomNavigation", "正在导航从 $currentRoute 到 $itemRoute")
                                
                                // 导航逻辑
                                navController.navigate(itemRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = false
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                                
                                // 延迟记录，检查导航是否成功
                                Handler(Looper.getMainLooper()).postDelayed({
                                    val newRoute = navController.currentBackStackEntry?.destination?.route
//                                    Log.d("WooBottomNavigation", "导航后的当前路由: $newRoute")
                                }, 100)
                            } else {
//                                Log.d("WooBottomNavigation", "已经在目标路由上，无需导航")
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.title
                            )
                        },
                        label = { 
                            // 直接使用item.title，不使用try-catch
                            Text(text = item.title)
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
}

@Preview
@Composable
fun WooBottomNavigationPreview() {
    WooAutoTheme {
        WooBottomNavigation(navController = rememberNavController())
    }
} 