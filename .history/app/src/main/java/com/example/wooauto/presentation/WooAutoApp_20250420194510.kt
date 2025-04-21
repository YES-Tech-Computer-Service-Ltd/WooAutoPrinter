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
import com.example.wooauto.presentation.screens.printTemplates.PrintTemplatesScreen
import com.example.wooauto.presentation.screens.templatePreview.TemplatePreviewScreen
import com.example.wooauto.presentation.screens.settings.SoundSettingsScreen
import com.example.wooauto.presentation.screens.settings.AutomationSettingsScreen
import com.example.wooauto.presentation.theme.WooAutoTheme
import com.example.wooauto.utils.LocalAppLocale
import com.example.wooauto.utils.LocaleManager
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState

private const val TAG = "WooAutoApp"

class WooAutoApp {
    companion object {
        @Composable
        fun getTheme(content: @Composable () -> Unit) {
            WooAutoTheme {
                content()
            }
        }

        @Composable
        fun getContent() {
            // 获取当前语言状态并提供给整个应用
            val context = LocalContext.current
            
            // 使用rememberUpdatedState确保每次重组时获取最新的语言状态
            val currentLocale by rememberUpdatedState(LocaleManager.currentLocale)
            
            // 添加日志以追踪语言状态变化
            LaunchedEffect(currentLocale) {
                Log.d(TAG, "语言状态更新: ${currentLocale.language}, ${currentLocale.displayName}")
            }
            
            // 使用key包装整个UI树，确保语言变化时整个UI树重新构建
            key(currentLocale.language) {
                // 使用CompositionLocalProvider提供语言状态给所有子组件
                CompositionLocalProvider(LocalAppLocale provides currentLocale) {
                    AppContent()
                }
            }
        }
    }
}

@Composable
fun AppContent() {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    // 使用currentBackStackEntryAsState获取当前路由
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route ?: "未知路由"
    
    // 获取当前语言状态
    val locale = LocalAppLocale.current
    
    // 添加日志跟踪导航变化
    LaunchedEffect(currentRoute) {
        Log.d(TAG, "导航状态: 当前路由='$currentRoute', 当前语言=${locale.language}")
    }
    
    Scaffold(
        topBar = { WooAppBar(navController = navController) },
        bottomBar = { 
            // 确保底部导航栏能够正确响应导航变化
            WooBottomNavigation(navController = navController)
        }
    ) { paddingValues ->
        // 添加额外日志，监控导航控制器
        LaunchedEffect(navController) {
            navController.addOnDestinationChangedListener { _, destination, arguments ->
                Log.d(TAG, "导航目的地变更: ${destination.route}, 参数: $arguments")
                // 诊断当前导航堆栈 - 移除访问私有属性的代码
                try {
                    // 不再尝试访问私有的backQueue
                    Log.d(TAG, "当前导航路线: ${destination.route ?: "null"}")
                } catch (e: Exception) {
                    Log.e(TAG, "获取导航状态时出错", e)
                }
            }
        }
        
        // 确保在订单和设置间导航时能正确工作的关键：处理重组
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = currentBackStackEntry?.destination
        val currentDestinationRoute = currentDestination?.route
        
        // 记录每次重组中的当前路由
        Log.d(TAG, "重组: 当前目的地路由 = $currentDestinationRoute")
        
        NavHost(
            navController = navController,
            startDestination = NavigationItem.Orders.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(NavigationItem.Orders.route) {
                Log.d(TAG, "导航到订单页面")
                OrdersScreen(navController = navController)
            }
            
            composable(NavigationItem.Products.route) {
                Log.d(TAG, "导航到产品页面")
                ProductsScreen(navController = navController)
            }
            
            composable(NavigationItem.Settings.route) {
                Log.d(TAG, "导航到设置页面")
                SettingsScreen(navController = navController)
            }
            
            // 网站设置页面
            composable(Screen.WebsiteSettings.route) {
                Log.d(TAG, "导航到网站设置页面")
                WebsiteSettingsScreen()
            }
            
            // 打印机设置页面
            composable(Screen.PrinterSettings.route) {
                Log.d(TAG, "导航到打印机设置页面")
                PrinterSettingsScreen(navController = navController)
            }
            
            // 打印机详情页面
            composable(Screen.PrinterDetails.route) { backStackEntry ->
                Log.d(TAG, "导航到打印机详情页面")
                val printerId = backStackEntry.arguments?.getString("printerId") ?: "new"
                PrinterDetailsScreen(
                    navController = navController,
                    printerId = printerId
                )
            }
            
            // 打印模板页面
            composable(Screen.PrintTemplates.route) {
                Log.d(TAG, "导航到打印模板页面")
                PrintTemplatesScreen(navController = navController)
            }
            
            // 模板预览页面
            composable(
                Screen.TemplatePreview.route,
                arguments = listOf(navArgument("templateId") { type = NavType.StringType })
            ) { backStackEntry ->
                val templateId = backStackEntry.arguments?.getString("templateId") ?: "default"
                Log.d(TAG, "导航到模板预览页面: $templateId")
                TemplatePreviewScreen(
                    navController = navController,
                    templateId = templateId
                )
            }
            
            // 声音设置页面
            composable(Screen.SoundSettings.route) {
                Log.d(TAG, "导航到声音设置页面")
                SoundSettingsScreen(navController = navController)
            }
            
            // 自动化设置页面
            composable(Screen.AutomationSettings.route) {
                Log.d(TAG, "导航到自动化设置页面")
                AutomationSettingsScreen(navController = navController)
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