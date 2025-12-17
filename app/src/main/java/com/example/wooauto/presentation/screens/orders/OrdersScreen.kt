package com.example.wooauto.presentation.screens.orders

import android.content.Intent
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
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.wooauto.domain.models.Order
import com.example.wooauto.navigation.NavigationItem
import kotlinx.coroutines.launch
import java.util.Locale
import com.example.wooauto.R
import com.example.wooauto.utils.LocalAppLocale
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import com.example.wooauto.presentation.screens.products.UnconfiguredView
import com.example.wooauto.presentation.EventBus
import com.example.wooauto.utils.UiLog

@Composable
fun StatusDropdown(
	selectedStatus: String,
	options: List<Pair<String, String>>,
	onChange: (String) -> Unit
) {
	var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selectedStatus } ?: ("" to stringResource(id = com.example.wooauto.R.string.all_status))
	Box(
		modifier = Modifier
			.clip(RoundedCornerShape(16.dp))
			.background(MaterialTheme.colorScheme.surface)
			.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
			.clickable { expanded = true }
			.padding(horizontal = 10.dp, vertical = 8.dp)
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Icon(imageVector = Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
			Spacer(modifier = Modifier.size(6.dp))
			Text(text = current.second, style = MaterialTheme.typography.bodyMedium)
			Spacer(modifier = Modifier.size(4.dp))
			Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			options.forEach { (value, label) ->
				DropdownMenuItem(text = { Text(text = label) }, onClick = {
					expanded = false
					onChange(value)
				})
			}
		}
	}
}

@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = hiltViewModel(),
    navController: NavController = rememberNavController()
) {
    UiLog.d("OrdersScreen", "订单屏幕初始化")
    
    // 获取当前路由
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "unknown"
    UiLog.d("OrdersScreen", "当前路由: $currentRoute")
    
    // 获取当前语言环境
    val locale = LocalAppLocale.current
    val context = LocalContext.current
    
    // 提前获取需要使用的字符串资源
    val apiNotConfiguredMessage = stringResource(R.string.api_notification_not_configured)
    val ordersTitle = stringResource(id = R.string.orders)
    val searchOrdersPlaceholder = stringResource(id = R.string.search_orders_hint)
    // 读取 orders/{section}，用于控制默认状态和过滤条显示
    val ordersSection = remember(currentRoute) {
        if (currentRoute.startsWith("orders/")) currentRoute.removePrefix("orders/") else "history"
    }
    val errorApiNotConfigured = stringResource(R.string.error_api_not_configured)
    
    val isConfigured by viewModel.isConfigured.collectAsState()
    val configChecked by viewModel.configChecked.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedOrder by viewModel.selectedOrder.collectAsState()
    val isRefreshing by viewModel.refreshing.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val emptyGuardActive by viewModel.emptyGuardActive.collectAsState()
    
    // 新增状态，用于控制何时显示UI
    val isInitialized = remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { ev ->
            val statusLabel = when (ev.newStatus) {
                "processing" -> context.getString(R.string.order_status_processing)
                "completed" -> context.getString(R.string.order_status_completed)
                "pending" -> context.getString(R.string.order_status_pending)
                "on-hold" -> context.getString(R.string.order_status_on_hold)
                "cancelled" -> context.getString(R.string.order_status_cancelled)
                "refunded" -> context.getString(R.string.order_status_refunded)
                "failed" -> context.getString(R.string.order_status_failed)
                else -> ev.newStatus
            }
            val msg = "#${ev.orderNumber} → ${statusLabel}"
            snackbarHostState.showSnackbar(msg)
        }
    }
    val coroutineScope = rememberCoroutineScope()
    
    // 搜索相关状态
    var searchQuery by remember { mutableStateOf("") }
    // 最近天数筛选（null 表示全部），默认改为1天
    var lastDaysFilter by remember(ordersSection) { mutableStateOf<Int?>(1) }
    var showOrderDetail by remember { mutableStateOf(false) }
    var showUnreadDialog by remember { mutableStateOf(false) }
    // 与 ViewModel 的 currentStatusFilter 双向保持一致
    val vmStatus by viewModel.currentStatusFilter.collectAsState()
    var statusFilter by remember { mutableStateOf(vmStatus ?: "") }
    LaunchedEffect(vmStatus) {
        // 当VM内的筛选状态变化时，更新UI本地状态，保持选中Chip正确高亮
        statusFilter = vmStatus ?: ""
    }
    
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
                    UiLog.d("OrdersScreen", "收到搜索事件：${event.query}")
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
                    UiLog.d("OrdersScreen", "收到刷新事件")
                    viewModel.refreshOrders()
                }
            }
        }
        
        // 清理协程
        onDispose {
            searchJob.cancel()
            refreshJob.cancel()
            UiLog.d("OrdersScreen", "清理事件订阅协程")
        }
    }
    
    // 监听selectedOrder的变化，自动显示订单详情对话框
    LaunchedEffect(selectedOrder?.id) {
        UiLog.d("OrdersScreen", "LaunchedEffect触发 - selectedOrder?.id: ${selectedOrder?.id}, showOrderDetail: $showOrderDetail")
        selectedOrder?.let { order ->
            UiLog.d("OrdersScreen", "selectedOrder不为空: ${order.id}, 当前showOrderDetail: $showOrderDetail")
            if (!showOrderDetail) {
                UiLog.d("OrdersScreen", "检测到selectedOrder变化，显示订单详情对话框: ${order.id}")
                showOrderDetail = true
                UiLog.d("OrdersScreen", "已设置showOrderDetail = true")
            } else {
                UiLog.d("OrdersScreen", "showOrderDetail已经为true，跳过设置")
            }
        } ?: run {
            UiLog.d("OrdersScreen", "selectedOrder为null")
        }
    }
    
    // 添加对selectedOrder的监听日志
    LaunchedEffect(selectedOrder) {
        UiLog.d("OrdersScreen", "selectedOrder状态变化: ${selectedOrder?.let { "订单ID=${it.id}, 订单号=${it.number}" } ?: "null"}")
    }
    
    // 当进入此屏幕时执行初始化操作 - 简化逻辑，减少重复调用
    LaunchedEffect(key1 = Unit) {
        UiLog.d("OrdersScreen", "LaunchedEffect 触发，开始初始化流程")
        
        // 标记初始化完成，避免其他LaunchedEffect重复操作
        isInitialized.value = true
        
        // 直接调用checkApiConfiguration获取配置状态
        val configResult = viewModel.checkApiConfiguration()
        UiLog.d("OrdersScreen", "API配置检查结果: $configResult")
        
        if (!configResult) {
            // API未配置时显示提示
            UiLog.d("OrdersScreen", "API未配置，显示提示信息")
            coroutineScope.launch {
                snackbarHostState.showSnackbar(apiNotConfiguredMessage)
            }
        } else {
            // API已配置，history 默认显示全部状态
            val initialStatus = if (ordersSection == "history") "" else statusFilter
            UiLog.d("OrdersScreen", "API已配置，按筛选状态加载: $initialStatus (section=$ordersSection)")
            viewModel.filterOrdersByStatus(initialStatus)
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
    
    // 简化的导航监听 - 只在真正需要时刷新
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val currentRoute = destination.route
            if (currentRoute == NavigationItem.Orders.route && isInitialized.value) {
                UiLog.d("OrdersScreen", "导航回订单页面，检查是否需要刷新")
                // 只有在订单列表为空时才刷新，避免不必要的API调用
                if (orders.isEmpty() && !isLoading) {
                    UiLog.d("OrdersScreen", "订单列表为空且未在加载，执行刷新")
                    viewModel.refreshOrders()
                }
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
            "processing" to "处理中",
            "pending" to "待付款",
            "on-hold" to "暂挂",
            "completed" to "已完成",
            "cancelled" to "已取消",
            "refunded" to "已退款",
            "failed" to "失败",
            "" to "全部订单"
        )
    } else {
        listOf(
            "processing" to "Processing",
            "pending" to "Pending",
            "on-hold" to "On Hold",
            "completed" to "Completed",
            "cancelled" to "Cancelled",
            "refunded" to "Refunded",
            "failed" to "Failed",
            "" to "All Orders"
        )
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        // 获取系统状态栏和TopBar的组合高度
        val topPadding = paddingValues.calculateTopPadding()
        UiLog.d("OrdersScreen", "TopBar和状态栏总高度：$topPadding")
        
        // 打印完整的内边距信息用于调试
        UiLog.d("OrdersScreen", "使用Scaffold提供的内边距")
        
        // 使用动态调整后的内边距
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            // 顶部筛选区域（固定不刷新）
            OrdersFiltersBar(
                selectedStatus = statusFilter,
                onStatusSelected = { status ->
                    if (status.isEmpty()) viewModel.filterOrdersByStatus("") else viewModel.filterOrdersByStatus(status)
                },
                lastDays = lastDaysFilter,
                onLastDaysChange = { lastDaysFilter = it },
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it }
            )
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp), thickness = 1.dp)

            // 内容区域（仅此区域显示加载/空态/列表）
            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                when {
                    !isInitialized.value || !configChecked || isLoading -> {
                        UiLog.d("OrdersScreen", "Loading gating -> isInitialized=${isInitialized.value}, configChecked=$configChecked, isLoading=$isLoading, ordersSize=${orders.size}")
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(text = if (locale.language == "zh") "正在加载订单数据..." else "Loading orders...", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    orders.isEmpty() && !isConfigured && configChecked -> {
                        UnconfiguredView(
                            errorMessage = errorMessage ?: errorApiNotConfigured,
                            onSettingsClick = {
                                navController.navigate(NavigationItem.Settings.route) { launchSingleTop = true }
                                val intent = Intent("com.example.wooauto.ACTION_OPEN_API_SETTINGS")
                                context.sendBroadcast(intent)
                            }
                        )
                    }
                    else -> {
                        // Active 批量操作：当在 processing 视图且满足数量条件时显示
                        val newCount = if (statusFilter == "processing") orders.count { it.status == "processing" && !it.isRead } else 0
                        val inProcCount = if (statusFilter == "processing") orders.count { it.status == "processing" && it.isRead } else 0
                        var confirmAction by remember { mutableStateOf<String?>(null) }
                        if (statusFilter == "processing" && (newCount >= 2 || inProcCount >= 2)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { confirmAction = "start_processing" },
                                    enabled = newCount >= 2
                                ) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = if (locale.language == "zh") "一键开始处理(新)" else "Start all (new)")
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Button(
                                    onClick = { confirmAction = "complete" },
                                    enabled = inProcCount >= 2,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = if (locale.language == "zh") "一键完成(处理中)" else "Mark completed (processing)")
                                }
                            }
                            if (confirmAction != null) {
                                Dialog(
                                    onDismissRequest = { confirmAction = null },
                                    properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
                                ) {
                                    Card(shape = RoundedCornerShape(16.dp)) {
                                        Column(modifier = Modifier.padding(20.dp)) {
                                            val isStart = confirmAction == "start_processing"
                                            Text(
                                                text = if (isStart) {
                                                    if (locale.language == "zh") "确认将所有新订单设为处理中？（数量≥2）" else "Confirm to start all new orders as processing?"
                                                } else {
                                                    if (locale.language == "zh") "确认将所有处理中订单标记为已完成？（数量≥2）" else "Confirm to mark all processing orders as completed?"
                                                },
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                                OutlinedButton(onClick = { confirmAction = null }) {
                                                    Text(text = if (locale.language == "zh") "取消" else "Cancel")
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Button(onClick = {
                                                    if (confirmAction == "start_processing") {
                                                        viewModel.batchStartProcessingForNewOrders()
                                                    } else {
                                                        viewModel.batchCompleteProcessingOrders()
                                                    }
                                                    confirmAction = null
                                                }) {
                                                    Text(text = if (locale.language == "zh") "确认" else "Confirm")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OrdersList(
                            orders = orders,
                            selectedStatus = statusFilter,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            lastDays = lastDaysFilter,
                            onLastDaysChange = { lastDaysFilter = it },
                            onSelectOrder = { order ->
                                // 发送全局事件打开订单详情
                                kotlinx.coroutines.GlobalScope.launch {
                                    EventBus.emitOpenOrderDetail(order, DetailMode.AUTO)
                                }
                            },
                            onStatusSelected = { status ->
                                if (status.isEmpty()) viewModel.filterOrdersByStatus("") else viewModel.filterOrdersByStatus(status)
                            },
                            currencySymbol = currencySymbol,
                            isLoading = isLoading,
                            emptyGuardActive = emptyGuardActive
                        )

                        if (isRefreshing && orders.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = if (locale.language == "zh") "正在刷新订单..." else "Refreshing orders...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

            // 订单详情对话框 - 已移至 MainActivity 处理
            // if (showOrderDetail && selectedOrder != null) { ... }
            
            // 未读订单对话框
            if (showUnreadDialog) {
                UnreadOrdersDialog(
                    onDismiss = { showUnreadDialog = false }
                )
            }
        }
    }

@Composable
private fun OrdersFiltersBar(
    selectedStatus: String,
    onStatusSelected: (String) -> Unit,
    lastDays: Int?,
    onLastDaysChange: (Int?) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态
        val options = listOf(
            "" to stringResource(id = R.string.all_status),
            "processing" to stringResource(id = R.string.order_status_processing),
            "pending" to stringResource(id = R.string.order_status_pending),
            "on-hold" to stringResource(id = R.string.order_status_on_hold),
            "completed" to stringResource(id = R.string.order_status_completed),
            "cancelled" to stringResource(id = R.string.order_status_cancelled),
            "refunded" to stringResource(id = R.string.order_status_refunded),
            "failed" to stringResource(id = R.string.order_status_failed)
        )
        StatusDropdown(selectedStatus = selectedStatus, options = options) { value -> onStatusSelected(value) }

        Spacer(modifier = Modifier.width(12.dp))

        // 日期
        DateRangeDropdown(lastDays = lastDays, onChange = onLastDaysChange)

        Spacer(modifier = Modifier.width(12.dp))

        // 搜索（靠右）
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            SearchFieldInline(
                value = searchQuery,
                onChange = onSearchChange,
                placeholder = stringResource(id = R.string.search_orders_hint),
                widthDp = 360
            )
        }
    }
}

@Composable
private fun DateRangeDropdown(
    lastDays: Int?,
    onChange: (Int?) -> Unit
) {
    val options: List<Pair<Int?, String>> = listOf(
        1 to stringResource(id = R.string.last_1_day),
        7 to stringResource(id = R.string.last_7_days),
        30 to stringResource(id = R.string.last_30_days),
        90 to stringResource(id = R.string.last_90_days),
        null to stringResource(id = R.string.all_time)
    )
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == lastDays }?.second ?: options.first().second
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable { expanded = true }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, labelText) ->
                DropdownMenuItem(text = { Text(text = labelText) }, onClick = {
                    expanded = false
                    onChange(value)
                })
            }
        }
    }
}

@Composable
private fun SearchFieldInline(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    widthDp: Int = 420
) {
    val primary = MaterialTheme.colorScheme.primary
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { }),
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .width(widthDp.dp)
    ) { inner ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(text = placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            }
            if (value.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = { onChange("") }, modifier = Modifier.size(22.dp)) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = null, tint = primary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun InboxButton(
    unreadCount: Int,
    onClick: () -> Unit
) {
    Box {
        IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
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
    selectedStatus: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit = {},
    lastDays: Int? = null,
    onLastDaysChange: (Int?) -> Unit = {},
    onSelectOrder: (Order) -> Unit,
    onStatusSelected: (String) -> Unit,
    currencySymbol: String = "C$",
    isLoading: Boolean,
    emptyGuardActive: Boolean
) {
    val locale = LocalAppLocale.current
    
    // 定义状态选项列表 - 确保与API支持的值一致
    val statusOptions = listOf(
        "processing" to stringResource(id = R.string.order_status_processing),
        "completed" to stringResource(id = R.string.order_status_completed),
        "pending" to stringResource(id = R.string.order_status_pending),
        "cancelled" to stringResource(id = R.string.order_status_cancelled),
        "on-hold" to stringResource(id = R.string.order_status_on_hold),
        "refunded" to stringResource(id = R.string.order_status_refunded),
        "failed" to stringResource(id = R.string.order_status_failed),
        "" to stringResource(id = R.string.all_status)
    )

    // StatusDropdown 已提升为顶层函数
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 0.dp) // 移除底部padding
    ) {
        // 顶部已存在固定筛选栏，此处不再重复渲染筛选行

        // 原状态 Chip 保留为备选样式，但默认隐藏（避免重复UI）
        val showLegacyStatusChips = false
        if (showLegacyStatusChips) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
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
        }
        
        // 订单列表
        // 重要：列表不再按状态做本地过滤，避免与 ViewModel 的服务端/数据库筛选产生竞态导致空态闪烁。
        // 仅在客户端做搜索过滤；状态筛选完全交给 ViewModel。
        val nowMs = System.currentTimeMillis()
        val cutoffMs = lastDays?.let { nowMs - it * 24L * 60 * 60 * 1000 } // 天转毫秒
        val filteredOrders = orders.filter {
            val queryMatch = searchQuery.isEmpty() ||
                it.number.contains(searchQuery, ignoreCase = true) ||
                it.customerName.contains(searchQuery, ignoreCase = true)
            val dateMatch = cutoffMs == null || (it.dateCreated.time >= cutoffMs)
            queryMatch && dateMatch
        }
        
        if (filteredOrders.isEmpty()) {
            UiLog.d("OrdersScreen", "Empty UI check -> isLoading=$isLoading, emptyGuardActive=$emptyGuardActive, selectedStatus='$selectedStatus', searchQuery='$searchQuery'")
            // 如果正在加载（包含筛选切换后的首次加载），不要展示“未找到”空状态，优先显示加载
            if (isLoading || emptyGuardActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    UiLog.d("OrdersScreen", "Render Loading instead of Empty")
                    CircularProgressIndicator()
                }
                return@Column
            }
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
                
                // 主要提示文本（根据筛选状态展示更贴近的文案）
                val statusLabelZh = when (selectedStatus) {
                    "processing" -> "处理中"
                    "pending" -> "待付款"
                    "on-hold" -> "暂挂"
                    "completed" -> "已完成"
                    "cancelled" -> "已取消"
                    "refunded" -> "已退款"
                    "failed" -> "失败"
                    else -> "全部订单"
                }
                val statusLabelEn = when (selectedStatus) {
                    "processing" -> "processing"
                    "pending" -> "pending"
                    "on-hold" -> "on-hold"
                    "completed" -> "completed"
                    "cancelled" -> "cancelled"
                    "refunded" -> "refunded"
                    "failed" -> "failed"
                    else -> "all orders"
                }
                val emptyTip = if (selectedStatus.isNotEmpty()) {
                    if (locale.language == "zh") "无$statusLabelZh 订单" else "No $statusLabelEn orders"
                } else {
                    if (locale.language == "zh") "暂无订单" else "No orders"
                }
                Text(
                    text = emptyTip,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // 不再提供“清除筛选”的按钮，避免歧义
            }
        } else {
            // 添加筛选结果摘要
            if (selectedStatus.isNotEmpty() || searchQuery.isNotEmpty()) {
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
                        onClick = { onSelectOrder(order) },
                        currencySymbol = currencySymbol
                    )
                }
            }
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    onClick: () -> Unit,
    currencySymbol: String = "C$"
) {
    val deliveryDisplayInfo = remember(
        order.woofoodInfo?.deliveryDate,
        order.woofoodInfo?.deliveryTime,
        order.dateCreated.time
    ) {
        DeliveryDisplayFormatter.format(order.woofoodInfo, order.dateCreated, Locale.getDefault())
    }
    val dateHeadlineColor = when {
        !deliveryDisplayInfo.hasDate -> MaterialTheme.colorScheme.onSurfaceVariant
        deliveryDisplayInfo.isFutureOrToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    }

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
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = dateHeadlineColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = deliveryDisplayInfo.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = dateHeadlineColor
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = deliveryDisplayInfo.timeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.AttachMoney,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$currencySymbol${order.total}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // 门店位置（仅在可用时显示）
                val storeName = order.woofoodInfo?.storeLocationName?.trim().orEmpty()
                if (storeName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = storeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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