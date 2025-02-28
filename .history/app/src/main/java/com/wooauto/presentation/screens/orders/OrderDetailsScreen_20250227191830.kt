package com.wooauto.presentation.screens.orders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wooauto.data.remote.models.OrderItemResponse
import com.wooauto.presentation.components.ErrorState
import com.wooauto.presentation.components.LoadingIndicator
import com.wooauto.presentation.components.StatusBadge
import com.wooauto.presentation.viewmodels.OrderDetailsViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * 订单详情页面
 *
 * @param orderId 订单ID
 * @param onNavigateBack 返回回调
 * @param viewModel 订单详情 ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailsScreen(
    orderId: Long,
    onNavigateBack: () -> Unit,
    viewModel: OrderDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("订单 #${uiState.order?.number ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.markOrderAsPrinted() },
                        enabled = uiState.order != null
                    ) {
                        Icon(Icons.Default.Print, "打印订单")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error!!,
                        onRetry = { viewModel.refreshOrderDetails() }
                    )
                }
                uiState.isLoading -> {
                    LoadingIndicator()
                }
                uiState.order != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 订单状态部分
                        item {
                            OrderStatusSection(
                                currentStatus = uiState.order!!.status,
                                onStatusChange = { viewModel.updateOrderStatus(it) }
                            )
                        }

                        // 基本信息部分
                        item {
                            OrderBasicInfoSection(
                                order = uiState.order!!,
                                dateFormat = dateFormat
                            )
                        }

                        // 配送信息部分
                        item {
                            DeliveryInfoSection(order = uiState.order!!)
                        }

                        // 客户信息部分
                        item {
                            CustomerInfoSection(order = uiState.order!!)
                        }

                        // 订单项目部分
                        item {
                            Text(
                                text = "订单项目",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        // 使用Gson解析订单项JSON
                        val gson = remember { Gson() }
                        val type = remember { object : TypeToken<List<OrderItemResponse>>() {}.type }
                        val orderItems = remember(uiState.order!!.lineItemsJson) {
                            gson.fromJson<List<OrderItemResponse>>(uiState.order!!.lineItemsJson, type)
                        }

                        items(orderItems) { item ->
                            OrderLineItem(item = item)
                        }

                        // 订单总计部分
                        item {
                            OrderTotalSection(order = uiState.order!!)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 订单状态部分
 */
@Composable
private fun OrderStatusSection(
    currentStatus: String,
    onStatusChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val statusOptions = listOf("pending", "processing", "completed", "cancelled", "refunded")

    Column {
        Text(
            text = "订单状态",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box {
            StatusBadge(
                status = currentStatus,
                modifier = Modifier.clickable { expanded = true }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                statusOptions.forEach { status ->
                    DropdownMenuItem(
                        text = { Text(status) },
                        onClick = {
                            onStatusChange(status)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * 订单基本信息部分
 */
@Composable
private fun OrderBasicInfoSection(
    order: com.wooauto.domain.models.Order,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "基本信息",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text("订单号: #${order.number}")
            Text("创建时间: ${dateFormat.format(order.dateCreated)}")
            Text("支付方式: ${order.paymentMethodTitle}")
            if (order.customerNote != null) {
                Text(
                    text = "客户备注:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = order.customerNote,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * 配送信息部分
 */
@Composable
private fun DeliveryInfoSection(
    order: com.wooauto.domain.models.Order
) {
    if (order.orderMethod == "delivery") {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "配送信息",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(order.shippingAddress)
                if (order.deliveryDate != null && order.deliveryTime != null) {
                    Text("配送日期: ${order.deliveryDate}")
                    Text("配送时间: ${order.deliveryTime}")
                }
                if (order.deliveryFee != null) {
                    Text("配送费: ${order.deliveryFee}")
                }
            }
        }
    }
}

/**
 * 客户信息部分
 */
@Composable
private fun CustomerInfoSection(
    order: com.wooauto.domain.models.Order
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "客户信息",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text("客户ID: ${order.customerId}")
            Text("姓名: ${order.customerName}")
            Text("账单地址:")
            Text(order.billingAddress)
        }
    }
}

/**
 * 订单项目
 */
@Composable
private fun OrderLineItem(
    item: OrderItemResponse
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "单价: ${item.price}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "x${item.quantity}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "小计: ${item.total}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * 订单总计部分
 */
@Composable
private fun OrderTotalSection(
    order: com.wooauto.domain.models.Order
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "订单总计",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (order.deliveryFee != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("配送费")
                    Text(order.deliveryFee)
                }
            }
            if (order.tip != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("小费")
                    Text(order.tip)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "总计",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = order.total,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
} 