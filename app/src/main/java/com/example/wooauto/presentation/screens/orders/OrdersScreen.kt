package com.example.wooauto.presentation.screens.orders

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.OrderItem
import com.example.wooauto.navigation.NavigationItem
import com.example.wooauto.presentation.theme.WooAutoTheme
import com.example.wooauto.utils.LocaleHelper
import com.google.gson.Gson
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = hiltViewModel(),
    navController: NavController = rememberNavController()
) {
    val context = LocalContext.current
    Log.d("OrdersScreen", "订单屏幕初始化")
    
    val isConfigured by viewModel.isConfigured.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedOrder by viewModel.selectedOrder.collectAsState()
    val isRefreshing by viewModel.refreshing.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // 当进入此屏幕时执行刷新操作
    LaunchedEffect(key1 = Unit) {
        Log.d("OrdersScreen", "LaunchedEffect 触发，刷新订单数据")
        if (isConfigured) {
            viewModel.refreshOrders()
        }
    }
    
    // 显示错误消息
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }
    
    var searchQuery by remember { mutableStateOf("") }
    var showOrderDetail by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf("") }
    var statusExpanded by remember { mutableStateOf(false) }
    
    val statusOptions = listOf(
        "" to "全部状态",
        "processing" to "处理中",
        "pending" to "待处理",
        "on-hold" to "保留",
        "completed" to "已完成",
        "cancelled" to "已取消",
        "refunded" to "已退款",
        "failed" to "失败"
    )
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 加载中状态
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在加载...")
                }
            } 
            // API 未配置状态
            else if (!isConfigured) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "未配置",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "WooCommerce API 未配置",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "请先在设置页面配置您的 WooCommerce API 连接信息，才能查看订单数据。",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { 
                            Log.d("OrdersScreen", "点击前往设置按钮，导航到：${NavigationItem.Settings.route}")
                            navController.navigate(NavigationItem.Settings.route)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("前往设置")
                    }
                }
            } 
            // 已配置，显示订单列表
            else {
                Log.d("OrdersScreen", "显示订单列表，共 ${orders.size} 个订单")
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 顶部提示和刷新按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "订单列表",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        IconButton(
                            onClick = { viewModel.refreshOrders() },
                            modifier = Modifier.size(48.dp),
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "刷新",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 搜索和过滤
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 搜索框
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(0.7f),
                            placeholder = { Text("搜索订单...") },
                            leadingIcon = { 
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "搜索"
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "清除"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                        
                        // 状态过滤器
                        ExposedDropdownMenuBox(
                            expanded = statusExpanded,
                            onExpandedChange = { statusExpanded = it },
                            modifier = Modifier.weight(0.3f)
                        ) {
                            OutlinedTextField(
                                value = statusOptions.first { it.first == statusFilter }.second,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.menuAnchor(),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded)
                                },
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            )
                            
                            ExposedDropdownMenu(
                                expanded = statusExpanded,
                                onDismissRequest = { statusExpanded = false }
                            ) {
                                statusOptions.forEach { (status, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            statusFilter = status
                                            statusExpanded = false
                                            viewModel.filterOrdersByStatus(status.takeIf { it.isNotEmpty() })
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 订单列表
                    if (orders.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "无数据",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "暂无订单数据",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val filteredOrders = orders.filter {
                                val orderNumber = it.number.lowercase(Locale.getDefault())
                                val customerName = it.customerName.lowercase(Locale.getDefault())
                                val query = searchQuery.lowercase(Locale.getDefault())
                                
                                (orderNumber.contains(query) || customerName.contains(query))
                            }
                            
                            if (filteredOrders.isEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "未找到匹配的订单",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            } else {
                                items(filteredOrders) { order ->
                                    OrderCard(
                                        order = order,
                                        onClick = {
                                            viewModel.getOrderDetails(order.id)
                                            showOrderDetail = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 显示订单详情对话框
                if (showOrderDetail && selectedOrder != null) {
                    OrderDetailDialog(
                        order = selectedOrder!!,
                        onDismiss = { 
                            showOrderDetail = false
                            viewModel.clearSelectedOrder()
                        },
                        onStatusChange = { orderId, newStatus ->
                            viewModel.updateOrderStatus(orderId, newStatus)
                        },
                        onMarkAsPrinted = { orderId ->
                            viewModel.markOrderAsPrinted(orderId)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderCard(
    order: Order,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "订单号: ${order.number}",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "客户: ${order.customerName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val formattedDate = dateFormat.format(order.dateCreated)
                
                Text(
                    text = "日期: $formattedDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = order.total,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 将WooCommerce状态转换为中文显示
                val statusText = when(order.status) {
                    "processing" -> "处理中"
                    "pending" -> "待处理"
                    "on-hold" -> "保留"
                    "completed" -> "已完成"
                    "cancelled" -> "已取消"
                    "refunded" -> "已退款"
                    "failed" -> "失败"
                    else -> order.status
                }
                
                val statusColor = when(order.status) {
                    "completed" -> MaterialTheme.colorScheme.primary
                    "processing" -> Color(0xFF2196F3) // 蓝色
                    "pending" -> Color(0xFFFFA000) // 橙色
                    "cancelled", "failed" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = statusColor.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                    }
                    
                    if (order.status == "completed") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "已完成",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                if (order.isPrinted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "已打印",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun OrderDetailDialog(
    order: Order,
    onDismiss: () -> Unit,
    onStatusChange: (Long, String) -> Unit,
    onMarkAsPrinted: (Long) -> Unit
) {
    var showStatusOptions by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "订单详情",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OrderDetailRow("订单号", order.number)
                OrderDetailRow("订单ID", order.id.toString())
                
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val formattedDate = dateFormat.format(order.dateCreated)
                OrderDetailRow("下单日期", formattedDate)
                
                OrderDetailRow("客户", order.customerName)
                
                if (order.contactInfo.isNotEmpty()) {
                    OrderDetailRow("联系方式", order.contactInfo)
                }
                
                if (order.billingInfo.isNotEmpty()) {
                    OrderDetailRow("账单信息", order.billingInfo)
                }
                
                OrderDetailRow("支付方式", order.paymentMethod)
                OrderDetailRow("总金额", order.total)
                
                // 显示订单项目
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "订单项目",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                order.items.forEach { item ->
                    OrderItemRow(item)
                }
                
                // 状态显示
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "状态: ",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    val statusText = when(order.status) {
                        "processing" -> "处理中"
                        "pending" -> "待处理"
                        "on-hold" -> "保留"
                        "completed" -> "已完成"
                        "cancelled" -> "已取消"
                        "refunded" -> "已退款"
                        "failed" -> "失败"
                        else -> order.status
                    }
                    
                    val statusColor = when(order.status) {
                        "completed" -> MaterialTheme.colorScheme.primary
                        "processing" -> Color(0xFF2196F3) // 蓝色
                        "pending" -> Color(0xFFFFA000) // 橙色
                        "cancelled", "failed" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(
                                color = statusColor.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable { showStatusOptions = true }
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor
                        )
                    }
                    
                    if (showStatusOptions) {
                        StatusChangeDialog(
                            currentStatus = order.status,
                            onDismiss = { showStatusOptions = false },
                            onStatusSelected = { newStatus ->
                                onStatusChange(order.id, newStatus)
                                showStatusOptions = false
                            }
                        )
                    }
                }
                
                // 按钮操作区
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 打印按钮
                    Button(
                        onClick = { onMarkAsPrinted(order.id) }
                    ) {
                        Text(if (order.isPrinted) "重新打印" else "打印订单")
                    }
                    
                    // 关闭按钮
                    Button(
                        onClick = onDismiss
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
fun OrderDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StatusChangeDialog(
    currentStatus: String,
    onDismiss: () -> Unit,
    onStatusSelected: (String) -> Unit
) {
    val statusOptions = listOf(
        "processing" to "处理中",
        "pending" to "待处理",
        "on-hold" to "保留",
        "completed" to "已完成",
        "cancelled" to "已取消",
        "refunded" to "已退款",
        "failed" to "失败"
    )
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "更改订单状态",
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn {
                    items(statusOptions) { (status, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStatusSelected(status) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isSelected = status == currentStatus
                            val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor
                            )
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "已选择",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
fun OrderItemRow(item: OrderItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${item.name} × ${item.quantity}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = item.total,
            style = MaterialTheme.typography.bodyMedium
        )
    }
} 