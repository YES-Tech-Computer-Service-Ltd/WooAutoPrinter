package com.example.wooauto.presentation.screens.orders

import android.content.Intent
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow

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
                onRefresh = { viewModel.refreshOrders() },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        // 修改Box的padding，保留顶部padding但减少底部padding
        Box(modifier = Modifier.padding(
            top = paddingValues.calculateTopPadding(),
            bottom = 0.dp, // 减少底部padding
            start = 0.dp,
            end = 0.dp
        )) {
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
                            // 直接导航到设置页面的API设置部分，而非独立页面
                            navController.navigate(NavigationItem.Settings.route) {
                                // 确保是单一顶部实例
                                launchSingleTop = true
                            }
                            // 发送广播通知设置页面直接打开API设置
                            val intent = Intent("com.example.wooauto.ACTION_OPEN_API_SETTINGS")
                            context.sendBroadcast(intent)
                        }
                    ) {
                        Text("前往API设置")
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
            
            // 显示API配置对话框
            if (showApiConfigDialog) {
                Dialog(
                    onDismissRequest = { showApiConfigDialog = false },
                    properties = DialogProperties(dismissOnClickOutside = true)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (locale.language == "zh") "请先配置API" else "Please Configure API",
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = if (locale.language == "zh") 
                                    "要使用订单功能，您需要先配置WooCommerce API设置。" 
                                else 
                                    "To use the order features, you need to configure the WooCommerce API settings first.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Button(
                                    onClick = { showApiConfigDialog = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (locale.language == "zh") "稍后再说" else "Later")
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Button(
                                    onClick = {
                                        showApiConfigDialog = false
                                        navController.navigate(NavigationItem.Settings.route) {
                                            launchSingleTop = true
                                        }
                                        val intent = Intent("com.example.wooauto.ACTION_OPEN_API_SETTINGS")
                                        context.sendBroadcast(intent)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (locale.language == "zh") "去设置" else "Settings")
                                }
                            }
                        }
                    }
                }
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
                        // 调用打印逻辑
                        viewModel.printOrder(orderId)
                    }
                )
            }
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
    onRefresh: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    val locale = LocalAppLocale.current
    
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 调整标题样式和位置
                Text(
                    text = if (locale.language == "zh") "订单" else "Orders",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp, // 稍微增大字体
                        letterSpacing = 0.5.sp // 增加字间距
                    ),
                    modifier = Modifier
                        .padding(start = 0.dp, end = 14.dp) // 增加右边距，与搜索框拉开距离
                        .width(70.dp), // 固定宽度，确保布局稳定
                    color = MaterialTheme.colorScheme.onPrimary
                )
                
                // 搜索框样式
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp, max = 58.dp) // 增加高度
                        .padding(vertical = 4.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(12.dp),
                            spotColor = Color.Black.copy(alpha = 0.2f)
                        )
                        .clip(RoundedCornerShape(12.dp)),
                    placeholder = { 
                        Text(
                            text = if (locale.language == "zh") "搜索订单..." else "Search orders...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 16.sp, // 增大占位符字体
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ) 
                    },
                    leadingIcon = { 
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = if (locale.language == "zh") "搜索" else "Search",
                            modifier = Modifier.size(24.dp), // 增大图标
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(40.dp) // 增大按钮
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = if (locale.language == "zh") "清除" else "Clear",
                                    modifier = Modifier.size(22.dp), // 增大图标
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface, 
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 17.sp, // 显著增大输入文字大小
                        fontWeight = FontWeight.Normal
                    )
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