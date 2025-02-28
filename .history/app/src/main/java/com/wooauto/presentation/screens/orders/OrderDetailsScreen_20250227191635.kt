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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("订单 #${uiState.order?.number}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // 打印按钮
                    IconButton(
                        onClick = { viewModel.markOrderAsPrinted() },
                        enabled = uiState.order != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "打印订单"
                        )
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
                    OrderDetails(
                        viewModel = viewModel,
                        uiState = uiState
                    )
                }
            }
        }
    }
}

/**
 * 订单详情内容
 */
@Composable
private fun OrderDetails(
    viewModel: OrderDetailsViewModel,
    uiState: OrderDetailsUiState
) {
    val order = uiState.order!!
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val gson = remember { Gson() }
    val itemType = remember { object : TypeToken<List<OrderItemResponse>>() {}.type }
    val orderItems: List<OrderItemResponse> = remember(order.lineItemsJson) {
        gson.fromJson(order.lineItemsJson, itemType)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 订单状态部分
        item {
            OrderStatusSection(
                currentStatus = order.status,
                onStatusChange = { viewModel.updateOrderStatus(it) }
            )
        }

        // 订单基本信息部分
        item {
            OrderBasicInfoSection(
                order = order,
                dateFormat = dateFormat
            )
        }

        // 配送信息部分
        item {
            DeliveryInfoSection(order = order)
        }

        // 客户信息部分
        item {
            CustomerInfoSection(order = order)
        }

        // 订单项目列表
        item {
            Text(
                text = "订单项目",
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        items(orderItems) { item ->
            OrderLineItem(item = item)
        }

        // 订单总计部分
        item {
            OrderTotalSection(order = order)
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
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "订单状态",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status = currentStatus)
                AssistChip(
                    onClick = { /* 显示状态选择对话框 */ },
                    label = { Text("更改状态") }
                )
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
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "基本信息",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("订单编号: #${order.number}")
            Text("创建时间: ${dateFormat.format(order.dateCreated)}")
            Text("支付方式: ${order.paymentMethodTitle}")
            if (order.customerNote != null) {
                Text("客户备注: ${order.customerNote}")
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
    if (order.orderMethod != null) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "配送信息",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("订单方式: ${order.orderMethod}")
                if (order.orderMethod == "delivery") {
                    Text("配送地址: ${order.shippingAddress}")
                    if (order.deliveryDate != null && order.deliveryTime != null) {
                        Text("配送时间: ${order.deliveryDate} ${order.deliveryTime}")
                    }
                    if (order.deliveryFee != null) {
                        Text("配送费: ${order.deliveryFee}")
                    }
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
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "客户信息",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("客户ID: ${order.customerId}")
            Text("客户姓名: ${order.customerName}")
            Text("账单地址: ${order.billingAddress}")
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "数量: ${item.quantity} × ${item.price}",
                    style = MaterialTheme.typography.bodyMedium
                )
                item.metaData.forEach { meta ->
                    if (meta.key != "_exoptions") {
                        Text(
                            text = "${meta.key}: ${meta.value}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Text(
                text = item.total,
                style = MaterialTheme.typography.titleMedium
            )
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
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "订单总计",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
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
            Divider(modifier = Modifier.padding(vertical = 8.dp))
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