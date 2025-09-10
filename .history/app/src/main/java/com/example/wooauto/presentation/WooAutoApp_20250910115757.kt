package com.example.wooauto.presentation

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.wooauto.licensing.LicenseDataStore
import com.example.wooauto.licensing.LicenseVerificationManager
import com.example.wooauto.licensing.LicenseManager
import com.example.wooauto.licensing.EligibilityStatus
import com.example.wooauto.licensing.TrialTokenManager
import com.example.wooauto.presentation.components.WooAppBar
import com.example.wooauto.presentation.components.WooSideNavigation
import com.example.wooauto.navigation.NavigationItem
import com.example.wooauto.presentation.navigation.Screen
import com.example.wooauto.presentation.screens.orders.OrdersScreen
import com.example.wooauto.presentation.screens.products.ProductsScreen
import com.example.wooauto.presentation.screens.settings.*
import com.example.wooauto.presentation.screens.settings.PrintTemplatesScreen
import com.example.wooauto.presentation.screens.templatePreview.TemplatePreviewScreen
import com.example.wooauto.presentation.screens.settings.LicenseSettingsScreen
import com.example.wooauto.presentation.theme.WooAutoTheme
import com.example.wooauto.utils.LocalAppLocale
import com.example.wooauto.utils.LocaleManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.wooauto.presentation.screens.settings.PrinterSettings.PrinterDetailsScreen
import com.example.wooauto.presentation.screens.settings.PrinterSettings.PrinterSettingsScreen

private const val TAG = "WooAutoApp"

// 定义搜索事件数据类
data class SearchEvent(val query: String, val screenRoute: String)

// 刷新事件数据类
data class RefreshEvent(val screenRoute: String)

// 应用级事件总线
object EventBus {
    private val _searchEvents = MutableSharedFlow<SearchEvent>()
    val searchEvents = _searchEvents.asSharedFlow()
    
    private val _refreshEvents = MutableSharedFlow<RefreshEvent>()
    val refreshEvents = _refreshEvents.asSharedFlow()
    
    suspend fun emitSearchEvent(query: String, screenRoute: String) {
        _searchEvents.emit(SearchEvent(query, screenRoute))
    }
    
    suspend fun emitRefreshEvent(screenRoute: String) {
        _refreshEvents.emit(RefreshEvent(screenRoute))
    }
}

class WooAutoApp {
    companion object {
        @Composable
        fun getTheme(content: @Composable () -> Unit) {
            WooAutoTheme {
                content()
            }
        }

        @RequiresApi(Build.VERSION_CODES.S)
        @Composable
        fun GetContent() {
            // 获取当前语言状态并提供给整个应用
            LocalContext.current

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

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun AppContent() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 使用currentBackStackEntryAsState获取当前路由
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route ?: "未知路由"

    // 获取当前语言状态
    val locale = LocalAppLocale.current

    // 添加日志跟踪导航变化
    LaunchedEffect(currentRoute) {
        Log.d(TAG, "导航状态: 当前路由='$currentRoute', 当前语言=${locale.language}")
    }

    // ===== 统一资格验证 =====
    // 获取LicenseManager实例
    val app = context.applicationContext as com.example.wooauto.WooAutoApplication
    val licenseManager = app.licenseManager
    
    // 使用统一的资格状态
    val isEligibilityChecked = remember { mutableStateOf(false) }
    val hasEligibility = remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        try {
            // 验证统一资格状态
            licenseManager.verifyLicense(context, coroutineScope) { isValid ->
                hasEligibility.value = isValid
                isEligibilityChecked.value = true
                Log.d("AppContent", "统一资格检查完成: hasEligibility = ${hasEligibility.value}")
            }
            
            // 添加超时保护
            delay(10000) // 最多等待10秒
            if (!isEligibilityChecked.value) {
                Log.w("AppContent", "资格检查超时，强制完成")
                isEligibilityChecked.value = true
            }
        } catch (e: Exception) {
            Log.e("AppContent", "资格检查异常: ${e.message}", e)
            hasEligibility.value = false
            isEligibilityChecked.value = true
        }
    }

    // 导航完成标志
    val isInitialNavigationHandled = remember { mutableStateOf(false) }
    // 标志 NavHost 是否已初始化
    val isNavHostInitialized = remember { mutableStateOf(false) }

    // 判断是否在特殊屏幕上
    val isSpecialScreen = currentRoute.startsWith("printer_") ||
            currentRoute == Screen.PrinterSettings.route ||
            currentRoute == Screen.WebsiteSettings.route ||
            currentRoute == Screen.SoundSettings.route ||
            currentRoute == Screen.AutomationSettings.route ||
            currentRoute == Screen.LicenseSettings.route ||
            currentRoute == Screen.LanguageSettings.route ||
            currentRoute.startsWith("template_")

    // 获取系统状态栏高度
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Row(modifier = Modifier.fillMaxSize()) {
        if (!isSpecialScreen) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 72.dp, max = 280.dp)
                    .weight(0.18f)
            ) {
                WooSideNavigation(
                    navController = navController,
                    contentPadding = WindowInsets.statusBars.asPaddingValues()
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        ) {
            if (!isSpecialScreen) {
                WooAppBar(
                    navController = navController,
                    onSearch = { query, route ->
                        coroutineScope.launch { EventBus.emitSearchEvent(query, route) }
                    },
                    onRefresh = { route ->
                        coroutineScope.launch { EventBus.emitRefreshEvent(route) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // 如果状态未加载完成，显示加载中，但添加超时逻辑
                if (!isEligibilityChecked.value) {
                    LaunchedEffect(Unit) {
                        delay(10000)
                        if (!isEligibilityChecked.value) {
                            Log.w("AppContent", "资格检查超时，强制完成")
                            isEligibilityChecked.value = true
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // 安全获取默认起始路由
                val startDestination = try {
                    NavigationItem.getDefaultRoute()
                } catch (e: Exception) {
                    Log.e(TAG, "获取默认路由失败，使用硬编码路由: ${e.message}", e)
                    NavigationItem.Orders.route
                }

                // 渲染 NavHost
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.padding(
                        top = if (isSpecialScreen) statusBarHeight else 0.dp,
                        start = 0.dp,
                        end = 0.dp,
                        bottom = 0.dp
                    )
                ) {
                    // 许可设置页面
                    composable(Screen.LicenseSettings.route) {
                        LicenseSettingsScreen(
                            navController = navController,
                            onLicenseActivated = {
                                coroutineScope.launch {
                                    try {
                                        Log.d("AppContent", "License activated, updating states")
                                        LicenseDataStore.setLicensed(context, true)
                                        isEligibilityChecked.value = true
                                        hasEligibility.value = true
                                        val savedLicensed = LicenseDataStore.isLicensed(context).first()
                                        Log.d("AppContent", "Post-activation state - isLicensed: $savedLicensed")
                                        if (savedLicensed) {
                                            Log.d("AppContent", "States updated successfully, navigating to OrdersScreen")
                                            navController.navigate(NavigationItem.Orders.route) {
                                                popUpTo(0) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        } else {
                                            Log.e("AppContent", "State update failed - isLicensed: $savedLicensed")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AppContent", "Failed to update states after activation: ${e.message}", e)
                                    }
                                }
                            }
                        )
                    }

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

                    // 打印机设置页面
                    composable(Screen.PrinterSettings.route) {
                        Log.d(TAG, "导航到打印机设置页面")
                        PrinterSettingsScreen(
                            navController = navController,
                            onClose = { navController.popBackStack() }
                        )
                    }

                    // 打印机详情页面
                    composable(
                        route = Screen.PrinterDetails.route,
                        arguments = listOf(navArgument("printerId") { type = NavType.StringType })
                    ) {
                        val printerId = it.arguments?.getString("printerId") ?: "new"
                        Log.d(TAG, "导航到打印机详情页面")
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
                        route = Screen.TemplatePreview.route,
                        arguments = listOf(navArgument("templateId") { type = NavType.StringType })
                    ) {
                        val templateId = it.arguments?.getString("templateId") ?: "default"
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

                    // 语言设置页面
                    composable(Screen.LanguageSettings.route) {
                        Log.d(TAG, "导航到语言设置页面")
                        LanguageSettingsScreen()
                    }
                }

                // 标记 NavHost 已初始化
                LaunchedEffect(Unit) {
                    isNavHostInitialized.value = true
                    Log.d("AppContent", "NavHost initialized")
                }
            }
        }
    }

    // 仅在 NavHost 初始化后执行初始导航
    LaunchedEffect(isEligibilityChecked.value, isNavHostInitialized.value) {
        if (isEligibilityChecked.value && isNavHostInitialized.value && !isInitialNavigationHandled.value) {
            delay(1000)
            Log.d("AppContent", "资格状态已加载")
            isInitialNavigationHandled.value = true
        }
    }
}

// 使用现有的PrinterSettingsScreen组件，移除未实现的组件


@RequiresApi(Build.VERSION_CODES.S)
@Preview(showBackground = true)
@Composable
fun AppPreview() {
    WooAutoTheme {
        CompositionLocalProvider(LocalAppLocale provides LocaleManager.currentLocale) {
            AppContent()
        }
    }
}