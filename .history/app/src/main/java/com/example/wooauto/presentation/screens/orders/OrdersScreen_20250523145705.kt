package com.example.wooauto.presentation.screens.orders

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TopAppBar
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wooauto.R
import com.example.wooauto.utils.LocalAppLocale
import com.example.wooauto.domain.templates.TemplateType
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.core.content.ContextCompat
import com.example.wooauto.presentation.components.WooTopBar
import com.example.wooauto.presentation.screens.products.UnconfiguredView
import com.example.wooauto.presentation.EventBus
import com.example.wooauto.presentation.SearchEvent
import com.example.wooauto.presentation.RefreshEvent
import androidx.compose.ui.unit.Dp
import com.example.wooauto.presentation.screens.orders.OrderDetailDialog

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
    
    // 提前获取需要使用的字符串资源
    val apiNotConfiguredMessage = stringResource(R.string.api_notification_not_configured)
    val ordersTitle = stringResource(id = R.string.orders)
    val searchOrdersPlaceholder = if (locale.language == "zh") "搜索订单..." else "Search orders..."
    val unreadOrdersText = if (locale.language == "zh") "未读订单" else "Unread Orders"
    val errorApiNotConfigured = stringResource(R.string.error_api_not_configured)
    
    val isConfigured by viewModel.isConfigured.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedOrder by viewModel.selectedOrder.collectAsState()
    val isRefreshing by viewModel.refreshing.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    
    // 新增状态，用于控制何时显示UI
    val isInitialized = remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    var searchQuery by remember { mutableStateOf("") }
    var showOrderDetail by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf("") }
    var showUnreadOrders by remember { mutableStateOf(false) }
    
    // 记录TopBar的实际高度
    var topBarHeight by remember { mutableStateOf(0.dp) }
    
    // 观察导航事件
    val navigationEvent by viewModel.navigationEvent.collectAsState()
    
    // 处理导航事件
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                "license_settings" -> {
                    navController.navigate(com.example.wooauto.presentation.navigation.Screen.LicenseSettings.route)
                    viewModel.clearNavigationEvent()
                }
            }
        }
    }
    
    // 接收搜索和刷新事件
    val eventScope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        // 启动搜索事件收集器
        val searchJob = eventScope.launch {
            EventBus.searchEvents.collect { event ->
                if (event.screenRoute == NavigationItem.Orders.route) {
                    Log.d("OrdersScreen", "收到搜索事件：${event.query}")
                    searchQuery = event.query
                    // 这里可以添加搜索逻辑
                    // viewModel.searchOrders(event.query)
                }
            }
        }
        
        // 启动刷新事件收集器
        val refreshJob = eventScope.launch {
            EventBus.refreshEvents.collect { event ->
                if (event.screenRoute == NavigationItem.Orders.route) {
                    Log.d("OrdersScreen", "收到刷新事件")
                    viewModel.refreshOrders()
                }
            }
        }
        
        // 清理协程
        onDispose {
            searchJob.cancel()
            refreshJob.cancel()
            Log.d("OrdersScreen", "清理事件订阅协程")
        }
    }
    
    // 当进入此屏幕时执行初始化操作
    LaunchedEffect(key1 = Unit) {
        Log.d("OrdersScreen", "LaunchedEffect 触发，开始初始化流程")
        
        // 首先检查API是否已配置
        val configResult = viewModel.checkApiConfiguration()
        
        if (configResult) {
            // API已配置，直接刷新订单数据
            Log.d("OrdersScreen", "API已配置，直接刷新订单数据")
            viewModel.refreshOrders()
            // 数据加载完成后将初始化状态设为true
            isInitialized.value = true
        } else {
            // API未配置，显示配置对话框
            Log.d("OrdersScreen", "API未配置，显示配置对话框")
            // 虽然未配置API，但仍然可以展示UI
            isInitialized.value = true
            
            // 添加黑色Toast提示
            coroutineScope.launch {
                snackbarHostState.showSnackbar(apiNotConfiguredMessage)
            }
        }
    }
    
    // 注册接收新订单详情显示的广播接收器
    DisposableEffect(key1 = context) {
        val intentFilter = android.content.IntentFilter("com.example.wooauto.ACTION_OPEN_ORDER_DETAILS")
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == "com.example.wooauto.ACTION_OPEN_ORDER_DETAILS") {
                    val orderId = intent.getLongExtra("orderId", -1L)
                    if (orderId != -1L) {
                        Log.d("OrdersScreen", "收到显示订单详情广播，订单ID: $orderId")
                        // 导航到Orders页面（如果不在的话）
                        navController.navigate(NavigationItem.Orders.route) {
                            // 防止创建多个实例
                            launchSingleTop = true
                            // 避免重复进入
                            restoreState = true
                        }
                        // 显示订单详情
                        viewModel.getOrderDetails(orderId)
                        showOrderDetail = true
                    }
                }
            }
        }
        
        // 根据API级别使用相应的注册方法
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
            Log.d("OrdersScreen", "使用RECEIVER_NOT_EXPORTED标志注册订单详情广播接收器(Android 13+)")
        } else {
            ContextCompat.registerReceiver(
                context,
                receiver,
                intentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            Log.d("OrdersScreen", "标准方式注册订单详情广播接收器(Android 12及以下)")
        }
        
        // 在效果结束时注销接收器
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e("OrdersScreen", "注销接收器失败", e)
            }
        }
    }
    
    // 显示错误消息
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            coroutineScope.launch {
                // 确保错误消息显示优先，关闭API配置对话框
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }
    
    // 检查API配置状态，优化判断逻辑
    LaunchedEffect(isLoading, isConfigured, orders, errorMessage) {
        Log.d("OrdersScreen", "检查API配置状态: isLoading=$isLoading, isConfigured=$isConfigured, 订单数量=${orders.size}, 错误=${errorMessage != null}")
        
        // 只有在没有错误消息的情况下才显示API配置对话框
        if (errorMessage == null && !isLoading && !isConfigured && orders.isEmpty()) {
            // 只有在未配置API且没有订单数据时才显示配置对话框
            // 这里不再调用私有方法checkApiConfiguration，而是刷新订单
            viewModel.refreshOrders()
        } else {
            // 已经加载了数据或API已配置或有错误消息，不显示配置对话框
            // 这里不再调用私有方法checkApiConfiguration，而是刷新订单
            viewModel.refreshOrders()
        }
    }
    
    // 订单页面每次成为活动页面时刷新API配置状态
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val currentRoute = destination.route
            Log.d("OrdersScreen", "当前导航到页面: $currentRoute")
            if (currentRoute == NavigationItem.Orders.route) {
                Log.d("OrdersScreen", "导航回订单页面，刷新订单数据")
                viewModel.refreshOrders()
            }
        }
        
        // 添加监听器
        navController.addOnDestinationChangedListener(listener)
        
        // 清理
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }
    
    // 根据当前语言环境提供状态选项
    val statusOptions = if (locale.language == "zh") {
        listOf(
            "" to "全部状态",
            "processing" to "处理中",
            "pending" to "待付款",
            "on-hold" to "暂挂",
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            WooTopBar(
                title = ordersTitle,
                showSearch = true,
                searchQuery = searchQuery,
                onSearchQueryChange = { newQuery -> 
                    searchQuery = newQuery
                },
                searchPlaceholder = searchOrdersPlaceholder,
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshOrders() },
                showRefreshButton = true,
                locale = locale
            )
        }
    ) { paddingValues ->
        // 获取系统状态栏和TopBar的组合高度
        val topPadding = paddingValues.calculateTopPadding()
        Log.d("OrdersScreen", "TopBar和状态栏总高度：$topPadding")
        
        // 打印完整的内边距信息用于调试
        Log.d("OrdersScreen", "使用Scaffold提供的内边距")
        
        // 使用动态调整后的内边距
        Box(
            modifier = Modifier.padding(paddingValues)
        ) {
            // 先检查是否初始化完成
            if (!isInitialized.value) {
                // 显示初始化加载界面
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "正在加载订单数据...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else if (isLoading && orders.isEmpty()) {
                // 已初始化但正在加载且没有订单数据
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (orders.isEmpty() && !isConfigured) {
                // 没有订单且API未配置，使用UnconfiguredView
                UnconfiguredView(
                    errorMessage = errorMessage ?: errorApiNotConfigured,
                    onSettingsClick = { 
                        navController.navigate(NavigationItem.Settings.route) {
                            launchSingleTop = true
                        }
                        // 保留广播以自动打开API配置对话框
                        val intent = Intent("com.example.wooauto.ACTION_OPEN_API_SETTINGS")
                        context.sendBroadcast(intent)
                    }
                )
            } else if (orders.isEmpty() && isConfigured) {
                // 已配置但没有订单
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(R.string.no_orders_found),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringResource(R.string.no_orders_message),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { viewModel.refreshOrders() },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = stringResource(id = R.string.refresh))
                    }
                }
            } else {
                // 有订单数据，显示订单列表
                OrdersList(
                    orders = orders,
                    showUnreadOnly = showUnreadOrders,
                    selectedStatus = statusFilter,
                    searchQuery = searchQuery,
                    onSelectOrder = { order ->
                        viewModel.getOrderDetails(order.id)
                        showOrderDetail = true
                        // 标记订单为已读
                        viewModel.markOrderAsRead(order.id)
                    },
                    onStatusSelected = { status ->
                        statusFilter = status
                        viewModel.filterOrdersByStatus(status)
                    }
                )
            }
            
            // 订单详情对话框
            if (showOrderDetail && selectedOrder != null) {
                OrderDetailDialog(
                    order = selectedOrder!!,
                    onDismiss = { showOrderDetail = false },
                    onStatusChange = { orderId, newStatus ->
                        // 调用状态变更逻辑
                        viewModel.updateOrderStatus(orderId, newStatus)
                    },
                    onMarkAsPrinted = { orderId ->
                        // 直接调用标记为已打印的方法，不需要调用打印逻辑
                        viewModel.markOrderAsPrinted(orderId)
                    }
                )
            }
        }
    }
}

/**
 * 订单列表组件
 */
@Composable
private fun OrdersList(
    orders: List<Order>,
    showUnreadOnly: Boolean,
    selectedStatus: String,
    searchQuery: String,
    onSelectOrder: (Order) -> Unit,
    onStatusSelected: (String) -> Unit
) {
    val locale = LocalAppLocale.current
    
    // 定义状态选项列表 - 确保与API支持的值一致
    val statusOptions = listOf(
        "" to (if (locale.language == "zh") "全部" else "All"),
        "processing" to (if (locale.language == "zh") "处理中" else "Processing"),
        "completed" to (if (locale.language == "zh") "已完成" else "Completed"),
        "pending" to (if (locale.language == "zh") "待付款" else "Pending"),
        "cancelled" to (if (locale.language == "zh") "已取消" else "Cancelled"),
        "on-hold" to (if (locale.language == "zh") "暂挂" else "On Hold"),
        "refunded" to (if (locale.language == "zh") "已退款" else "Refunded"),
        "failed" to (if (locale.language == "zh") "失败" else "Failed")
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 0.dp) // 移除底部padding
    ) {
        // 移除手动添加的顶部Spacer
        
        // 移除Card，直接使用Row作为状态过滤栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 标题区域
            Row(
                modifier = Modifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 筛选图标
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 标题文本
                Text(
                    text = if (locale.language == "zh") "订单状态:" else "Status:",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 筛选选项区域
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(statusOptions) { statusOption ->
                    val (status, label) = statusOption
                    val isSelected = selectedStatus == status
                    
                    // 为状态选项添加图标和颜色
                    val statusIcon = when(status) {
                        "processing" -> Icons.Default.Schedule
                        "completed" -> Icons.Default.CheckCircle
                        "pending" -> Icons.Default.Schedule
                        "cancelled" -> Icons.Default.Close
                        "on-hold" -> Icons.Default.Schedule
                        else -> Icons.Default.List
                    }
                    
                    val statusColor = when(status) {
                        "processing" -> Color(0xFF2196F3) // 蓝色
                        "completed" -> Color(0xFF4CAF50) // 绿色
                        "pending" -> Color(0xFFFFA000) // 橙色
                        "cancelled" -> Color(0xFFE53935) // 红色 
                        "on-hold" -> Color(0xFF9C27B0) // 紫色
                        else -> MaterialTheme.colorScheme.primary
                    }
                    
                    // 使用Box替代FilterChip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                color = if (isSelected) 
                                    if (status.isEmpty()) 
                                        MaterialTheme.colorScheme.primaryContainer
                                    else 
                                        statusColor.copy(alpha = 0.15f)
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected)
                                    if (status.isEmpty())
                                        MaterialTheme.colorScheme.primary
                                    else
                                        statusColor
                                else
                                    if (status.isEmpty())
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    else
                                        statusColor.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onStatusSelected(status) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // 只有非空状态才显示图标
                            if (status.isNotEmpty()) {
                                Icon(
                                    imageVector = statusIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) 
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else 
                                        statusColor.copy(alpha = 0.7f)
                                )
                                
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1,
                                color = if (isSelected)
                                    if (status.isEmpty())
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        statusColor
                                else
                                    if (status.isEmpty())
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        statusColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
            
            // 添加清除筛选按钮
            if (selectedStatus.isNotEmpty()) {
                IconButton(
                    onClick = { onStatusSelected("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = if (locale.language == "zh") "清除筛选" else "Clear filter",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        // 订单列表
        val filteredOrders = orders.filter {
            // 状态过滤
            val statusMatch = selectedStatus.isEmpty() || it.status == selectedStatus
            // 搜索过滤
            val queryMatch = searchQuery.isEmpty() || 
                it.number.contains(searchQuery, ignoreCase = true) || 
                it.customerName.contains(searchQuery, ignoreCase = true)
            // 未读过滤
            val unreadMatch = !showUnreadOnly || !it.isRead
            
            statusMatch && queryMatch && unreadMatch
        }
        
        if (filteredOrders.isEmpty()) {
            // 没有匹配的订单 - 美化空状态展示
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 40.dp), // 增加顶部间距
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 添加图标
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 主要提示文本
                Text(
                    text = if (locale.language == "zh") "未找到匹配的订单" else "No matching orders found",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 次要提示文本
                Text(
                    text = if (locale.language == "zh") "尝试更改筛选条件" else "Try changing your filter criteria",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 清除筛选按钮
                if (selectedStatus.isNotEmpty() || searchQuery.isNotEmpty() || showUnreadOnly) {
                    Button(
                        onClick = { 
                            // 清除所有筛选条件，恢复到默认视图
                            onStatusSelected("")
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = if (locale.language == "zh") "清除筛选" else "Clear Filters")
                    }
                }
            }
        } else {
            // 添加筛选结果摘要
            if (selectedStatus.isNotEmpty() || searchQuery.isNotEmpty() || showUnreadOnly) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (locale.language == "zh") 
                            "找到 ${filteredOrders.size} 个订单" 
                        else 
                            "Found ${filteredOrders.size} orders",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // 添加排序选项 (可扩展功能)
                    /*
                    Text(
                        text = if (locale.language == "zh") "排序: 最新" else "Sort: Newest",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { /* 排序功能 */ }
                    )
                    */
                }
            }
            
            // 显示订单列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp), // 增加底部内边距
                verticalArrangement = Arrangement.spacedBy(4.dp) // 保持一致的间距
            ) {
                items(filteredOrders) { order ->
                    OrderCard(
                        order = order,
                        onClick = { onSelectOrder(order) }
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
            .padding(horizontal = 8.dp, vertical = 4.dp), // 调整边距更统一
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), // 降低高度提高精致感
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧订单号显示圆形标识
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        color = getStatusColor(order.status).copy(alpha = 0.1f),
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = getStatusColor(order.status).copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = order.number.takeLast(2),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = getStatusColor(order.status)
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.order_number, order.number),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = order.customerName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 日期图标
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // 日期
                    val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    Text(
                        text = dateFormat.format(order.dateCreated),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 金额图标
                    Icon(
                        imageVector = Icons.Default.AttachMoney,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    
                                        // 金额                    Text(                        text = "$currencySymbol${order.total}",                        style = MaterialTheme.typography.bodyMedium,                        fontWeight = FontWeight.Bold,                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 右侧价格和状态显示
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // 订单状态
                val statusText = when(order.status) {
                    "completed" -> stringResource(R.string.order_status_completed)
                    "processing" -> stringResource(R.string.order_status_processing)
                    "pending" -> stringResource(R.string.order_status_pending)
                    "cancelled" -> stringResource(R.string.order_status_cancelled)
                    "refunded" -> stringResource(R.string.order_status_refunded)
                    "failed" -> stringResource(R.string.order_status_failed)
                    "on-hold" -> stringResource(R.string.order_status_on_hold)
                    else -> order.status // 使用实际状态而不是默认为pending
                }
                
                val statusColor = getStatusColor(order.status)
                
                // 状态显示 - 使用相同风格的Chip
                Box(
                    modifier = Modifier
                        .background(
                            color = statusColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp) // 使用更圆的角
                        )
                        .border(
                            width = 0.5.dp,
                            color = statusColor.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 添加状态图标
                        val statusIcon = when(order.status) {
                            "completed" -> Icons.Default.CheckCircle
                            "processing" -> Icons.Default.Schedule
                            "pending" -> Icons.Default.Schedule
                            "cancelled" -> Icons.Default.Close
                            else -> Icons.Default.Schedule
                        }
                        
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = statusColor
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp
                            ),
                            color = statusColor
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 打印状态
                val printStatusText = if (order.isPrinted) stringResource(R.string.printed_yes) else stringResource(R.string.printed_no)
                val printStatusIcon = if (order.isPrinted) Icons.Default.CheckCircle else Icons.Default.Print
                val printStatusColor = if (order.isPrinted) Color(0xFF4CAF50) else Color(0xFFE53935)
                
                Box(
                    modifier = Modifier
                        .background(
                            color = printStatusColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp) // 使用更圆的角
                        )
                        .border(
                            width = 0.5.dp,
                            color = printStatusColor.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = printStatusIcon,
                            contentDescription = printStatusText,
                            modifier = Modifier.size(12.dp),
                            tint = printStatusColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = printStatusText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp
                            ),
                            color = printStatusColor
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

@Composable
fun UnreadOrdersDialog(
    onDismiss: () -> Unit,
    onOrderClick: (Order) -> Unit,
    viewModel: OrdersViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    // 获取未读订单列表
    val unreadOrders by viewModel.unreadOrders.collectAsState()
    
    // 每次对话框打开时重新加载未读订单
    LaunchedEffect(key1 = Unit) {
        viewModel.refreshUnreadOrders()
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = stringResource(id = if (LocalAppLocale.current.language == "zh") R.string.unread_orders else R.string.unread_orders),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                // 未读订单数量统计
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            id = if (LocalAppLocale.current.language == "zh") R.string.unread_orders_count else R.string.unread_orders_count,
                            unreadOrders.size
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Text(
                        text = stringResource(id = if (LocalAppLocale.current.language == "zh") R.string.mark_all_as_read else R.string.mark_all_as_read),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { 
                            viewModel.markAllOrdersAsRead()
                        }
                    )
                }
                
                // 未读订单列表
                if (unreadOrders.isEmpty()) {
                    // 显示空状态
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = stringResource(id = if (LocalAppLocale.current.language == "zh") R.string.no_unread_orders else R.string.no_unread_orders),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    // 显示未读订单列表
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(unreadOrders) { order ->
                            UnreadOrderItem(
                                order = order,
                                onClick = { 
                                    // 点击时标记为已读
                                    viewModel.markOrderAsRead(order.id)
                                    onOrderClick(order)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composablefun UnreadOrderItem(    order: Order,    onClick: () -> Unit,    currencySymbol: String = "C$") {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧红点标记未读状态
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 中间订单信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.order_number, order.number),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 状态标签
                    Box(
                        modifier = Modifier
                            .background(
                                getStatusColor(order.status).copy(alpha = 0.1f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when(order.status) {
                                "completed" -> stringResource(R.string.order_status_completed)
                                "processing" -> stringResource(R.string.order_status_processing)
                                "pending" -> stringResource(R.string.order_status_pending)
                                "cancelled" -> stringResource(R.string.order_status_cancelled)
                                else -> stringResource(R.string.order_status_pending)
                            },
                            color = getStatusColor(order.status),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 客户信息
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = order.customerName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    
                    if (order.contactInfo.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = order.contactInfo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 时间和价格
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    Text(
                        text = dateFormat.format(order.dateCreated),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Icon(
                        imageVector = Icons.Default.AttachMoney,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                                        Text(                        text = "$currencySymbol${order.total}",                        style = MaterialTheme.typography.bodyMedium,                        fontWeight = FontWeight.Bold,                        color = MaterialTheme.colorScheme.primary                    )
                }
                
                // 备注信息（如果有）
                if (order.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TextSnippet,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = order.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 右侧箭头
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
} 