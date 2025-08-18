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
import android.view.WindowManager
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.presentation.WooAutoApp
import com.example.wooauto.presentation.theme.WooAutoTheme
import com.example.wooauto.utils.LocaleHelper
import com.example.wooauto.utils.LocaleManager
import com.example.wooauto.utils.OrderNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity(), OrderNotificationManager.NotificationCallback {
    @Inject
    lateinit var soundManager: com.example.wooauto.utils.SoundManager
    
    private val TAG = "MainActivity"
    
    @Inject
    lateinit var orderRepository: DomainOrderRepository
    
    @Inject
    lateinit var orderNotificationManager: OrderNotificationManager
    
    @Inject
    lateinit var settingsRepository: DomainSettingRepository
    
    // 用于存储新订单的状态
    private var showNewOrderDialog by mutableStateOf(false)
    private var currentNewOrder by mutableStateOf<Order?>(null)
    
    // 屏幕常亮状态
    private var isKeepScreenOnEnabled by mutableStateOf(false)
    
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
        
        // 初始化屏幕常亮功能
        initializeKeepScreenOn()
        
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
                
                // 强化显示条件：当开启“接单持续提示”且仍有当前订单时，强制显示弹窗，避免外部事件误关
                val keepRinging = soundManager.isKeepRingingUntilAcceptEnabled()
                if ((showNewOrderDialog || (keepRinging && currentNewOrder != null)) && currentNewOrder != null) {
                    NewOrderPopup(
                        order = currentNewOrder!!,
                        keepRingingUntilAccept = keepRinging,
                        onDismiss = { 
                            // 只是隐藏弹窗，不处理已读状态（由NewOrderPopup内部处理）
                            showNewOrderDialog = false
                            currentNewOrder = null
                            // 停止可能的持续响铃
                            soundManager.stopAllSounds()
                        },
                        onManualClose = {
                            // 用户手动关闭时标记为已读
                            orderNotificationManager.markOrderAsRead(currentNewOrder!!.id)
                            // 停止持续响铃
                            soundManager.stopAllSounds()
                            showNewOrderDialog = false
                            currentNewOrder = null
                        },
                        onViewDetails = {
                            // 用户主动查看详情时标记为已读
                            orderNotificationManager.markOrderAsRead(currentNewOrder!!.id)
                            // 停止持续响铃
                            soundManager.stopAllSounds()
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
                            // 打印订单，不自动标记为已读
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
            // 交给 LocaleManager 统一初始化与应用
            LocaleManager.initialize(applicationContext)
            // 不再重复设置/回退，避免竞态与闪切
        } catch (e: Exception) {
            Log.e(TAG, "初始化应用语言失败", e)
            // 保持现状，不做强制回退，避免“自动切换”
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
    
    /**
     * 初始化屏幕常亮功能
     */
    private fun initializeKeepScreenOn() {
        lifecycleScope.launch {
            try {
                // 监听屏幕常亮设置的变化
                settingsRepository.getKeepScreenOn().collect { keepOn ->
                    Log.d(TAG, "屏幕常亮设置变更: $keepOn")
                    isKeepScreenOnEnabled = keepOn
                    updateScreenOnState(keepOn)
                }
            } catch (e: Exception) {
                Log.e(TAG, "初始化屏幕常亮功能失败", e)
            }
        }
    }
    
    /**
     * 更新屏幕常亮状态
     */
    private fun updateScreenOnState(keepOn: Boolean) {
        try {
            if (keepOn) {
                // 保持屏幕常亮
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                Log.d(TAG, "已启用屏幕常亮")
            } else {
                // 取消屏幕常亮
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                Log.d(TAG, "已禁用屏幕常亮")
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新屏幕常亮状态失败", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 当Activity恢复时，确保屏幕常亮状态正确
        if (isKeepScreenOnEnabled) {
            updateScreenOnState(true)
        }
    }
    
    override fun onPause() {
        super.onPause()
        // 当Activity暂停时，根据设置决定是否保持屏幕常亮
        // 如果用户启用了屏幕常亮，则保持；否则清除标志
        if (!isKeepScreenOnEnabled) {
            updateScreenOnState(false)
        }
    }
}

/**
 * 新订单弹窗
 * 与OrderDetailDialog不同的UI设计，醒目的显示新订单提醒
 */
@Composable
fun NewOrderPopup(
    order: Order,
    keepRingingUntilAccept: Boolean,
    onDismiss: () -> Unit,
    onManualClose: () -> Unit,
    onViewDetails: () -> Unit,
    onPrintOrder: () -> Unit
) {
    rememberScrollState()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(order.dateCreated)
    
    // 计算商品总数
    val totalItems = order.items.sumOf { it.quantity }
    
    // 用于区分自动关闭和手动关闭的状态
    var isAutoClose by remember { mutableStateOf(false) }
    
    // 自动关闭定时器：当开启“接单持续提示”时不自动关闭
    LaunchedEffect(key1 = order.id, key2 = keepRingingUntilAccept) {
        if (!keepRingingUntilAccept) {
            delay(10000)
            isAutoClose = true
            onDismiss()
        }
    }
    
    Dialog(
        onDismissRequest = {
            // 在“接单持续提示”开启时，完全忽略任何外部dismiss请求，防止系统或外部事件导致自动关闭
            if (keepRingingUntilAccept) return@Dialog
            // 用户手动关闭（点击外部区域或返回键）或定时自动关闭
            if (!isAutoClose) {
                onManualClose()
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !keepRingingUntilAccept,
            dismissOnClickOutside = !keepRingingUntilAccept,
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
                        IconButton(onClick = onManualClose) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
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
                            text = stringResource(R.string.order_number, order.number),
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
                    Text(
                        text = stringResource(R.string.multiple_orders_hint),
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
                            text = stringResource(R.string.order_amount_label),
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
                            text = stringResource(R.string.items_count_label),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.items_count_value, totalItems),
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
                            text = stringResource(R.string.order_status_label),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        val statusText = when(order.status) {
                            "processing" -> stringResource(R.string.order_status_processing)
                            "pending" -> stringResource(R.string.order_status_pending)
                            "completed" -> stringResource(R.string.order_status_completed)
                            "cancelled" -> stringResource(R.string.order_status_cancelled)
                            "refunded" -> stringResource(R.string.order_status_refunded)
                            "failed" -> stringResource(R.string.order_status_failed)
                            "on-hold" -> stringResource(R.string.order_status_on_hold)
                            else -> order.status
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // 按钮区域（接受订单/打印）
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
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("接受订单")
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
                        Text(stringResource(R.string.print_order))
                    }
                }
            }
        }
    }
} 