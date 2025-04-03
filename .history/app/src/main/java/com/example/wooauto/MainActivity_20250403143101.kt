package com.example.wooauto

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.wooauto.R
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.presentation.WooAutoApp
import com.example.wooauto.presentation.theme.WooAutoTheme
import com.example.wooauto.utils.LocaleHelper
import com.example.wooauto.utils.LocaleManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val TAG = "MainActivity"
    
    @Inject
    lateinit var orderRepository: DomainOrderRepository
    
    // 用于存储新订单的状态
    private var showNewOrderDialog by mutableStateOf(false)
    private var newOrderId by mutableStateOf<Long?>(null)
    private var currentNewOrder by mutableStateOf<Order?>(null)
    
    // 新订单广播接收器
    private val newOrderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.wooauto.NEW_ORDER_RECEIVED") {
                val orderId = intent.getLongExtra("orderId", -1L)
                Log.d(TAG, "收到新订单广播: orderId=$orderId")
                
                if (orderId != -1L) {
                    newOrderId = orderId
                    loadAndShowOrderDetails(orderId)
                }
            }
        }
    }
    
    // 蓝牙相关权限
    private val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    
    // 通知权限
    private val notificationPermission = 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    
    // 权限请求回调
    @RequiresApi(Build.VERSION_CODES.S)
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 记录每个权限的结果
        val deniedPermissions = permissions.filterValues { !it }.keys.toList()
        val grantedPermissions = permissions.filterValues { it }.keys.toList()
        
        if (deniedPermissions.isEmpty()) {
            Log.d(TAG, "所有请求的权限已授予")
        } else {
            Log.w(TAG, "以下权限被拒绝: ${deniedPermissions.joinToString(", ")}")
            
            // 检查被拒绝的权限类型
            val hasBluetoothDenied = deniedPermissions.any { 
                it == Manifest.permission.BLUETOOTH_CONNECT || 
                it == Manifest.permission.BLUETOOTH_SCAN ||
                it == Manifest.permission.BLUETOOTH ||
                it == Manifest.permission.BLUETOOTH_ADMIN
            }
            
            val hasNotificationDenied = deniedPermissions.any {
                it == Manifest.permission.POST_NOTIFICATIONS
            }
            
            // 提示用户手动开启相关权限
            if (hasBluetoothDenied) {
                Log.w(TAG, "蓝牙相关权限被拒绝，蓝牙打印功能将无法正常工作")
                showPermissionSettings("蓝牙权限对于打印机功能至关，请在设置中手动开启")
            }
            
            if (hasNotificationDenied && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Log.w(TAG, "通知权限被拒绝，应用将无法显示新订单通知")
                showPermissionSettings("通知权限对于接收新订单提醒至关重要，请在设置中手动开启")
            }
        }
        
        if (grantedPermissions.isNotEmpty()) {
            Log.d(TAG, "以下权限已授予: ${grantedPermissions.joinToString(", ")}")
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        // 在super.onCreate之前初始化应用语言
        initAppLanguage()
        
        super.onCreate(savedInstanceState)
        
        // 请求所需权限
        requestRequiredPermissions()
        
        // 注册新订单广播接收器
        registerNewOrderReceiver()
        
        setContent {
            WooAutoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WooAutoApp.getContent()
                    
                    // 显示新订单弹窗
                    if (showNewOrderDialog && currentNewOrder != null) {
                        NewOrderPopup(
                            order = currentNewOrder!!,
                            onDismiss = { 
                                // 标记订单为已读
                                markOrderAsRead(currentNewOrder!!.id)
                                showNewOrderDialog = false
                                currentNewOrder = null
                            },
                            onViewDetails = {
                                // 标记订单为已读
                                markOrderAsRead(currentNewOrder!!.id)
                                // 隐藏弹窗
                                showNewOrderDialog = false
                                currentNewOrder = null
                                // 导航到订单列表页面（可以根据需要修改）
                                val intent = Intent("com.example.wooauto.ACTION_VIEW_ORDER_DETAILS")
                                intent.putExtra("orderId", currentNewOrder!!.id)
                                sendBroadcast(intent)
                            },
                            onPrintOrder = {
                                markOrderAsPrinted(currentNewOrder!!.id)
                            }
                        )
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 注销广播接收器
        try {
            unregisterReceiver(newOrderReceiver)
            Log.d(TAG, "注销新订单广播接收器")
        } catch (e: Exception) {
            Log.e(TAG, "注销新订单广播接收器失败: ${e.message}")
        }
    }
    
    /**
     * 注册新订单广播接收器
     */
    private fun registerNewOrderReceiver() {
        try {
            val filter = IntentFilter("com.example.wooauto.NEW_ORDER_RECEIVED")
            registerReceiver(newOrderReceiver, filter)
            Log.d(TAG, "已注册新订单广播接收器")
        } catch (e: Exception) {
            Log.e(TAG, "注册新订单广播接收器失败: ${e.message}")
        }
    }
    
    /**
     * 加载并显示订单详情
     */
    private fun loadAndShowOrderDetails(orderId: Long) {
        lifecycleScope.launch {
            try {
                // 使用IO线程执行网络/数据库操作
                val order = withContext(Dispatchers.IO) {
                    orderRepository.getOrderById(orderId)
                }
                
                if (order != null) {
                    Log.d(TAG, "加载订单详情成功: #${order.number}")
                    currentNewOrder = order
                    showNewOrderDialog = true
                } else {
                    Log.e(TAG, "获取订单详情失败，未找到订单: $orderId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载订单详情失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 标记订单为已读
     */
    private fun markOrderAsRead(orderId: Long) {
        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    orderRepository.markOrderAsRead(orderId)
                }
                if (success) {
                    Log.d(TAG, "成功标记订单为已读: $orderId")
                } else {
                    Log.e(TAG, "标记订单为已读失败: $orderId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "标记订单为已读时出错: ${e.message}", e)
            }
        }
    }
    
    /**
     * 标记订单为已打印
     */
    private fun markOrderAsPrinted(orderId: Long) {
        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    orderRepository.markOrderAsPrinted(orderId)
                }
                if (success) {
                    Log.d(TAG, "成功标记订单为已打印: $orderId")
                    // 刷新订单数据
                    val updatedOrder = orderRepository.getOrderById(orderId)
                    if (updatedOrder != null) {
                        currentNewOrder = updatedOrder
                    }
                } else {
                    Log.e(TAG, "标记订单为已打印失败: $orderId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "标记订单为已打印时出错: ${e.message}", e)
            }
        }
    }
    
    /**
     * 初始化应用语言设置
     */
    private fun initAppLanguage() {
        try {
            Log.d(TAG, "开始初始化应用语言设置")
            
            // 初始化 LocaleManager
            LocaleManager.initialize(applicationContext)
            
            // 从SharedPreferences加载保存的语言设置
            val savedLocale = LocaleHelper.loadSavedLocale(this)
            
            if (savedLocale != null) {
                // 找到保存的语言设置，应用它
                Log.d(TAG, "从SharedPreferences加载语言设置: ${savedLocale.language}")
                
                // 使用更完整的语言设置方法
                LocaleManager.setLocale(this, savedLocale)
                
                // 确保状态也更新
                LocaleManager.updateLocale(savedLocale)
            } else {
                // 没有保存的语言设置，使用系统语言
                val systemLocale = LocaleHelper.getSystemLocale(this)
                // 确保使用的是我们支持的语言之一
                val supportedLocale = LocaleHelper.SUPPORTED_LOCALES.find { 
                    it.language == systemLocale.language 
                } ?: Locale.ENGLISH
                
                Log.d(TAG, "没有保存的语言设置，使用系统语言: ${supportedLocale.language}")
                
                // 使用更完整的语言设置方法
                LocaleManager.setLocale(this, supportedLocale)
                
                // 更新UI状态
                LocaleManager.updateLocale(supportedLocale)
                
                // 保存语言设置
                LocaleHelper.saveLocalePreference(this, supportedLocale)
            }
            
            // 应用当前语言到上下文
            // API 24+, 使用createConfigurationContext方法
            val locale = LocaleManager.currentLocale
            val localeList = android.os.LocaleList(locale)
            val config = resources.configuration.apply {
                setLocales(localeList)
            }
            createConfigurationContext(config)

            Log.d(TAG, "应用语言初始化完成: ${LocaleManager.currentLocale.language}")
        } catch (e: Exception) {
            Log.e(TAG, "初始化应用语言失败", e)
            // 如果初始化失败，使用英语作为默认语言
            try {
                LocaleManager.setLocale(this, Locale.ENGLISH)
                LocaleManager.updateLocale(Locale.ENGLISH)
            } catch (e2: Exception) {
                Log.e(TAG, "回退到英语也失败了", e2)
            }
        }
    }
    
    /**
     * 请求应用所需权限
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        val missingPermissions = mutableListOf<String>()
        
        // 检查蓝牙权限
        bluetoothPermissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
                missingPermissions.add(permission)
            }
        }
        
        // 检查通知权限
        notificationPermission.forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
                missingPermissions.add(permission)
            }
        }
        
        // 记录缺少的权限
        if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "应用缺少以下权限: ${missingPermissions.joinToString(", ")}")
        } else {
            Log.d(TAG, "所有必要权限已获取")
        }
        
        // 请求所有缺少的权限
        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "正在请求以下权限: ${permissionsToRequest.joinToString(", ")}")
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    /**
     * 显示权限设置页面并提示用户
     */
    private fun showPermissionSettings(message: String) {
        // 首先显示提示消息
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        
        // 然后跳转到应用权限设置页面
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
    
    /**
     * 提供给外部组件的方法，用于重新请求所有权限
     * 可以在扫描蓝牙等功能失败时调用
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun requestAllPermissions() {
        Log.d(TAG, "外部组件请求权限")
        requestRequiredPermissions()
    }
}

/**
 * 新订单弹窗
 * 与OrderDetailDialog不同的UI设计，醒目的显示新订单提醒
 */
@Composable
fun NewOrderPopup(
    order: Order,
    onDismiss: () -> Unit,
    onViewDetails: () -> Unit,
    onPrintOrder: () -> Unit
) {
    val scrollState = rememberScrollState()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(order.dateCreated)
    
    // 计算商品总数
    val totalItems = order.items.sumOf { it.quantity }
    
    // 定义未读样式
    val unreadBadgeColor = MaterialTheme.colorScheme.error
    
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
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 顶部标题栏，使用醒目的颜色
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "新订单 #${order.number}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        
                        // 未读标记
                        if (!order.isRead) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(unreadBadgeColor, RoundedCornerShape(6.dp))
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 订单基本信息区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .weight(1f, fill = false)
                ) {
                    // 客户信息卡片
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = order.customerName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            
                            if (order.contactInfo.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = order.contactInfo,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                    
                    // 订单信息卡片
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            // 订单日期
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formattedDate,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // 订单状态
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val statusText = when(order.status) {
                                    "processing" -> "处理中"
                                    "pending" -> "待付款"
                                    "completed" -> "已完成"
                                    "cancelled" -> "已取消"
                                    "refunded" -> "已退款"
                                    "failed" -> "失败"
                                    "on-hold" -> "暂挂"
                                    else -> order.status
                                }
                                Text(
                                    text = "状态: $statusText",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // 支付方式
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Payment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = order.paymentMethod,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // 订单总金额
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachMoney,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "总金额: ${order.total}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    // 商品摘要卡片
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "商品 ($totalItems)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 显示前三个商品和总数
                            val displayItems = if (order.items.size > 3) {
                                order.items.take(3)
                            } else {
                                order.items
                            }
                            
                            displayItems.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${item.quantity} x ${item.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "¥${item.total}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            
                            if (order.items.size > 3) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "...还有 ${order.items.size - 3} 个商品",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    
                    // 已读/未读状态
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val readStatusText = if (order.isRead) "已读" else "未读"
                        val readStatusIcon = if (order.isRead) Icons.Default.MarkEmailRead else Icons.Default.MarkEmailUnread
                        val readStatusColor = if (order.isRead) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                        
                        Icon(
                            imageVector = readStatusIcon,
                            contentDescription = null,
                            tint = readStatusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = readStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = readStatusColor
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // 打印状态
                        val printStatusText = if (order.isPrinted) "已打印" else "未打印"
                        val printStatusIcon = if (order.isPrinted) Icons.Default.Print else Icons.Default.Print
                        val printStatusColor = if (order.isPrinted) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        
                        Icon(
                            imageVector = printStatusIcon,
                            contentDescription = null,
                            tint = printStatusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = printStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = printStatusColor
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 底部按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 打印按钮
                    Button(
                        onClick = onPrintOrder,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (order.isPrinted) "重新打印" else "打印订单")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 查看详情按钮
                    Button(
                        onClick = onViewDetails,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("查看详情")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 关闭按钮
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("关闭")
                    }
                }
            }
        }
    }
} 