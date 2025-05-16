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
import com.example.wooauto.presentation.screens.products.ProductsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WooAppBar(
    navController: NavController? = null,
    ordersViewModel: OrdersViewModel? = null,
    productsViewModel: ProductsViewModel? = null
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
            val isRefreshing = ordersViewModel?.refreshing?.value ?: false
            
            WooTopBar(
                title = ordersTitle,
                showSearch = true,
                searchQuery = searchQuery,
                onSearchQueryChange = { 
                    searchQuery = it 
                    // 如果有订单视图模型，更新其搜索状态
                    // 这里可以添加订单搜索的实现
                },
                searchPlaceholder = searchOrdersPlaceholder,
                isRefreshing = isRefreshing,
                onRefresh = { ordersViewModel?.refreshOrders() },
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
            val isRefreshing = productsViewModel?.isRefreshing?.value ?: false
            
            WooTopBar(
                title = productsTitle,
                showSearch = true,
                searchQuery = searchQuery,
                onSearchQueryChange = { query -> 
                    searchQuery = query
                    // 如果有产品视图模型，更新其搜索状态
                    productsViewModel?.let { viewModel ->
                        if (query.isEmpty()) {
                            viewModel.filterProductsByCategory(viewModel.currentSelectedCategoryId.value)
                        } else {
                            viewModel.searchProducts(query)
                        }
                    }
                },
                searchPlaceholder = searchProductsPlaceholder,
                isRefreshing = isRefreshing,
                onRefresh = { productsViewModel?.refreshData() },
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