package com.wooauto.presentation.screens.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.wooauto.domain.models.Order
import com.wooauto.presentation.components.EmptyState
import com.wooauto.presentation.components.ErrorState
import com.wooauto.presentation.components.LoadingIndicator
import com.wooauto.presentation.components.StatusBadge
import com.wooauto.presentation.viewmodels.OrderViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * 订单列表页面
 *
 * @param onOrderClick 订单点击回调
 * @param viewModel 订单列表 ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    onOrderClick: (String) -> Unit,
    viewModel: OrderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val swipeRefreshState = rememberSwipeRefreshState(uiState.isLoading)

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部搜索栏
        TextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("搜索订单...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索"
                )
            },
            singleLine = true
        )

        // 过滤器区域
        FilterSection(
            selectedStatus = uiState.selectedStatus,
            selectedDeliveryMethod = uiState.selectedDeliveryMethod,
            selectedDate = uiState.selectedDate,
            onStatusSelected = { viewModel.setStatusFilter(it) },
            onDeliveryMethodSelected = { viewModel.setDeliveryMethodFilter(it) },
            onDateSelected = { viewModel.setDateFilter(it) }
        )

        // 主要内容区域
        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error!!,
                        onRetry = { viewModel.refreshOrders() }
                    )
                }
                uiState.isLoading && uiState.orders.isEmpty() -> {
                    LoadingIndicator()
                }
                uiState.orders.isEmpty() -> {
                    EmptyState(message = "暂无订单")
                }
                else -> {
                    SwipeRefresh(
                        state = swipeRefreshState,
                        onRefresh = { viewModel.refreshOrders() }
                    ) {
                        OrderList(
                            orders = uiState.orders,
                            onOrderClick = onOrderClick
                        )
                    }
                }
            }
        }
    }
}

/**
 * 过滤器区域
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSection(
    selectedStatus: String?,
    selectedDeliveryMethod: String?,
    selectedDate: Date?,
    onStatusSelected: (String?) -> Unit,
    onDeliveryMethodSelected: (String?) -> Unit,
    onDateSelected: (Date?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 状态过滤器
        ExposedDropdownMenuBox(
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier.weight(1f)
        ) {
            TextField(
                value = selectedStatus ?: "全部状态",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.menuAnchor()
            )
            DropdownMenu(
                expanded = false,
                onDismissRequest = {}
            ) {
                DropdownMenuItem(
                    text = { Text("全部") },
                    onClick = { onStatusSelected(null) }
                )
                listOf("pending", "processing", "completed", "cancelled").forEach { status ->
                    DropdownMenuItem(
                        text = { Text(status) },
                        onClick = { onStatusSelected(status) }
                    )
                }
            }
        }

        // 配送方式过滤器
        ExposedDropdownMenuBox(
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier.weight(1f)
        ) {
            TextField(
                value = selectedDeliveryMethod ?: "全部方式",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.menuAnchor()
            )
            DropdownMenu(
                expanded = false,
                onDismissRequest = {}
            ) {
                DropdownMenuItem(
                    text = { Text("全部") },
                    onClick = { onDeliveryMethodSelected(null) }
                )
                listOf("delivery", "pickup").forEach { method ->
                    DropdownMenuItem(
                        text = { Text(method) },
                        onClick = { onDeliveryMethodSelected(method) }
                    )
                }
            }
        }

        // 日期选择器
        IconButton(onClick = { /* 显示日期选择器 */ }) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "选择日期"
            )
        }
    }
}

/**
 * 订单列表
 */
@Composable
private fun OrderList(
    orders: List<Order>,
    onOrderClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(orders) { order ->
            OrderItem(
                order = order,
                onClick = { onOrderClick(order.id.toString()) }
            )
        }
    }
}

/**
 * 订单项
 */
@Composable
private fun OrderItem(
    order: Order,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "订单 #${order.number}",
                    style = MaterialTheme.typography.titleMedium
                )
                StatusBadge(status = order.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "客户: ${order.customerName}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "创建时间: ${dateFormat.format(order.dateCreated)}",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "总金额: ${order.total}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (order.orderMethod != null) {
                Text(
                    text = "订单方式: ${order.orderMethod}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (order.deliveryDate != null && order.deliveryTime != null) {
                Text(
                    text = "配送时间: ${order.deliveryDate} ${order.deliveryTime}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
} 