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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextSnippet
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
import com.example.wooauto.presentation.theme.WooAutoTheme
import com.example.wooauto.utils.LocaleHelper
import com.example.wooauto.presentation.screens.settings.SettingsViewModel
import com.google.gson.Gson
import kotlinx.coroutines.delay
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
import com.example.wooauto.domain.templates.TemplateType
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Discount
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ChevronRight

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
    
    // 新增状态，用于控制何时显示UI
    val isInitialized = remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // 修改API检查状态逻辑
    var showApiConfigDialog by remember { mutableStateOf(false) }
    // 不再使用api检查延迟
    // var apiCheckDelayCompleted by remember { mutableStateOf(false) }
    
    var searchQuery by remember { mutableStateOf("") }
    var showOrderDetail by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf("") }
    var showUnreadOrders by remember { mutableStateOf(false) }
    
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
            showApiConfigDialog = true
            // 虽然未配置API，但仍然可以展示UI
            isInitialized.value = true
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
            context.registerReceiver(receiver, intentFilter)
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
                showApiConfigDialog = false
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
            showApiConfigDialog = true
        } else {
            // 已经加载了数据或API已配置或有错误消息，不显示配置对话框
            showApiConfigDialog = false
        }
    }
    
    // 订单页面每次成为活动页面时刷新API配置状态
    LaunchedEffect(navController) {
        // 使用navController的addOnDestinationChangedListener代替currentBackStackEntryAsState
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val currentRoute = destination.route
            Log.d("OrdersScreen", "当前导航到页面: $currentRoute")
            if (currentRoute == NavigationItem.Orders.route) {
                Log.d("OrdersScreen", "导航回订单页面，刷新订单数据")
                viewModel.refreshOrders()
            }
        }
    }
    
    // 添加对导航变化的监听，确保对话框状态正确
    LaunchedEffect(Unit) {
        val callback = { route: String? -> 
            // 当导航回到这个页面时，检查API是否已配置，并相应更新对话框状态
            Log.d("OrdersScreen", "导航状态变化: $route")
            if (route == NavigationItem.Orders.route) {
                if (isConfigured) {
                    showApiConfigDialog = false
                    Log.d("OrdersScreen", "导航返回订单页面，API已配置，隐藏对话框")
                } else {
                    Log.d("OrdersScreen", "导航返回订单页面，API未配置，检查是否需要显示对话框")
                    // 这里不再调用私有方法checkApiConfiguration，而是刷新订单
                    viewModel.refreshOrders()
                }
            }
        }
        try {
            navController.currentBackStackEntryFlow.collect {
                callback(it.destination.route)
            }
        } catch (e: Exception) {
            Log.e("OrdersScreen", "监听导航变化出错", e)
        }
    }
    
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
        topBar = {
            TopBar(
                isRefreshing = isRefreshing,
                showUnreadOrders = showUnreadOrders,
                onToggleUnreadOrders = { showUnreadOrders = !showUnreadOrders },
                onRefresh = { viewModel.refreshOrders() }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
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
                // 没有订单且API未配置
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "请先配置WooCommerce API",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            navController.navigate("website_settings")
                        }
                    ) {
                        Text("前往配置")
                    }
                }
            } else if (orders.isEmpty()) {
                // 已配置但没有订单
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "没有订单数据",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.refreshOrders() }
                    ) {
                        Text("刷新")
                    }
                }
            } else {
                // 有订单数据，显示订单列表
                OrdersList(
                    orders = orders,
                    showUnreadOnly = showUnreadOrders,
                    selectedStatus = statusFilter,
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
            
            // 其余UI部分保持不变
            // ... existing code ...
        }
    }
}

/**
 * 订单列表顶部栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    isRefreshing: Boolean,
    showUnreadOrders: Boolean,
    onToggleUnreadOrders: () -> Unit,
    onRefresh: () -> Unit
) {
    val locale = LocalAppLocale.current
    
    TopAppBar(
        title = { Text(text = if (locale.language == "zh") "订单" else "Orders") },
        actions = {
            // 未读订单按钮
            IconButton(
                onClick = onToggleUnreadOrders
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = if (locale.language == "zh") "未读订单" else "Unread Orders"
                )
            }
            
            // 刷新按钮
            IconButton(
                onClick = onRefresh,
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
                        contentDescription = if (locale.language == "zh") "刷新" else "Refresh"
                    )
                }
            }
        }
    )
}

/**
 * 订单列表组件
 */
@Composable
private fun OrdersList(
    orders: List<Order>,
    showUnreadOnly: Boolean,
    selectedStatus: String,
    onSelectOrder: (Order) -> Unit,
    onStatusSelected: (String) -> Unit
) {
    val locale = LocalAppLocale.current
    var searchQuery by remember { mutableStateOf("") }
    
    // 定义状态选项列表
    val statusOptions = listOf(
        "" to (if (locale.language == "zh") "全部" else "All"),
        "processing" to (if (locale.language == "zh") "处理中" else "Processing"),
        "completed" to (if (locale.language == "zh") "已完成" else "Completed"),
        "pending" to (if (locale.language == "zh") "待付款" else "Pending"),
        "cancelled" to (if (locale.language == "zh") "已取消" else "Cancelled"),
        "on-hold" to (if (locale.language == "zh") "暂挂" else "On Hold")
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        // 搜索栏
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
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
                        onClick = { searchQuery = "" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = if (locale.language == "zh") "清除" else "Clear"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
        
        // 状态过滤器
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(statusOptions) { statusOption ->
                val (status, label) = statusOption  // 正确的解构语法
                FilterChip(
                    selected = selectedStatus == status,
                    onClick = { onStatusSelected(status) },
                    label = { Text(text = label) }  // 明确指定text参数
                )
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
            // 没有匹配的订单
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (locale.language == "zh") "未找到匹配的订单" else "No matching orders found",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // 显示订单列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                .padding(14.dp),  // 增加内边距
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)  // 增大尺寸
                    .background(
                        color = getStatusColor(order.status).copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = order.number.takeLast(2),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 22.sp  // 增大字体尺寸
                    ),
                    color = getStatusColor(order.status)
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))  // 增加间距
            
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
                        modifier = Modifier.size(14.dp),  // 保持小图标尺寸
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
                        modifier = Modifier.size(14.dp),  // 保持小图标尺寸
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
                        modifier = Modifier.size(14.dp),  // 保持小图标尺寸
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // 金额
                    Text(
                        text = "¥${order.total}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 右侧价格和状态显示
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // 订单状态
                Box(
                    modifier = Modifier
                        .background(
                            color = getStatusColor(order.status).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(
                            when(order.status) {
                                "completed" -> R.string.order_status_completed
                                "processing" -> R.string.order_status_processing
                                "pending" -> R.string.order_status_pending
                                "cancelled" -> R.string.order_status_cancelled
                                else -> R.string.order_status_pending
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp  // 调整字体尺寸
                        ),
                        color = getStatusColor(order.status)
                    )
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
                            shape = RoundedCornerShape(4.dp)
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
                            modifier = Modifier.size(14.dp),
                            tint = printStatusColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = printStatusText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 14.sp  // 增大字体尺寸
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
    
    // 观察当前选中的订单，以便实时更新UI
    val currentOrder by viewModel.selectedOrder.collectAsState()
    
    // 使用当前的订单信息（如果有更新）或者传入的订单
    val displayOrder = currentOrder ?: order
    
    // 定义打印状态相关变量
    val printStatusText = if (displayOrder.isPrinted) stringResource(R.string.printed_yes) else stringResource(R.string.printed_no)
    val printStatusColor = if (displayOrder.isPrinted) Color(0xFF4CAF50) else Color(0xFFE53935)
    
    Log.d("OrderDetailDialog", "显示订单详情，订单ID: ${displayOrder.id}, 打印状态: ${displayOrder.isPrinted}")
    
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
                                text = stringResource(R.string.order_details) + " #${displayOrder.number}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.close)
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
                    // 使用 order_number 字符串资源
                    OrderDetailRow(
                        label = stringResource(R.string.order_number).substringBefore(":"), 
                        value = displayOrder.number,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = stringResource(R.string.order_number),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    OrderDetailRow(
                        label = stringResource(R.string.order_id).substringBefore(":"), 
                        value = displayOrder.id.toString(),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Tag,
                                contentDescription = stringResource(R.string.order_id),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val formattedDate = dateFormat.format(displayOrder.dateCreated)
                    OrderDetailRow(
                        label = stringResource(R.string.order_date).substringBefore(":"),
                        value = formattedDate,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = stringResource(R.string.order_date),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    OrderDetailRow(
                        label = stringResource(R.string.customer_name).substringBefore(":"),
                        value = displayOrder.customerName,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = stringResource(R.string.customer_name),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    // 手机号
                    OrderDetailRow(
                        label = stringResource(R.string.contact_info).substringBefore(":"),
                        value = displayOrder.contactInfo.ifEmpty { stringResource(R.string.not_provided) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = stringResource(R.string.contact_info),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    // 打印状态显示
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.printed_status),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.width(80.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Icon(
                            imageVector = if (displayOrder.isPrinted) Icons.Default.CheckCircle else Icons.Default.Print,
                            contentDescription = stringResource(R.string.printed_status),
                            modifier = Modifier.size(16.dp),
                            tint = printStatusColor
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = printStatusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = printStatusColor
                        )
                        
                        if (!displayOrder.isPrinted) {
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            OutlinedButton(
                                onClick = { onMarkAsPrinted(displayOrder.id) },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.mark_as_printed),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    
                    if (displayOrder.billingInfo.isNotEmpty()) {
                        OrderDetailRow(
                            label = stringResource(R.string.billing_address),
                            value = displayOrder.billingInfo,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = stringResource(R.string.billing_address),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    
                    // 显示订单备注信息
                    if (displayOrder.notes.isNotEmpty()) {
                        OrderDetailRow(
                            label = stringResource(R.string.order_note),
                            value = displayOrder.notes,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.TextSnippet,
                                    contentDescription = stringResource(R.string.order_note),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    
                    // 显示WooFood信息（如果有）
                    displayOrder.woofoodInfo?.let { wooFoodInfo ->
                        // 添加一个显眼的订单方式标签
                        val orderMethodColor = if (wooFoodInfo.isDelivery) {
                            Color(0xFF2196F3) // 蓝色用于外卖
                        } else {
                            Color(0xFF4CAF50) // 绿色用于自取
                        }
                        
                        val orderMethodText = if (wooFoodInfo.isDelivery) 
                            stringResource(R.string.delivery_order) 
                        else 
                            stringResource(R.string.pickup_order)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = orderMethodColor.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (wooFoodInfo.isDelivery) 
                                            Icons.Default.LocalShipping 
                                        else 
                                            Icons.Default.Store,
                                        contentDescription = orderMethodText,
                                        modifier = Modifier.size(18.dp),
                                        tint = orderMethodColor
                                    )
                                    
                                    Spacer(modifier = Modifier.width(6.dp))
                                    
                                    Text(
                                        text = orderMethodText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = orderMethodColor
                                    )
                                }
                            }
                        }
                        
                        // 显示地址或者自取信息
                        if (wooFoodInfo.isDelivery) {
                            wooFoodInfo.deliveryAddress?.let { address ->
                                OrderDetailRow(
                                    label = stringResource(R.string.delivery_address),
                                    value = address,
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = stringResource(R.string.delivery_address),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        } else {
                            OrderDetailRow(
                                label = stringResource(R.string.pickup_location),
                                value = displayOrder.billingInfo.split("\n").firstOrNull() ?: stringResource(R.string.in_store_pickup),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = stringResource(R.string.pickup_location),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                        
                        // 统一显示预计时间（配送/自取）
                        wooFoodInfo.deliveryTime?.let { time ->
                            OrderDetailRow(
                                label = if (wooFoodInfo.isDelivery) stringResource(R.string.delivery_time) else stringResource(R.string.pickup_time),
                                value = time,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = if (wooFoodInfo.isDelivery) stringResource(R.string.delivery_time) else stringResource(R.string.pickup_time),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                    }
                    
                    // 显示订单商品列表
                    Text(
                        text = stringResource(R.string.product_list),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                    
                    if (displayOrder.items.isNotEmpty()) {
                        OrderItemsList(items = displayOrder.items)
                    } else {
                        Text(
                            text = stringResource(R.string.no_product_info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    // 显示价格明细
                    Text(
                        text = stringResource(R.string.price_details),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                    
                    OrderDetailRow(
                        label = stringResource(R.string.subtotal),
                        value = "¥${displayOrder.subtotal}",
                        icon = {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = stringResource(R.string.subtotal),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    if (displayOrder.discountTotal.isNotEmpty() && displayOrder.discountTotal != "0.00") {
                        OrderDetailRow(
                            label = stringResource(R.string.discount),
                            value = "- ¥${displayOrder.discountTotal}",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.AttachMoney,
                                    contentDescription = stringResource(R.string.discount),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    
                    // 是否是外卖订单
                    val isDelivery = displayOrder.woofoodInfo?.isDelivery ?: false
                    
                    // 记录所有费用行，方便调试
                    Log.d("OrderDetailDialog", "【UI查找前】订单#${displayOrder.number} 费用行数量: ${displayOrder.feeLines.size}")
                    displayOrder.feeLines.forEach { feeLine ->
                        Log.d("OrderDetailDialog", "【UI查找前】费用行: '${feeLine.name}' = ${feeLine.total}")
                    }
                    
                    // 更全面的配送费名称匹配
                    val deliveryFeeLine = displayOrder.feeLines.find { 
                        it.name.equals("Shipping fee", ignoreCase = true) ||
                        it.name.equals("shipping fee", ignoreCase = true) ||
                        it.name.equals("SHIPPING FEE", ignoreCase = true) ||
                        it.name.contains("配送费", ignoreCase = true) || 
                        it.name.contains("外卖费", ignoreCase = true) ||
                        it.name.contains("delivery", ignoreCase = true) ||
                        it.name.contains("shipping", ignoreCase = true) ||
                        it.name.contains("运费", ignoreCase = true)
                    }
                    
                    // 更全面的小费名称匹配
                    val tipLine = displayOrder.feeLines.find { 
                        it.name.equals("Show Your Appreciation", ignoreCase = true) ||
                        it.name.contains("小费", ignoreCase = true) || 
                        it.name.contains("tip", ignoreCase = true) ||
                        it.name.contains("gratuity", ignoreCase = true) ||
                        it.name.contains("appreciation", ignoreCase = true)
                    }
                    
                    // 记录匹配结果
                    Log.d("OrderDetailDialog", "【UI匹配结果】配送费行: ${deliveryFeeLine?.name ?: "未找到"}, 金额: ${deliveryFeeLine?.total ?: "0.00"}")
                    Log.d("OrderDetailDialog", "【UI匹配结果】小费行: ${tipLine?.name ?: "未找到"}, 金额: ${tipLine?.total ?: "0.00"}")
                    
                    // 获取配送费和小费的值
                    var deliveryFee = "0.00"
                    var tip = "0.00"
                    
                    // 首先尝试从feeLines直接获取配送费
                    if (deliveryFeeLine != null) {
                        deliveryFee = deliveryFeeLine.total
                        Log.d("OrderDetailDialog", "【UI】从feeLines获取配送费: $deliveryFee (${deliveryFeeLine.name})")
                    }
                    
                    // 首先尝试从feeLines直接获取小费
                    if (tipLine != null) {
                        tip = tipLine.total
                        Log.d("OrderDetailDialog", "【UI】从feeLines获取小费: $tip (${tipLine.name})")
                    }
                    
                    // 如果feeLines中没有找到配送费，但woofoodInfo中有，使用woofoodInfo中的值
                    if (deliveryFee == "0.00" && displayOrder.woofoodInfo?.deliveryFee != null && displayOrder.woofoodInfo.deliveryFee != "0.00") {
                        deliveryFee = displayOrder.woofoodInfo.deliveryFee
                        Log.d("OrderDetailDialog", "【UI】从woofoodInfo获取配送费: $deliveryFee")
                    }
                    
                    // 如果feeLines中没有找到小费，但woofoodInfo中有，使用woofoodInfo中的值
                    if (tip == "0.00" && displayOrder.woofoodInfo?.tip != null && displayOrder.woofoodInfo.tip != "0.00") {
                        tip = displayOrder.woofoodInfo.tip
                        Log.d("OrderDetailDialog", "【UI】从woofoodInfo获取小费: $tip")
                    }
                    
                    // 添加详细日志，显示订单详情对话框中的数据状态
                    Log.d("OrderDetailDialog", "【UI最终数据】订单#${displayOrder.number} - 是否外卖: $isDelivery, 最终配送费: $deliveryFee, 最终小费: $tip")
                    Log.d("OrderDetailDialog", "【UI数据源】woofoodInfo值 - 配送费: ${displayOrder.woofoodInfo?.deliveryFee}, 小费: ${displayOrder.woofoodInfo?.tip}")
                    
                    // 如果是外卖订单，始终显示配送费行（即使金额为0）
                    if (isDelivery) {
                        OrderDetailRow(
                            label = stringResource(R.string.delivery_fee),
                            value = "¥$deliveryFee",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    
                    // 始终显示小费行（只要不是0.00或空字符串）
                    if (tip != "0.00" && tip.isNotEmpty()) {
                        OrderDetailRow(
                            label = stringResource(R.string.tip_amount),
                            value = "¥$tip",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.AttachMoney,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    
                    // 显示其他额外费用（不是配送费或小费的其他费用）
                    displayOrder.feeLines.forEach { feeLine ->
                        // 排除已经显示过的配送费和小费
                        val isDeliveryFee = deliveryFeeLine?.id == feeLine.id || 
                                          feeLine.name.equals("Shipping fee", ignoreCase = true) ||
                                          feeLine.name.equals("shipping fee", ignoreCase = true) ||
                                          feeLine.name.equals("SHIPPING FEE", ignoreCase = true) ||
                                          feeLine.name.contains("配送费", ignoreCase = true) || 
                                          feeLine.name.contains("外卖费", ignoreCase = true) ||
                                          feeLine.name.contains("shipping", ignoreCase = true) || 
                                          feeLine.name.contains("delivery", ignoreCase = true)
                        
                        val isTip = tipLine?.id == feeLine.id ||
                                  feeLine.name.equals("Show Your Appreciation", ignoreCase = true) ||
                                  feeLine.name.contains("小费", ignoreCase = true) || 
                                  feeLine.name.contains("tip", ignoreCase = true) || 
                                  feeLine.name.contains("gratuity", ignoreCase = true) ||
                                  feeLine.name.contains("appreciation", ignoreCase = true)
                        
                        if (!isDeliveryFee && !isTip) {
                            OrderDetailRow(
                                label = feeLine.name,
                                value = "¥${feeLine.total}",
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.AttachMoney,
                                        contentDescription = feeLine.name,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                    }
                    
                    // 替换原来的税费显示为PST和GST分开显示
                    var hasPST = false
                    var hasGST = false
                    
                    // 记录一下所有税费行，方便调试
                    if (displayOrder.taxLines.isNotEmpty()) {
                        Log.d("OrderDetailDialog", "税费行数量: ${displayOrder.taxLines.size}")
                        displayOrder.taxLines.forEach { taxLine ->
                            Log.d("OrderDetailDialog", "税费: ${taxLine.label} (${taxLine.ratePercent}%) = ¥${taxLine.taxTotal}")
                        }
                    } else {
                        Log.d("OrderDetailDialog", "无税费行信息")
                    }
                    
                    // 遍历税费行，分别显示PST和GST
                    displayOrder.taxLines.forEach { taxLine ->
                        when {
                            taxLine.label.contains("PST", ignoreCase = true) -> {
                                hasPST = true
                                OrderDetailRow(
                                    label = stringResource(R.string.tax_pst, taxLine.ratePercent.toString()),
                                    value = "¥${taxLine.taxTotal}",
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalance,
                                            contentDescription = "PST税",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                            taxLine.label.contains("GST", ignoreCase = true) -> {
                                hasGST = true
                                OrderDetailRow(
                                    label = stringResource(R.string.tax_gst, taxLine.ratePercent.toString()),
                                    value = "¥${taxLine.taxTotal}",
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalance,
                                            contentDescription = "GST税",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                            else -> {
                                // 其他税费显示
                                OrderDetailRow(
                                    label = "${taxLine.label} (${taxLine.ratePercent}%)",
                                    value = "¥${taxLine.taxTotal}",
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = stringResource(R.string.tax),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        }
                    }
                    
                    // 如果没有具体税费行，但有总税费，显示总税费
                    if (displayOrder.taxLines.isEmpty() && displayOrder.totalTax != "0.00" && displayOrder.totalTax.isNotEmpty()) {
                        OrderDetailRow(
                            label = stringResource(R.string.tax),
                            value = "¥${displayOrder.totalTax}",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = stringResource(R.string.tax),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // 显示总计
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.total),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "¥${displayOrder.total}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // 显示订单状态
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.status) + ": ",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        val statusText = when(displayOrder.status) {
                            "processing" -> stringResource(R.string.order_status_processing)
                            "pending" -> stringResource(R.string.order_status_pending)
                            "on-hold" -> stringResource(R.string.order_status_on_hold)
                            "completed" -> stringResource(R.string.order_status_completed)
                            "cancelled" -> stringResource(R.string.order_status_cancelled)
                            "refunded" -> stringResource(R.string.order_status_refunded)
                            "failed" -> stringResource(R.string.order_status_failed)
                            else -> displayOrder.status
                        }
                        
                        val statusColor = when(displayOrder.status) {
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
                        onClick = { showTemplateOptions = true }
                    ) {
                        Text(if (displayOrder.isPrinted) stringResource(R.string.reprint) else stringResource(R.string.print_order))
                    }
                    
                    // 关闭按钮
                    Button(
                        onClick = onDismiss
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
    
    // 显示状态选择对话框
    if (showStatusOptions) {
        StatusChangeDialog(
            currentStatus = displayOrder.status,
            onDismiss = { showStatusOptions = false },
            onStatusSelected = { newStatus ->
                onStatusChange(displayOrder.id, newStatus)
                showStatusOptions = false
            }
        )
    }
    
    // 添加模板选择对话框
    if (showTemplateOptions) {
        TemplateSelectorDialog(
            onDismiss = { showTemplateOptions = false },
            onTemplateSelected = { templateType ->
                // 使用选定的模板打印
                viewModel.printOrder(displayOrder.id, templateType)
                showTemplateOptions = false
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
        "processing" to stringResource(R.string.order_status_processing),
        "pending" to stringResource(R.string.order_status_pending),
        "on-hold" to stringResource(R.string.order_status_on_hold),
        "completed" to stringResource(R.string.order_status_completed),
        "cancelled" to stringResource(R.string.order_status_cancelled),
        "refunded" to stringResource(R.string.order_status_refunded),
        "failed" to stringResource(R.string.order_status_failed)
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
                    text = stringResource(R.string.change_order_status),
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
                                    contentDescription = null,
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
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
fun TemplateSelectorDialog(
    onDismiss: () -> Unit,
    onTemplateSelected: (TemplateType) -> Unit
) {
    val templateOptions = listOf(
        Pair(TemplateType.FULL_DETAILS, stringResource(R.string.full_details_template)),
        Pair(TemplateType.DELIVERY, stringResource(R.string.delivery_template)),
        Pair(TemplateType.KITCHEN, stringResource(R.string.kitchen_template))
    )
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_print_template),
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                templateOptions.forEach { (type, description) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onTemplateSelected(type)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when(type) {
                                TemplateType.FULL_DETAILS -> Icons.Default.Article
                                TemplateType.DELIVERY -> Icons.Default.LocalShipping
                                TemplateType.KITCHEN -> Icons.Default.Restaurant
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    
                    if (type != TemplateType.KITCHEN) {
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
fun OrderItemsList(items: List<OrderItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        items.forEach { item ->
            OrderItemRow(item)
            if (items.indexOf(item) < items.size - 1) {
                Divider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            }
        }
    }
}

@Composable
fun OrderItemRow(item: OrderItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 如果有商品图片，显示图片
        if (item.image.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.image)
                    .crossfade(true)
                    .build(),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
        Spacer(modifier = Modifier.width(8.dp))  // 增加间距
        
        // 商品信息（名称、选项、数量）
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            
            // 显示商品选项
            if (item.options.isNotEmpty()) {
                Text(
                    text = item.options.joinToString(", ") { "${it.name}: ${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        
        // 数量
        Text(
            text = "x${item.quantity}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        
        // 价格
        Text(
            text = "¥${item.total}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
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

@Composable
fun UnreadOrderItem(
    order: Order,
    onClick: () -> Unit
) {
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
                    
                    Text(
                        text = "¥${order.total}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // 备注信息（如果有）
                if (order.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextSnippet,
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