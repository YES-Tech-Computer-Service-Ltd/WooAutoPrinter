package com.example.wooauto.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.wooauto.R
import com.example.wooauto.navigation.NavigationItem
import com.example.wooauto.presentation.theme.WooAutoTheme
import com.example.wooauto.utils.LocalAppLocale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.presentation.screens.orders.OrdersViewModel
import com.example.wooauto.presentation.screens.orders.UnreadOrdersDialog
import com.example.wooauto.presentation.navigation.AppNavConfig
import com.example.wooauto.presentation.navigation.SettingsSectionRoutes

/**
 * 全局顶部栏组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WooAppBar(
    navController: NavController? = null,
    onSearch: (query: String, route: String) -> Unit = { _, _ -> },
    onRefresh: (route: String) -> Unit = { _ -> }
) {
    // 获取当前语言环境
    val locale = LocalAppLocale.current
    
    // 根据当前路径获取标题和决定是否显示搜索框
    val navBackStackEntry by navController?.currentBackStackEntryAsState() ?: remember { mutableStateOf(null) }
    val currentRoute = navBackStackEntry?.destination?.route ?: ""
    
    // 搜索相关状态
    var searchQuery by remember { mutableStateOf("") }
    var showUnreadOrders by remember { mutableStateOf(false) }
    
    // 决定显示哪种顶部栏
    val spec = AppNavConfig.topBarSpecForRoute(currentRoute)
    // 使用路由参数（若存在）生成层级标题：settings/{section}/{sub} -> 一级 - 二级
    val sectionArg = navBackStackEntry?.arguments?.getString("section")
    val subArg = navBackStackEntry?.arguments?.getString("sub")
    val compositeTitle: String? = run {
        if (!sectionArg.isNullOrEmpty() && !subArg.isNullOrEmpty()) {
            val sectionRes = AppNavConfig
                .subEntriesForRoute(NavigationItem.Settings.route)
                .find { it.route == sectionArg }?.titleResId
            val subRes = when (subArg) {
                "language" -> R.string.language
                "display" -> R.string.display_settings
                "templates" -> R.string.printer_templates
                else -> null
            }
            if (sectionRes != null && subRes != null) stringResource(sectionRes) + " - " + stringResource(subRes) else null
        } else null
    }
    when (currentRoute) {
        NavigationItem.Orders.route -> {
            val unreadOrdersText = stringResource(id = R.string.unread_orders)
            WooTopBar(
                title = compositeTitle ?: stringResource(id = spec.titleResId),
                showSearch = false,
                isRefreshing = false,
                onRefresh = { },
                showRefreshButton = false,
                locale = locale,
                showTitle = true,
                titleAlignment = if (spec.alignStart) Alignment.Start else Alignment.CenterHorizontally,
                showStatusStrip = spec.showStatusStrip,
                leadingContent = null,
                trailingContent = if (spec.showUnreadButton) {
                    {
                        IconButton(
                            onClick = { showUnreadOrders = true },
                            modifier = Modifier
                                .size(44.dp)
                                .padding(end = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = unreadOrdersText,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                } else null
            )
            if (showUnreadOrders) {
                UnreadOrdersDialog(onDismiss = { showUnreadOrders = false })
            }
        }
        else -> {
            // 仅在非顶级页面显示返回按钮（当前顶级：orders/products/settings 以及 settings/{section}）
            val showBack = run {
                when {
                    currentRoute == NavigationItem.Orders.route -> false
                    currentRoute == NavigationItem.Products.route -> false
                    currentRoute == NavigationItem.Settings.route -> false
                    currentRoute.startsWith("settings/") -> {
                        val parts = currentRoute.removePrefix("settings/").split('/')
                        // settings/{section} -> 无返回；settings/{section}/{sub} -> 显示返回
                        parts.size >= 2
                    }
                    else -> true
                }
            }
            WooTopBar(
                title = compositeTitle ?: stringResource(id = spec.titleResId),
                showSearch = false,
                isRefreshing = false,
                onRefresh = { },
                showRefreshButton = false,
                locale = locale,
                showTitle = true,
                titleAlignment = if (spec.alignStart) Alignment.Start else Alignment.CenterHorizontally,
                showStatusStrip = spec.showStatusStrip,
                leadingContent = if (showBack) {
                    {
                        IconButton(onClick = { navController?.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.template_back),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                } else null
            )
        }
    }
}

@Preview
@Composable
fun WooAppBarPreview() {
    WooAutoTheme {
        WooAppBar()
    }
} 