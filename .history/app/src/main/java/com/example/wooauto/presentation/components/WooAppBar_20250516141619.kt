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

/**
 * 全局顶部栏组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WooAppBar(
    navController: NavController? = null,
) {
    // 获取当前语言环境
    val locale = LocalAppLocale.current
    
    // 根据当前路径获取标题和决定是否显示搜索框
    val navBackStackEntry by navController?.currentBackStackEntryAsState() ?: remember { mutableStateOf(null) }
    val currentRoute = navBackStackEntry?.destination?.route
    
    // 搜索相关状态
    var searchQuery by remember { mutableStateOf("") }
    var showUnreadOrders by remember { mutableStateOf(false) }
    
    // 决定显示哪种顶部栏
    when (currentRoute) {
        NavigationItem.Orders.route -> {
            // 订单页面特有的顶部栏 - 带搜索功能和未读订单按钮
            val ordersTitle = stringResource(id = R.string.orders)
            val searchOrdersPlaceholder = if (locale.language == "zh") "搜索订单..." else "Search orders..."
            val unreadOrdersText = if (locale.language == "zh") "未读订单" else "Unread Orders"
            
            WooTopBar(
                title = ordersTitle,
                showSearch = true,
                searchQuery = searchQuery,
                onSearchQueryChange = { 
                    searchQuery = it 
                    // 发送搜索请求到订单页面
                    // 这里我们将搜索逻辑交给页面处理，这部分需要在页面实现
                },
                searchPlaceholder = searchOrdersPlaceholder,
                isRefreshing = false, // 这里从页面获取
                onRefresh = { 
                    // 发送刷新请求到订单页面
                    // 这里我们将刷新逻辑交给页面处理
                },
                locale = locale,
                additionalActions = {
                    // 未读订单按钮
                    IconButton(
                        onClick = { showUnreadOrders = !showUnreadOrders },
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
        }
        NavigationItem.Products.route -> {
            // 产品页面特有的顶部栏 - 带搜索功能
            val productsTitle = stringResource(id = R.string.products)
            val searchProductsPlaceholder = if (locale.language == "zh") "搜索产品..." else "Search products..."
            
            WooTopBar(
                title = productsTitle,
                showSearch = true,
                searchQuery = searchQuery,
                onSearchQueryChange = { query -> 
                    searchQuery = query
                    // 发送搜索请求到产品页面
                    // 这里我们将搜索逻辑交给页面处理
                },
                searchPlaceholder = searchProductsPlaceholder,
                isRefreshing = false, // 这里从页面获取
                onRefresh = { 
                    // 发送刷新请求到产品页面
                    // 这里我们将刷新逻辑交给页面处理
                },
                locale = locale
            )
        }
        else -> {
            // 其他页面使用默认顶部栏 - 仅显示标题
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
                locale = locale
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