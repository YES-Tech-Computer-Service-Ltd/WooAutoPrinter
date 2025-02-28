package com.wooauto.presentation.components

import android.util.Log
import androidx.compose.foundation.layout.RowScope
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
import com.example.wooauto.presentation.navigation.NavigationItem
import com.wooauto.presentation.theme.WooAutoTheme

@Composable
fun WooBottomNavigation(navController: NavController) {
    // 添加日志
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
        
        items.forEach { item ->
            // 添加空值检查
            if (item == null) {
                Log.e("WooBottomNavigation", "警告: 导航项为null!")
                return@forEach
            }
            
            val selected = currentDestination?.hierarchy?.any { 
                it.route == item.route 
            } == true
            
            // 检查图标是否存在
            if (item.selectedIcon == null || item.unselectedIcon == null) {
                Log.e("WooBottomNavigation", "警告: 导航项图标为null! Route: ${item.route}")
                return@forEach
            }
            
            NavigationBarItem(
                selected = selected,
                onClick = {
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