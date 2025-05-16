package com.example.wooauto.presentation

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.example.wooauto.licensing.TrialTokenManager
import com.example.wooauto.presentation.components.WooAppBar
import com.example.wooauto.presentation.components.WooBottomNavigation
import com.example.wooauto.navigation.NavigationItem
import com.example.wooauto.presentation.navigation.Screen
import com.example.wooauto.presentation.screens.orders.OrdersScreen
import com.example.wooauto.presentation.screens.products.ProductsScreen
import com.example.wooauto.presentation.screens.settings.*
import com.example.wooauto.presentation.screens.printTemplates.PrintTemplatesScreen
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

private const val TAG = "WooAutoApp"

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
        fun getContent() {
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

    // ===== 许可验证相关状态 =====
    // 异步加载试用状态
    val isTrialChecked = remember { mutableStateOf(false) }
    val isTrialValid = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val appId = "wooauto_app"
        Log.d("TrialDebug", "deviceId = $deviceId, appId = $appId")
        try {
            // 添加超时机制，确保不会无限卡住
            val result = withTimeoutOrNull(5000) { // 5 秒超时
                TrialTokenManager.isTrialValid(context, deviceId, appId)
            } ?: false
            isTrialValid.value = result
            Log.d("TrialDebug", "Trial check completed, isTrialValid = ${isTrialValid.value}")
            // 如果初始检查失败，尝试重试一次
            if (!result) {
                delay(1000)
                val retryResult = withTimeoutOrNull(5000) {
                    TrialTokenManager.isTrialValid(context, deviceId, appId)
                } ?: false
                isTrialValid.value = retryResult
                Log.d("TrialDebug", "Trial check retry, isTrialValid = ${isTrialValid.value}")
            }
        } catch (e: Exception) {
            Log.e("AppContent", "Error checking trial: $e")
            isTrialValid.value = false
        } finally {
            isTrialChecked.value = true
            Log.d("TrialDebug", "isTrialValid final value = ${isTrialValid.value}")
        }
    }

    // 异步加载授权状态
    val isLicensed = remember { mutableStateOf(false) }
    val isStateLoaded = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            isLicensed.value = LicenseDataStore.isLicensed(context).first()
            Log.d("AppContent", "Initial state - isLicensed = ${isLicensed.value}")
            if (isLicensed.value) {
                val endDateStr = LicenseDataStore.getLicenseEndDate(context).first()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val endDate = sdf.parse(endDateStr ?: "")
                val currentDate = Calendar.getInstance().time
                if (endDate != null && endDate.before(currentDate)) {
                    Log.d("AppContent", "Local license expired: endDate=$endDateStr")
                    LicenseDataStore.setLicensed(context, false)
                    isLicensed.value = false
                    Log.d("AppContent", "License expired - placeholder for future restrictions (e.g., disable new orders)")
                }
            }
            isStateLoaded.value = true
        } catch (e: Exception) {
            Log.e("AppContent", "Failed to load license state: ${e.message}", e)
            isStateLoaded.value = true
        }
    }

    DisposableEffect(Unit) {
        val job = coroutineScope.launch {
            try {
                LicenseDataStore.isLicensed(context).collect { licensed ->
                    isLicensed.value = licensed
                    Log.d("AppContent", "isLicensed updated to $licensed")
                }
            } catch (e: Exception) {
                Log.e("AppContent", "Error collecting isLicensed: ${e.message}", e)
            }
        }
        onDispose {
            job.cancel()
        }
    }

    // 最终访问许可
    val hasAccess = remember { derivedStateOf { isTrialValid.value || isLicensed.value } }
    Log.d("AppContent", "hasAccess = ${hasAccess.value}, isTrialValid = ${isTrialValid.value}, isLicensed = ${isLicensed.value}")

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
            currentRoute.startsWith("template_")

    Scaffold(
        // 仅在不是特殊屏幕时显示顶部导航栏
        topBar = {
            if (!isSpecialScreen) {
                WooAppBar(                    navController = navController,                     // 注意：这里我们不提供ViewModel，在各页面内部获取并处理搜索逻辑                )
            }
        },
        bottomBar = {
            // 确保底部导航栏能够正确响应导航变化
            // 仅在标准页面（非特殊设置页面）上显示底部导航栏
            if (!isSpecialScreen) {
                WooBottomNavigation(navController = navController)
            }
        }
    ) { paddingValues ->
        // 如果状态未加载完成，显示加载中，但添加超时逻辑
        if (!isTrialChecked.value || !isStateLoaded.value) {
            LaunchedEffect(Unit) {
                delay(10000) // 最多等待 10 秒
                if (!isTrialChecked.value) {
                    Log.w("AppContent", "Trial check timed out after 10 seconds, forcing completion")
                    isTrialChecked.value = true
                }
                if (!isStateLoaded.value) {
                    Log.w("AppContent", "State load timed out after 10 seconds, forcing completion")
                    isStateLoaded.value = true
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
            NavigationItem.Orders.route  // 硬编码回退路由
        }

        // 渲染 NavHost
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
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
                                isLicensed.value = true
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
                    },
                    onGoToAppClicked = {
                        Log.d("AppContent", "Go to App clicked, navigating to OrdersScreen")
                        navController.navigate(NavigationItem.Orders.route) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
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

            // 网站设置页面
            composable(Screen.WebsiteSettings.route) {
                Log.d(TAG, "导航到网站设置页面")
                WebsiteSettingsScreen(navController = navController)
            }

            // 打印机设置页面
            composable(Screen.PrinterSettings.route) {
                Log.d(TAG, "导航到打印机设置页面")
                PrinterSettingsScreen(navController = navController)
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

            // 自动化设置页面
            composable(Screen.AutomationSettings.route) {
                Log.d(TAG, "导航到自动化设置页面")
                AutomationSettingsScreen(navController = navController)
            }
        }

        // 标记 NavHost 已初始化
        LaunchedEffect(Unit) {
            isNavHostInitialized.value = true
            Log.d("AppContent", "NavHost initialized")
        }
    }

    // 仅在 NavHost 初始化后执行初始导航
    LaunchedEffect(isTrialChecked.value, isStateLoaded.value, isNavHostInitialized.value) {
        if (isTrialChecked.value && isStateLoaded.value && isNavHostInitialized.value && !isInitialNavigationHandled.value) {
            delay(1000)
            Log.d("AppContent", "Performing initial navigation check: hasAccess = ${hasAccess.value}, isTrialValid = ${isTrialValid.value}, isLicensed = ${isLicensed.value}")
            if (!hasAccess.value) {
                Log.d("AppContent", "Trial expired or no license, navigating to LicenseSettingsScreen")
                navController.navigate(Screen.LicenseSettings.route) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                Log.d("AppContent", "Trial or license valid, staying on OrdersScreen")
            }
            isInitialNavigationHandled.value = true
        }
    }

    // 仅在非试用模式且许可有效时执行服务器验证
    LaunchedEffect(isTrialChecked.value, isStateLoaded.value, isTrialValid.value, isLicensed.value, isNavHostInitialized.value) {
        if (isTrialChecked.value && isStateLoaded.value && isNavHostInitialized.value && !isTrialValid.value && isLicensed.value) {
            Log.d("AppContent", "Licensed and not in trial, performing server validation")
            try {
                LicenseVerificationManager.forceServerValidation(
                    context,
                    coroutineScope,
                    onInvalid = {
                        Log.d("AppContent", "forceServerValidation failed, marking as unlicensed")
                        coroutineScope.launch {
                            try {
                                LicenseDataStore.setLicensed(context, false)
                                isLicensed.value = false
                                Log.d("AppContent", "License validation failed - navigating to LicenseSettingsScreen")
                                navController.navigate(Screen.LicenseSettings.route) {
                                    popUpTo(0) { inclusive = true }
                                    launchSingleTop = true
                                }
                            } catch (e: Exception) {
                                Log.e("AppContent", "Failed to update state after forceServerValidation: ${e.message}", e)
                            }
                        }
                    },
                    onSuccess = {
                        Log.d("AppContent", "forceServerValidation succeeded")
                    }
                )

                LicenseVerificationManager.verifyLicenseOnStart(
                    context,
                    coroutineScope,
                    onInvalid = {
                        Log.d("MainActivity", "许可验证失败，导航到 LicenseSettingsScreen")
                        try {
                            Log.d("AppContent", "License verification failed - navigating to LicenseSettingsScreen")
                            navController.navigate(Screen.LicenseSettings.route) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        } catch (e: Exception) {
                            Log.e("AppContent", "导航失败: ${e.message}", e)
                        }
                    },
                    onSuccess = {
                        Log.d("AppContent", "verifyLicenseOnStart succeeded")
                    }
                )
            } catch (e: Exception) {
                Log.e("AppContent", "Error during license verification: ${e.message}", e)
            }
        }
    }
}

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