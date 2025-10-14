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
import com.example.wooauto.utils.UiLog
import android.view.WindowManager
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets as ComposeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.presentation.WooAutoApp
import com.example.wooauto.presentation.theme.WooAutoTheme
import com.example.wooauto.utils.LocaleManager
import com.example.wooauto.utils.OrderNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), OrderNotificationManager.NotificationCallback {
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
    private var currentPendingOrderCount by mutableStateOf(1)
    
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
        } else
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
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
            UiLog.d(TAG, "所有请求的权限已授予")
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
            UiLog.d(TAG, "以下权限已授予: ${grantedPermissions.joinToString(", ")}")
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        // 安装启动画面
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // 根据首屏准备度保留启动画面（最多 ~800ms）
        val startTime = System.currentTimeMillis()
        splash.setKeepOnScreenCondition {
            val elapsed = System.currentTimeMillis() - startTime
            // 资格检查、首个数据快照与语言就绪后尽快放行；硬上限 800ms
            val app = application as com.example.wooauto.WooAutoApplication
            val softReady = true // 语言已在 Application 初始化；Compose 首屏无阻塞
            val timeGuard = elapsed < 800
            softReady && timeGuard
        }
        
        // 请求所需权限 - 延迟到首帧后
        lifecycleScope.launch {
            launch {
                delay(600)
                requestRequiredPermissions()
            }
        }
        
        // 注册通知回调
        orderNotificationManager.registerCallback(this)
        // 确保回调注册后恢复任意残留的持续响铃为可控状态
        soundManager.stopAllSounds()
        
        // 初始化屏幕常亮功能
        initializeKeepScreenOn()
        
        setContent {
            MainAppContent()
        }

        // 进入应用后隐藏系统状态栏，保持更沉浸的商业展示
        setStatusBarHidden(true)
        // 应用亮度持久化
        applyPersistedAppBrightness()
        
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
                // 使用数据库Flow统计真实的新订单数量（processing + 未读）
                val newCountState = orderRepository.getNewProcessingCountFlow().collectAsState(initial = 0)
                val newCount = newCountState.value
                if ((showNewOrderDialog || (keepRinging && currentNewOrder != null)) && currentNewOrder != null) {
                    NewOrderPopup(
                        order = currentNewOrder!!,
                        keepRingingUntilAccept = keepRinging,
                        pendingCount = if (newCount > 0) newCount else currentPendingOrderCount,
                        onDismiss = { 
                            // 只是隐藏弹窗，不处理已读状态（由NewOrderPopup内部处理）
                            showNewOrderDialog = false
                            currentNewOrder = null
                            // 停止可能的持续响铃
                            soundManager.stopAllSounds()
                        },
                        onManualClose = {
                            // 用户手动关闭：不标记已读，保留在 New 队列；停止声音
                            soundManager.stopAllSounds()
                            showNewOrderDialog = false
                            currentNewOrder = null
                        },
                        onStartProcessing = {
                            // 开始处理：标记为已读，并尝试将状态置为 processing
                            val targetId = currentNewOrder!!.id
                            val currentStatus = currentNewOrder!!.status
                            soundManager.stopAllSounds()
                            showNewOrderDialog = false
                            currentNewOrder = null
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    if (currentStatus != "processing") {
                                        orderRepository.updateOrderStatus(targetId, "processing")
                                    }
                                } catch (_: Exception) {}
                                try {
                                    orderNotificationManager.markOrderAsRead(targetId)
                                } catch (_: Exception) {}
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
    override fun onNewOrderReceived(order: Order, totalCount: Int) {
        UiLog.d(TAG, "[UI] onNewOrderReceived id=${order.id} #${order.number} total=$totalCount ts=${System.currentTimeMillis()}")
        val incoming = if (totalCount <= 0) 1 else totalCount
        if (showNewOrderDialog && currentNewOrder != null) {
            // 弹窗已显示：累计增加数量，并更新展示的订单为最新
            UiLog.d(TAG, "[UI] 弹窗已显示，叠加计数 +$incoming")
            currentPendingOrderCount += incoming
            currentNewOrder = order
        } else {
            // 弹窗未显示：以本批数量为基准
            UiLog.d(TAG, "[UI] 弹窗未显示，准备显示全屏弹窗 count=$incoming")
            currentPendingOrderCount = incoming
            currentNewOrder = order
            showNewOrderDialog = true
        }
    }
    
    // 语言初始化已在 WooAutoApplication.onCreate 统一执行
    
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
            UiLog.d(TAG, "应用缺少以下权限: ${missingPermissions.joinToString(", ")}")
        } else {
            UiLog.d(TAG, "所有必要权限已获取")
        }
        
        // 请求所有缺少的权限
        if (permissionsToRequest.isNotEmpty()) {
            UiLog.d(TAG, "正在请求以下权限: ${permissionsToRequest.joinToString(", ")}")
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
        UiLog.d(TAG, "外部组件请求权限")
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
                    UiLog.d(TAG, "屏幕常亮设置变更: $keepOn")
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
                UiLog.d(TAG, "已启用屏幕常亮")
            } else {
                // 取消屏幕常亮
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                UiLog.d(TAG, "已禁用屏幕常亮")
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新屏幕常亮状态失败", e)
        }
    }

    /**
     * 应用应用内亮度（若有持久化值）
     */
    private fun applyPersistedAppBrightness() {
        lifecycleScope.launch {
            try {
                settingsRepository.getAppBrightnessFlow().collect { percent ->
                    if (percent == null) {
                        // 跟随系统
                        window.attributes = window.attributes.apply { screenBrightness = -1f }
                    } else {
                        val v = (percent.coerceIn(5, 100) / 100f)
                        window.attributes = window.attributes.apply { screenBrightness = v }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "应用持久化亮度失败", e)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 当Activity恢复时，确保屏幕常亮状态正确
        if (isKeepScreenOnEnabled) {
            updateScreenOnState(true)
        }
        // 恢复时再次应用状态栏隐藏，防止系统恢复显示
        setStatusBarHidden(true)
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

private fun AppCompatActivity.setStatusBarHidden(hidden: Boolean) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (hidden) {
                controller?.hide(android.view.WindowInsets.Type.statusBars())
                controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller?.show(android.view.WindowInsets.Type.statusBars())
            }
        } else {
            @Suppress("DEPRECATION")
            if (hidden) {
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
            val compat = WindowInsetsControllerCompat(window, window.decorView)
            if (hidden) compat.hide(WindowInsetsCompat.Type.statusBars()) else compat.show(WindowInsetsCompat.Type.statusBars())
        }
        // 让内容拓展到系统栏区域，获得扁平沉浸效果
        WindowCompat.setDecorFitsSystemWindows(window, false)
    } catch (_: Throwable) {
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
    pendingCount: Int = 1,
    onDismiss: () -> Unit,
    onManualClose: () -> Unit,
    onStartProcessing: () -> Unit
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
    
    // Back键处理：持续提示开启时禁用返回键，否则允许关闭
    BackHandler(enabled = !keepRingingUntilAccept) {
        onManualClose()
    }

    // 全屏覆盖层（不使用 Dialog，避免不同 ROM 对对话框窗口 inset 的差异）
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .zIndex(999f),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 中间大标题
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(84.dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "收到 ${pendingCount} 个新订单",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            // 底部大按钮区
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .windowInsetsPadding(ComposeWindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .windowInsetsPadding(ComposeWindowInsets.displayCutout.only(WindowInsetsSides.Bottom)),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onStartProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.start_processing_bilingual))
                }
                OutlinedButton(
                    onClick = onManualClose,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.close_bilingual))
                }
            }
        }
    }
} 