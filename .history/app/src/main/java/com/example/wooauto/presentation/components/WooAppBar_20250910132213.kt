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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.presentation.screens.orders.OrdersViewModel
import com.example.wooauto.presentation.screens.orders.UnreadOrdersDialog

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
    when (currentRoute) {
        NavigationItem.Orders.route -> {
            val unreadOrdersText = stringResource(id = R.string.unread_orders)
            WooTopBar(
                title = stringResource(id = R.string.orders),
                showSearch = false,
                isRefreshing = false,
                onRefresh = { },
                showRefreshButton = false,
                locale = locale,
                showTitle = true,
                titleAlignment = Alignment.Start,
                trailingContent = {
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
            )
            if (showUnreadOrders) {
                UnreadOrdersDialog(onDismiss = { showUnreadOrders = false })
            }
        }
        NavigationItem.Products.route -> {
            WooTopBar(
                title = stringResource(id = R.string.products),
                showSearch = false,
                isRefreshing = false,
                onRefresh = { },
                showRefreshButton = false,
                locale = locale,
                showTitle = true,
                titleAlignment = Alignment.Start
            )
        }
        else -> {
            // 其他页面(如Settings)使用默认顶部栏 - 显示左侧标题
            val title = when (currentRoute) {
                NavigationItem.Settings.route -> stringResource(id = R.string.settings)
                else -> stringResource(id = R.string.app_name)
            }
            
            WooTopBar(
                title = title,
                showSearch = false,
                isRefreshing = false,
                onRefresh = { },
                showRefreshButton = false,
                locale = locale,
                showTitle = true,
                titleAlignment = Alignment.Start
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