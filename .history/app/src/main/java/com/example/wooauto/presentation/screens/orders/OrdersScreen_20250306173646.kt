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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.OrderItem
import com.example.wooauto.navigation.NavigationItem
import com.example.wooauto.presentation.theme.WooAutoTheme
import com.example.wooauto.utils.LocaleHelper
import com.example.wooauto.presentation.screens.settings.SettingsViewModel
import com.google.gson.Gson
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TopAppBar
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.wooauto.R
import com.example.wooauto.utils.LocalAppLocale
import com.example.wooauto.utils.LocaleManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = hiltViewModel(),
    navController: NavController = rememberNavController()
) {
    Log.d("OrdersScreen", "订单屏幕初始化")
    
    // 获取当前语言环境
    val locale = LocalAppLocale.current
    val context = LocalContext.current
    
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
    
    // 根据当前语言环境提供状态选项
    val statusOptions = if (locale.language == "zh") {
        listOf(
            "" to "全部状态",
            "processing" to "处理中",
            "pending" to "待处理",
            "on-hold" to "保留",
            "completed" to "已完成",
            "cancelled" to "已取消",
            "refunded" to "已退款",
            "failed" to "失败"
        )
    } else {
        listOf(
            "" to "All Status",
            "processing" to "Processing",
            "pending" to "Pending",
            "on-hold" to "On Hold",
            "completed" to "Completed",
            "cancelled" to "Cancelled",
            "refunded" to "Refunded",
            "failed" to "Failed"
        )
    }
    
    Scaffold(
        // 删除顶部AppBar，使用WooAutoApp中的全局AppBar
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
                    Text(if (locale.language == "zh") "正在加载..." else "Loading...")
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
                        contentDescription = if (locale.language == "zh") "未配置" else "Not Configured",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (locale.language == "zh") "WooCommerce API 未配置" else "WooCommerce API Not Configured",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (locale.language == "zh") 
                            "请先在设置页面配置您的 WooCommerce API 连接信息，才能查看订单数据。" 
                        else 
                            "Please configure your WooCommerce API connection in the settings page to view order data.",
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
                            contentDescription = if (locale.language == "zh") "设置" else "Settings"
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(if (locale.language == "zh") "前往设置" else "Go to Settings")
                    }
                }
            } 
            // 已配置，显示订单列表
            else {
                Log.d("OrdersScreen", "显示订单列表，共 ${orders.size} 个订单")
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    // 搜索框与刷新按钮行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 搜索框
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            placeholder = { Text(if (locale.language == "zh") "搜索订单..." else "Search orders...") },
                            leadingIcon = { 
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = if (locale.language == "zh") "搜索" else "Search"
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = if (locale.language == "zh") "清除" else "Clear"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                        
                        // 刷新按钮
                        IconButton(
                            onClick = { viewModel.refreshOrders() },
                            enabled = !isRefreshing,
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(4.dp)
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(id = R.string.refresh),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    
                    // 状态过滤器 - 水平滚动按钮样式
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        state = rememberLazyListState()
                    ) {
                        items(statusOptions) { (status, label) ->
                            FilterChip(
                                selected = statusFilter == status,
                                onClick = {
                                    statusFilter = status
                                    viewModel.filterOrdersByStatus(status.takeIf { it.isNotEmpty() })
                                },
                                label = { 
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium
                                    ) 
                                },
                                leadingIcon = if (statusFilter == status) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                    
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
                                contentDescription = if (locale.language == "zh") "无数据" else "No Data",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (locale.language == "zh") "暂无订单数据" else "No Order Data",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 0.dp)
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
                                            text = if (locale.language == "zh") "未找到匹配的订单" else "No matching orders found",
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
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = getStatusColor(order.status).copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = order.number.takeLast(2),
                    style = MaterialTheme.typography.titleMedium,
                    color = getStatusColor(order.status)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "订单号: ${order.number}",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = "客户: ${order.customerName}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // 添加外卖配送信息显示
                order.woofoodInfo?.let { woofoodInfo ->
                    if (woofoodInfo.isDelivery) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = "外卖配送",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = woofoodInfo.deliveryTime ?: "外卖配送",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
                
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
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
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
                
                val statusColor = getStatusColor(order.status)
                
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
                
                if (order.isPrinted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(id = R.string.printed),
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = stringResource(id = R.string.printed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun getStatusColor(status: String): Color {
    return when(status) {
        "completed" -> MaterialTheme.colorScheme.primary
        "processing" -> Color(0xFF2196F3) // 蓝色
        "pending" -> Color(0xFFFFA000) // 橙色
        "on-hold" -> Color(0xFF9C27B0) // 紫色
        "cancelled", "failed" -> MaterialTheme.colorScheme.error
        "refunded" -> Color(0xFF4CAF50) // 绿色
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailDialog(
    order: Order,
    onDismiss: () -> Unit,
    onStatusChange: (Long, String) -> Unit,
    onMarkAsPrinted: (Long) -> Unit
) {
    var showStatusOptions by remember { mutableStateOf(false) }
    var showTemplateOptions by remember { mutableStateOf(false) }
    val viewModel: OrdersViewModel = hiltViewModel()
    val scrollState = rememberScrollState()
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { 
                            Text(
                                text = "订单详情 #${order.number}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "关闭"
                                )
                            }
                        },
                        actions = {
                            // 添加打印按钮
                            IconButton(
                                onClick = {
                                    // 显示模板选择对话框
                                    showTemplateOptions = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = "打印订单"
                                )
                            }
                            
                            // 改变订单状态按钮
                            IconButton(
                                onClick = { showStatusOptions = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "更改状态"
                                )
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)  // 让内容区域填满剩余空间
                        .verticalScroll(scrollState)  // 添加垂直滚动
                        .padding(16.dp)
                ) {
                    OrderDetailRow(
                        label = "订单号", 
                        value = order.number,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Numbers,
                                contentDescription = "订单号",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    OrderDetailRow(
                        label = "订单ID", 
                        value = order.id.toString(),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Tag,
                                contentDescription = "订单ID",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val formattedDate = dateFormat.format(order.dateCreated)
                    OrderDetailRow(
                        label = "下单日期",
                        value = formattedDate,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "日期",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    OrderDetailRow(
                        label = "客户",
                        value = order.customerName,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "客户",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    if (order.contactInfo.isNotEmpty()) {
                        OrderDetailRow(
                            label = "联系方式",
                            value = order.contactInfo,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "电话联系",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    
                    if (order.billingInfo.isNotEmpty()) {
                        OrderDetailRow(
                            label = "账单信息",
                            value = order.billingInfo,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = "地址",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    
                    OrderDetailRow(
                        label = "支付方式",
                        value = order.paymentMethod,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Payment,
                                contentDescription = "支付方式",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    OrderDetailRow(
                        label = "总金额",
                        value = order.total,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = "金额",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    // WooFood信息显示，修改显示逻辑以确保始终显示订单方式
                    val woofoodInfo = order.woofoodInfo
                    if (woofoodInfo != null) {
                        android.util.Log.d("WooFood", "显示订单 #${order.number} 的 WooFood 信息: ${woofoodInfo}")
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "订单配送信息",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        // 始终显示订单方式
                        OrderDetailRow(
                            label = "订单方式",
                            value = woofoodInfo.orderMethod ?: if (woofoodInfo.isDelivery) "外卖配送" else "自提",
                            icon = {
                                Icon(
                                    imageVector = if (woofoodInfo.isDelivery) Icons.Default.LocalShipping else Icons.Default.Store,
                                    contentDescription = "订单方式",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                        
                        woofoodInfo.deliveryTime?.let {
                            OrderDetailRow(
                                label = "配送时间",
                                value = it,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "配送时间",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        } ?: if (woofoodInfo.isDelivery) {
                            // 如果是外卖但没有配送时间，则使用订单创建时间作为参考
                            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                            val orderTime = timeFormat.format(order.dateCreated)
                            OrderDetailRow(
                                label = "下单时间",
                                value = orderTime + " (参考配送时间)",
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "下单时间",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        } else {
                            // 不是外卖订单，不显示配送时间
                            null
                        }
                        
                        if (woofoodInfo.isDelivery) {
                            woofoodInfo.deliveryAddress?.let {
                                OrderDetailRow(
                                    label = "配送地址",
                                    value = it,
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = "配送地址",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                            
                            woofoodInfo.deliveryFee?.let {
                                OrderDetailRow(
                                    label = "配送费用",
                                    value = it,
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.LocalShipping,
                                            contentDescription = "配送费用",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                            
                            woofoodInfo.tip?.let {
                                OrderDetailRow(
                                    label = "小费",
                                    value = it,
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.MonetizationOn,
                                            contentDescription = "小费",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        }
                    } else {
                        android.util.Log.d("WooFood", "订单 #${order.number} 没有 WooFood 信息")
                    }
                    
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
                    
                    // 添加价格明细部分
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "价格明细",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    // 商品小计
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "商品小计",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "C$${order.subtotal}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    // 显示所有的费用行
                    order.feeLines.forEach { feeLine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = feeLine.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "C$${feeLine.total}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    // 税费
                    if (order.totalTax != "0" && order.totalTax != "0.0" && order.totalTax != "0.00") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "税费",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "C$${order.totalTax}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    // 如果有折扣，显示折扣
                    if (order.discountTotal != "0" && order.discountTotal != "0.0" && order.discountTotal != "0.00") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "折扣",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "-C$${order.discountTotal}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Red
                            )
                        }
                    }
                    
                    // 总计
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "订单总计",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = "C$${order.total}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
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
                    }
                    
                    // 额外添加一些底部空间，确保内容不被按钮遮挡
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // 按钮操作区（固定在底部不滚动）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
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
    
    // 显示状态选择对话框
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

@Composable
fun OrderDetailRow(
    label: String,
    value: String,
    icon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.width(80.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(4.dp))
        }
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
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