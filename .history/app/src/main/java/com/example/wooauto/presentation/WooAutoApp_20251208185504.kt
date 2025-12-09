package com.example.wooauto.presentation

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import android.util.Log
import com.example.wooauto.utils.UiLog
import androidx.annotation.RequiresApi
import com.example.wooauto.domain.models.Order
import com.example.wooauto.licensing.LicenseDataStore
import com.example.wooauto.presentation.components.WooAppBar
import com.example.wooauto.presentation.components.WooSideNavigation
import com.example.wooauto.presentation.components.SideNavMode
import com.example.wooauto.presentation.navigation.AppNavConfig
import com.example.wooauto.navigation.NavigationItem
import com.example.wooauto.presentation.navigation.Screen
import com.example.wooauto.presentation.screens.orders.OrdersScreen
import com.example.wooauto.presentation.screens.products.ProductsScreen
import com.example.wooauto.presentation.screens.settings.*
import com.example.wooauto.presentation.screens.templatePreview.TemplatePreviewScreen
import com.example.wooauto.presentation.screens.settings.LicenseSettingsScreen
import com.example.wooauto.presentation.theme.WooAutoTheme
import com.example.wooauto.utils.LocalAppLocale
import com.example.wooauto.utils.LocaleManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.dp
import com.example.wooauto.presentation.screens.settings.PrinterSettings.PrinterDetailsScreen
import com.example.wooauto.presentation.screens.settings.PrinterSettings.PrinterSettingsScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background

private const val TAG = "WooAutoApp"

// 定义搜索事件数据类
data class SearchEvent(val query: String, val screenRoute: String)

// 刷新事件数据类
data class RefreshEvent(val screenRoute: String)

// 订单详情事件
data class OpenOrderDetailEvent(val order: Order)

// 应用级事件总线
object EventBus {
    private val _searchEvents = MutableSharedFlow<SearchEvent>()
    val searchEvents = _searchEvents.asSharedFlow()
    
    private val _refreshEvents = MutableSharedFlow<RefreshEvent>()
    val refreshEvents = _refreshEvents.asSharedFlow()
    
    private val _openOrderDetailEvents = MutableSharedFlow<OpenOrderDetailEvent>()
    val openOrderDetailEvents = _openOrderDetailEvents.asSharedFlow()
    
    suspend fun emitSearchEvent(query: String, screenRoute: String) {
        _searchEvents.emit(SearchEvent(query, screenRoute))
    }
    
    suspend fun emitRefreshEvent(screenRoute: String) {
        _refreshEvents.emit(RefreshEvent(screenRoute))
    }
    
    suspend fun emitOpenOrderDetail(order: Order) {
        _openOrderDetailEvents.emit(OpenOrderDetailEvent(order))
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
                UiLog.d(TAG, "语言状态更新: ${currentLocale.language}, ${currentLocale.displayName}")
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

import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.presentation.screens.orders.OrdersViewModel
import com.example.wooauto.presentation.screens.orders.OrderDetailDialog
import com.example.wooauto.presentation.screens.orders.DetailMode

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun AppContent() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 获取 ViewModel 以处理订单操作 (Activity/NavHost Scoped)
    val ordersViewModel: OrdersViewModel = hiltViewModel()
    
    // 订单详情状态
    val showOrderDetail = remember { mutableStateOf(false) }
    val detailOrder = remember { mutableStateOf<Order?>(null) }
    
    // 监听打开详情事件
    LaunchedEffect(Unit) {
        EventBus.openOrderDetailEvents.collect { event ->
            detailOrder.value = event.order
            showOrderDetail.value = true
        }
    }

    // Apply system bars color to align with app primary for a cohesive look
    val primaryColor = MaterialTheme.colorScheme.primary
    val isDark = isSystemInDarkTheme()
    DisposableEffect(primaryColor, isDark) {
        try {
            val window = (context as android.app.Activity).window
            window.statusBarColor = android.graphics.Color.parseColor("#00000000")
        } catch (_: Throwable) {}
        onDispose { }
    }

    // 使用currentBackStackEntryAsState获取当前路由
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route ?: "未知路由"

    // 获取当前语言状态
    val locale = LocalAppLocale.current

    // 添加日志跟踪导航变化
    LaunchedEffect(currentRoute) {
        UiLog.d(TAG, "导航状态: 当前路由='$currentRoute', 当前语言=${locale.language}")
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
                UiLog.d("AppContent", "统一资格检查完成: hasEligibility = ${hasEligibility.value}")
            }
            
            // 添加超时保护
            delay(10000) // 最多等待10秒
            if (!isEligibilityChecked.value) {
                UiLog.w("AppContent", "资格检查超时，强制完成")
                isEligibilityChecked.value = true
            }
        } catch (e: Exception) {
            UiLog.e("AppContent", "资格检查异常: ${e.message}", e)
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
            currentRoute == Screen.LanguageSettings.route

    // 获取系统状态栏高度
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Row(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalWidth = this.maxWidth
            // 基于宽度的三态模式：Expanded / Rail / MiniRail
            val sideMode = when {
                totalWidth >= 840.dp -> SideNavMode.Expanded
                totalWidth >= 600.dp -> SideNavMode.Rail
                else -> SideNavMode.MiniRail
            }
            val leftWidth = if (!isSpecialScreen) when (sideMode) {
                SideNavMode.Expanded -> 240.dp
                SideNavMode.Rail -> 80.dp
                SideNavMode.MiniRail -> 56.dp
            } else 0.dp
            val dividerWidth = if (!isSpecialScreen) 1.dp else 0.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (!isSpecialScreen) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(leftWidth)
                    ) {
                        val sideItems = remember { AppNavConfig.sideNavEntries() }
                        WooSideNavigation(
                            navController = navController,
                            items = sideItems,
                            contentPadding = WindowInsets.statusBars.asPaddingValues(),
                            mode = sideMode
                        )
                    }
                    // 垂直分隔线（侧栏与内容区之间）
                    Box(
                        modifier = Modifier
                            .width(dividerWidth)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(totalWidth - leftWidth - dividerWidth)
                ) {
                    if (!isSpecialScreen) {
                        WooAppBar(
                            navController = navController
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                // 如果状态未加载完成，显示加载中，但添加超时逻辑
                if (!isEligibilityChecked.value) {
                    LaunchedEffect(Unit) {
                        delay(10000)
                        if (!isEligibilityChecked.value) {
                            UiLog.w("AppContent", "资格检查超时，强制完成")
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
                    UiLog.e(TAG, "获取默认路由失败，使用硬编码路由: ${e.message}", e)
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
                                        UiLog.d("AppContent", "License activated, updating states")
                                        LicenseDataStore.setLicensed(context, true)
                                        isEligibilityChecked.value = true
                                        hasEligibility.value = true
                                        val savedLicensed = LicenseDataStore.isLicensed(context).first()
                                        UiLog.d("AppContent", "Post-activation state - isLicensed: $savedLicensed")
                                        if (savedLicensed) {
                                            UiLog.d("AppContent", "States updated successfully, navigating to OrdersScreen")
                                            navController.navigate(NavigationItem.Orders.route) {
                                                popUpTo(0) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        } else {
                                            UiLog.e("AppContent", "State update failed - isLicensed: $savedLicensed")
                                        }
                                    } catch (e: Exception) {
                                        UiLog.e("AppContent", "Failed to update states after activation: ${e.message}", e)
                                    }
                                }
                            }
                        )
                    }

                    composable(NavigationItem.Orders.route) {
                        UiLog.d(TAG, "导航到订单页面")
                        // 默认跳转到 orders/active 二级页面
                        navController.navigate(com.example.wooauto.presentation.navigation.Screen.OrdersSection.routeFor("active")) {
                            launchSingleTop = true
                        }
                    }

                    // Orders 子路由：orders/{section}
                    composable(
                        route = com.example.wooauto.presentation.navigation.Screen.OrdersSection.route,
                        arguments = listOf(navArgument("section") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val section = backStackEntry.arguments?.getString("section") ?: "active"
                        UiLog.d(TAG, "导航到订单子页面: $section")
                        when (section) {
                            "active" -> {
                                com.example.wooauto.presentation.screens.orders.OrdersActivePlaceholderScreen()
                            }
                            "history" -> {
                                OrdersScreen(navController = navController)
                            }
                            else -> {
                                OrdersScreen(navController = navController)
                            }
                        }
                    }

                    composable(NavigationItem.Products.route) {
                        UiLog.d(TAG, "导航到产品页面")
                        ProductsScreen(navController = navController)
                    }

                    composable(NavigationItem.Settings.route) {
                        UiLog.d(TAG, "导航到设置页面")
                        SettingsScreen(navController = navController)
                    }
                    // Settings 子路由：settings/{section}
                    composable(route = com.example.wooauto.presentation.navigation.SettingsSectionRoutes.pattern,
                        arguments = listOf(navArgument("section") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val section = backStackEntry.arguments?.getString("section") ?: "general"
                        UiLog.d(TAG, "导航到设置子页面: $section")
                        SettingsScreen(navController = navController)
                    }

                    // Settings 二级子路由：settings/{section}/{sub}
                    composable(
                        route = com.example.wooauto.presentation.navigation.SettingsSubPageRoutes.pattern,
                        arguments = listOf(
                            navArgument("section") { type = NavType.StringType },
                            navArgument("sub") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val section = backStackEntry.arguments?.getString("section") ?: "general"
                        val sub = backStackEntry.arguments?.getString("sub") ?: ""
                        UiLog.d(TAG, "导航到设置二级页面: $section/$sub")
                        when("$section/$sub") {
                            "general/language" -> {
                                LanguageSettingsScreen()
                            }
                            "general/display" -> {
                                com.example.wooauto.presentation.screens.settings.DisplaySettingsScreen()
                            }
                            "general/store" -> {
                                com.example.wooauto.presentation.screens.settings.StoreSettingsScreen()
                            }
                            "notification/sound" -> {
                                // 通知设置作为二级页
                                SoundSettingsScreen(navController = navController)
                            }
                            "printing/templates" -> {
                                // 打印模板列表与预览，作为设置二级页
                                PrintTemplatesInnerScreen(navController = navController)
                            }
                            else -> {
                                // 默认回退到该 section 的主屏
                                SettingsScreen(navController = navController)
                            }
                        }
                    }

                    // 打印机设置页面
                    composable(Screen.PrinterSettings.route) {
                        UiLog.d(TAG, "导航到打印机设置页面")
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
                        UiLog.d(TAG, "导航到打印机详情页面")
                        PrinterDetailsScreen(
                            navController = navController,
                            printerId = printerId
                        )
                    }

                    // 打印模板页面（旧：已由 settings/printing/templates 二级路由替代）

                    // 模板预览页面
                    composable(
                        route = Screen.TemplatePreview.route,
                        arguments = listOf(navArgument("templateId") { type = NavType.StringType })
                    ) {
                        val templateId = it.arguments?.getString("templateId") ?: "default"
                        UiLog.d(TAG, "导航到模板预览页面: $templateId")
                        TemplatePreviewScreen(
                            navController = navController,
                            templateId = templateId
                        )
                    }

                    // 声音设置页面
                    composable(Screen.SoundSettings.route) {
                        UiLog.d(TAG, "导航到声音设置页面")
                        SoundSettingsScreen(navController = navController)
                    }

                    // 语言设置页面
                    composable(Screen.LanguageSettings.route) {
                        UiLog.d(TAG, "导航到语言设置页面")
                        LanguageSettingsScreen()
                    }
                }

                        // 标记 NavHost 已初始化
                        LaunchedEffect(Unit) {
                            isNavHostInitialized.value = true
                            UiLog.d("AppContent", "NavHost initialized")
                        }
                    }
                }
            }
        }
    }

    // 仅在 NavHost 初始化后执行初始导航
    LaunchedEffect(isEligibilityChecked.value, isNavHostInitialized.value) {
        if (isEligibilityChecked.value && isNavHostInitialized.value && !isInitialNavigationHandled.value) {
            delay(1000)
            UiLog.d("AppContent", "资格状态已加载")
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