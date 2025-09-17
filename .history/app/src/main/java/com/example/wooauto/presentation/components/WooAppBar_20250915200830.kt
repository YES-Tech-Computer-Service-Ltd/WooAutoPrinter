package com.example.wooauto.presentation.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.example.wooauto.presentation.screens.orders.UnreadOrdersDialog
import com.example.wooauto.presentation.navigation.AppNavConfig
import com.example.wooauto.presentation.navigation.resolveTopBarTitle

/**
 * 全局顶部栏组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WooAppBar(
    navController: NavController? = null
) {
    // 获取当前语言环境
    val locale = LocalAppLocale.current
    
    // 根据当前路径获取标题和决定是否显示搜索框
    val navBackStackEntry by navController?.currentBackStackEntryAsState() ?: remember { mutableStateOf(null) }
    val currentRoute = navBackStackEntry?.destination?.route ?: ""
    
    // 搜索相关状态
    var showUnreadOrders by remember { mutableStateOf(false) }
    
    // 决定显示哪种顶部栏
    val spec = AppNavConfig.topBarSpecForRoute(currentRoute)
    // 交由统一解析器生成动态标题（settings 复合 & 模板名），回退到静态标题
    val resolvedTitle: String? = resolveTopBarTitle(currentRoute, navBackStackEntry?.arguments)
    when {
        currentRoute == NavigationItem.Orders.route || currentRoute.startsWith("orders/") -> {
            // Active/History 页面不再显示未读按钮（已由 Active 左栏表示）
            WooTopBar(
                title = resolvedTitle ?: stringResource(id = spec.titleResId),
                showSearch = false,
                isRefreshing = false,
                onRefresh = { },
                showRefreshButton = false,
                locale = locale,
                showTitle = true,
                titleAlignment = if (spec.alignStart) Alignment.Start else Alignment.CenterHorizontally,
                showStatusStrip = spec.showStatusStrip,
                leadingContent = null,
                trailingContent = null
            )
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
                title = resolvedTitle ?: stringResource(id = spec.titleResId),
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