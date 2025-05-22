package com.example.wooauto

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.Composable
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
import com.example.wooauto.utils.OrderNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean

@AndroidEntryPoint
class MainActivity : ComponentActivity(), OrderNotificationManager.NotificationCallback {
    
    private val TAG = "MainActivity"
    
    @Inject
    lateinit var orderRepository: DomainOrderRepository
    
    @Inject
    lateinit var orderNotificationManager: OrderNotificationManager
    
    // 用于存储新订单的状态
    private var showNewOrderDialog by mutableStateOf(false)
    private var currentNewOrder by mutableStateOf<Order?>(null)
    
    // 初始化标志和任务
    private val isInitialized = AtomicBoolean(false)
    private var languageInitJob: Job? = null
    
    // 蓝牙相关权限 - 根据SDK版本优化
    private val bluetoothPermissions by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            // Android 6.0以下的兼容性处理
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }
    
    // 相机权限
    private val cameraPermission = arrayOf(Manifest.permission.CAMERA)
    
    // 通知权限 - 只在Android 13+请求
    private val notificationPermission by lazy { 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    }
    
    // 权限请求回调
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
                it.contains("BLUETOOTH", ignoreCase = true)
            }
            
            val hasLocationDenied = deniedPermissions.any {
                it.contains("LOCATION", ignoreCase = true)
            }
            
            val hasNotificationDenied = deniedPermissions.any {
                it == Manifest.permission.POST_NOTIFICATIONS
            }
            
            // 提示用户手动开启相关权限
            if (hasBluetoothDenied) {
                Log.w(TAG, "蓝牙相关权限被拒绝，蓝牙打印功能将无法正常工作")
                showPermissionSettings("蓝牙权限对于打印机功能至关，请在设置中手动开启")
            }
            
            if (hasLocationDenied && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Log.w(TAG, "位置权限被拒绝，蓝牙扫描功能将受限")
                showPermissionSettings("位置权限用于蓝牙设备扫描，请在设置中手动开启")
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // 异步初始化应用语言，减少启动时间
        languageInitJob = lifecycleScope.launch(Dispatchers.IO) {
            initAppLanguage()
        }
        
        super.onCreate(savedInstanceState)
        
        // 请求所需权限 - 延迟执行确保UI已加载
        lifecycleScope.launch {
            // 延迟权限请求，等待UI完全加载
            launch {
                requestRequiredPermissions()
            }
        }
        
        // 注册通知回调
        orderNotificationManager.registerCallback(this)
        
        setContent {
            MainAppContent()
        }
        
        // 标记初始化完成
        isInitialized.set(true)
    }
    
    @RequiresApi(Build.VERSION_CODES.S)
    @Composable
    private fun MainAppContent() {
        WooAutoTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                WooAutoApp.GetContent()
                
                // 显示新订单弹窗
                if (showNewOrderDialog && currentNewOrder != null) {
                    NewOrderPopup(
                        order = currentNewOrder!!,
                        onDismiss = { 
                            // 标记订单为已读
                            orderNotificationManager.markOrderAsRead(currentNewOrder!!.id)
                            showNewOrderDialog = false
                            currentNewOrder = null
                        },
                        onViewDetails = {
                            // 标记订单为已读
                            orderNotificationManager.markOrderAsRead(currentNewOrder!!.id)
                            // 保存订单ID，避免空指针异常
                            val orderId = currentNewOrder!!.id
                            // 隐藏弹窗
                            showNewOrderDialog = false
                            currentNewOrder = null
                            // 导航到订单页面并显示订单详情
                            val intent = Intent("com.example.wooauto.ACTION_OPEN_ORDER_DETAILS")
                            intent.putExtra("orderId", orderId)
                            sendBroadcast(intent)
                        },
                        onPrintOrder = {
                            orderNotificationManager.markOrderAsPrinted(currentNewOrder!!.id) { updatedOrder ->
                                updatedOrder?.let { 
                                    currentNewOrder = it
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 注销通知回调
        orderNotificationManager.unregisterCallback()
        
        // 取消所有正在执行的协程
        languageInitJob?.cancel()
    }
    
    // 实现NotificationCallback接口
    override fun onNewOrderReceived(order: Order) {
        Log.d(TAG, "收到新订单通知: #${order.number}")
        currentNewOrder = order
        showNewOrderDialog = true
    }
    
    /**
     * 初始化应用语言设置
     */
    private suspend fun initAppLanguage() {
        try {
            Log.d(TAG, "开始初始化应用语言设置")
            
            // 初始化 LocaleManager
            LocaleManager.initialize(applicationContext)
            
            // 从SharedPreferences加载保存的语言设置
            val savedLocale = withContext(Dispatchers.IO) {
                LocaleHelper.loadSavedLocale(this@MainActivity)
            }
            
            if (savedLocale != null) {
                // 找到保存的语言设置，应用它
                Log.d(TAG, "从SharedPreferences加载语言设置: ${savedLocale.language}")
                
                // 使用更完整的语言设置方法
                withContext(Dispatchers.Main) {
                    LocaleManager.setLocale(this@MainActivity, savedLocale)
                    
                    // 确保状态也更新
                    LocaleManager.updateLocale(savedLocale)
                }
            } else {
                // 没有保存的语言设置，使用系统语言
                val systemLocale = LocaleHelper.getSystemLocale(this@MainActivity)
                // 确保使用的是我们支持的语言之一
                val supportedLocale = LocaleHelper.SUPPORTED_LOCALES.find { 
                    it.language == systemLocale.language 
                } ?: Locale.ENGLISH
                
                Log.d(TAG, "没有保存的语言设置，使用系统语言: ${supportedLocale.language}")
                
                // 使用更完整的语言设置方法
                withContext(Dispatchers.Main) {
                    LocaleManager.setLocale(this@MainActivity, supportedLocale)
                    
                    // 更新UI状态
                    LocaleManager.updateLocale(supportedLocale)
                }
                
                // 保存语言设置 (IO线程)
                withContext(Dispatchers.IO) {
                    LocaleHelper.saveLocalePreference(this@MainActivity, supportedLocale)
                }
            }
            
            // 应用当前语言到上下文 (必须在主线程)
            withContext(Dispatchers.Main) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // API 24+, 使用createConfigurationContext方法
                    val locale = LocaleManager.currentLocale
                    val localeList = android.os.LocaleList(locale)
                    val config = resources.configuration.apply {
                        setLocales(localeList)
                    }
                    createConfigurationContext(config)
                } else {
                    // API 23及以下，使用旧方法
                    val locale = LocaleManager.currentLocale
                    // 创建新的Configuration对象而不是使用copy()
                    val config = android.content.res.Configuration(resources.configuration)
                    @Suppress("DEPRECATION")
                    config.locale = locale
                    @Suppress("DEPRECATION")
                    resources.updateConfiguration(config, resources.displayMetrics)
                }
            }

//            Log.d(TAG, "应用语言初始化完成: ${LocaleManager.currentLocale.language}")
        } catch (e: Exception) {
            Log.e(TAG, "初始化应用语言失败", e)
            // 如果初始化失败，使用英语作为默认语言
            try {
                withContext(Dispatchers.Main) {
                    LocaleManager.setLocale(this@MainActivity, Locale.ENGLISH)
                    LocaleManager.updateLocale(Locale.ENGLISH)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "回退到英语也失败了", e2)
            }
        }
    }
    
    /**
     * 请求应用所需权限
     */
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
        
        // 检查相机权限
        cameraPermission.forEach { permission ->
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
    rememberScrollState()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(order.dateCreated)
    
    // 计算商品总数
    val totalItems = order.items.sumOf { it.quantity }
    
    // 定义未读样式
    MaterialTheme.colorScheme.error
    
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
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.new_order_received),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = Color.White
                            )
                        }
                    }
                }
                
                // 订单摘要信息
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    // 订单号和日期
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "订单 #${order.number}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 显示可能有多个订单的提示
                    // 只显示最新订单，但提示用户可能有多个
                    Text(
                        text = "提示：可能有多个新订单，这里仅显示最新一个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 订单金额
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "订单金额:",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = order.total,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // 订单商品数
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "商品数量:",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "$totalItems 件",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // 订单状态
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "订单状态:",
                            style = MaterialTheme.typography.bodyLarge
                        )
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
                            text = statusText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // 按钮区域
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onViewDetails,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("查看详情")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = onPrintOrder,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("打印订单")
                    }
                }
            }
        }
    }
} 