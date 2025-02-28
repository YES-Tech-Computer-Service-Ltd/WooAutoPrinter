package com.example.wooauto.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.wooauto.presentation.components.WooAppBar
import com.example.wooauto.presentation.components.WooBottomNavigation
import com.example.wooauto.navigation.NavigationItem
import com.example.wooauto.presentation.navigation.Screen
import com.example.wooauto.presentation.screens.orders.OrdersScreen
import com.example.wooauto.presentation.screens.products.ProductsScreen
import com.example.wooauto.presentation.screens.settings.PrinterDetailsScreen
import com.example.wooauto.presentation.screens.settings.PrinterSettingsScreen
import com.example.wooauto.presentation.screens.settings.SettingsScreen
import com.example.wooauto.presentation.screens.settings.WebsiteSettingsScreen
import com.example.wooauto.presentation.theme.WooAutoTheme
import android.util.Log

class WooAutoApp {
    companion object {
        @Composable
        fun getTheme(content: @Composable () -> Unit) {
            WooAutoTheme {
                content()
            }
        }

        @Composable
        fun getContent() = AppContent()
    }
}

@Composable
fun AppContent() {
    val navController = rememberNavController()
    
    // 添加日志跟踪导航变化
    val currentRoute = navController.currentBackStackEntryFlow.toString()
    Log.d("导航状态", "当前路由: $currentRoute")
    
    Scaffold(
        topBar = { WooAppBar(navController = navController) },
        bottomBar = { 
            // 确保底部导航栏能够正确响应导航变化
            WooBottomNavigation(navController = navController) 
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NavigationItem.Orders.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(NavigationItem.Orders.route) {
                Log.d("导航", "正在导航到订单页面")
                OrdersScreen(navController = navController)
            }
            
            composable(NavigationItem.Products.route) {
                Log.d("导航", "正在导航到产品页面")
                ProductsScreen(navController = navController)
            }
            
            composable(NavigationItem.Settings.route) {
                Log.d("导航", "正在导航到设置页面")
                SettingsScreen(navController = navController)
            }
            
            // 网站设置页面
            composable(Screen.WebsiteSettings.route) {
                Log.d("导航", "正在导航到网站设置页面")
                WebsiteSettingsScreen()
            }
            
            // 打印机设置页面
            composable(Screen.PrinterSettings.route) {
                Log.d("导航", "正在导航到打印机设置页面")
                PrinterSettingsScreen(navController = navController)
            }
            
            // 打印机详情页面
            composable(Screen.PrinterDetails.route) { backStackEntry ->
                val printerId = backStackEntry.arguments?.getString("printerId")
                Log.d("Navigation", "Navigating to PrinterDetails: $printerId")
                PrinterDetailsScreen(
                    navController = navController,
                    printerId = printerId
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    WooAutoTheme {
        AppContent()
    }
} 