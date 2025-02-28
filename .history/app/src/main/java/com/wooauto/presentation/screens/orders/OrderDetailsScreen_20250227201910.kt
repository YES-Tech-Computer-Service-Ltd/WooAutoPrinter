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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wooauto.data.remote.models.OrderItemResponse
import com.wooauto.presentation.components.ErrorState
import com.wooauto.presentation.components.LoadingIndicator
import com.wooauto.presentation.components.StatusBadge
import com.wooauto.presentation.viewmodels.OrderDetailsViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.clickable

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
    orderId: String,
    onNavigateBack: () -> Unit,
    viewModel: OrderDetailsViewModel = hiltViewModel()
) {
    LaunchedEffect(orderId) {
        viewModel.getOrderDetails(orderId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.order_details)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
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
            when (val uiState = viewModel.uiState.value) {
                is OrderDetailsUiState.Loading -> {
                    LoadingIndicator()
                }
                is OrderDetailsUiState.Error -> {
                    ErrorContent(
                        message = uiState.message,
                        onRetry = { viewModel.getOrderDetails(orderId) }
                    )
                }
                is OrderDetailsUiState.Success -> {
                    OrderDetailsContent(
                        order = uiState.order!!,
                        onMarkAsComplete = { viewModel.completeOrder(orderId) },
                        onPrintOrder = { viewModel.printOrderDetails(orderId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderDetailsContent(
    order: com.wooauto.domain.models.Order,
    onMarkAsComplete: () -> Unit,
    onPrintOrder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Order basic information
        Text(
            text = stringResource(R.string.order_number, order.number),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        // Customer information
        Text(
            text = stringResource(R.string.customer_name, order.customerName),
            style = MaterialTheme.typography.bodyLarge
        )
        
        // Actions
        OrderActions(viewModel, orderId)
    }
}

@Composable
private fun OrderActions(
    viewModel: OrderDetailsViewModel,
    orderId: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            onClick = { viewModel.completeOrder(orderId) },
            modifier = Modifier.weight(1f)
        ) {
            Text(text = stringResource(R.string.mark_as_complete))
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Button(
            onClick = { viewModel.printOrderDetails(orderId) },
            modifier = Modifier.weight(1f)
        ) {
            Text(text = stringResource(R.string.print_order))
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
    val statusMap = mapOf(
        "pending" to "待处理",
        "processing" to "处理中",
        "completed" to "已完成",
        "cancelled" to "已取消",
        "refunded" to "已退款"
    )

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "订单状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(status = currentStatus)
                    Text(
                        text = statusMap[currentStatus] ?: currentStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    statusOptions.forEach { status ->
                        DropdownMenuItem(
                            text = { 
                                Text(statusMap[status] ?: status)
                            },
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "单价: ${item.price}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "x${item.quantity}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "小计: ${item.total}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // 显示商品的元数据
            if (item.metaData.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                item.metaData.forEach { meta ->
                    if (meta.key != "_exoptions") {
                        Text(
                            text = "${meta.key}: ${meta.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (order.deliveryFee != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "配送费",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = order.deliveryFee,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            if (order.tip != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "小费",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = order.tip,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Divider(
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "总计",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = order.total,
                    style = MaterialThedicine.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
} 