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
    onRefresh: (route: String) -> Unit = { _ -> },
    onOrderClick: ((Long) -> Unit)? = null  // 新增订单点击回调
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
            // 订单页面特有的顶部栏 - 只显示搜索框和未读订单按钮，不显示标题
            val searchOrdersPlaceholder = if (locale.language == "zh") "搜索订单..." else "Search orders..."
            val unreadOrdersText = if (locale.language == "zh") "未读订单" else "Unread Orders"
            
            WooTopBar(
                title = "", // 空标题，不显示
                showSearch = true, // 显示搜索框
                searchQuery = searchQuery,
                onSearchQueryChange = { query -> 
                    searchQuery = query 
                    onSearch(query, NavigationItem.Orders.route)
                },
                searchPlaceholder = searchOrdersPlaceholder,
                isRefreshing = false,
                onRefresh = { 
                    onRefresh(NavigationItem.Orders.route)
                },
                locale = locale,
                showTitle = false, // 禁用标题显示
                additionalActions = {
                    // 未读订单按钮
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
            
            // 显示未读订单对话框
            if (showUnreadOrders) {
                UnreadOrdersDialog(
                    onDismiss = { showUnreadOrders = false },
                    onOrderClick = { order ->
                        // 点击订单时，关闭对话框并显示订单详情
                        android.util.Log.d("WooAppBar", "UnreadOrdersDialog点击订单: ${order.id}")
                        showUnreadOrders = false
                        // 使用广播通知OrdersScreen显示订单详情
                        navController?.let { nc ->
                            val context = nc.context
                            val intent = android.content.Intent("com.example.wooauto.ACTION_OPEN_ORDER_DETAILS")
                            intent.putExtra("orderId", order.id)
                            android.util.Log.d("WooAppBar", "发送广播显示订单详情: ${order.id}")
                            context.sendBroadcast(intent)
                            android.util.Log.d("WooAppBar", "广播已发送")
                        } ?: run {
                            android.util.Log.e("WooAppBar", "navController为null，无法发送广播")
                        }
                    }
                )
            }
        }
        NavigationItem.Products.route -> {
            // 产品页面特有的顶部栏 - 只显示搜索框，不显示标题
            val searchProductsPlaceholder = if (locale.language == "zh") "搜索产品..." else "Search products..."
            
            WooTopBar(
                title = "", // 空标题，不显示
                showSearch = true,
                searchQuery = searchQuery,
                onSearchQueryChange = { query -> 
                    searchQuery = query
                    onSearch(query, NavigationItem.Products.route)
                },
                searchPlaceholder = searchProductsPlaceholder,
                isRefreshing = false,
                onRefresh = { 
                    onRefresh(NavigationItem.Products.route)
                },
                locale = locale,
                showTitle = false // 禁用标题显示
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
                showTitle = true, // 启用标题显示
                titleAlignment = Alignment.Start // 左对齐标题
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