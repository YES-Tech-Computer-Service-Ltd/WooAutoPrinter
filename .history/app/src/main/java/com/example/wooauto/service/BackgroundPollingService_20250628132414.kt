package com.example.wooauto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.wooauto.MainActivity
import com.example.wooauto.R
import com.example.wooauto.data.local.WooCommerceConfig
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.domain.printer.PrinterManager
import com.example.wooauto.domain.templates.TemplateType
import com.example.wooauto.domain.printer.PrinterStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import javax.inject.Inject
import kotlin.collections.HashSet
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import com.example.wooauto.utils.PowerManagementUtils

@AndroidEntryPoint
class BackgroundPollingService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "woo_auto_polling_channel"
        private const val TAG = "BackgroundPollingService"
        
        // 广播Action常量
        const val ACTION_NEW_ORDERS_RECEIVED = "com.example.wooauto.NEW_ORDERS_RECEIVED"
        const val ACTION_ORDERS_UPDATED = "com.example.wooauto.ORDERS_UPDATED"
        const val EXTRA_ORDER_COUNT = "order_count"
        const val EXTRA_RESTART_POLLING = "com.example.wooauto.RESTART_POLLING"
        
        // 轮询间隔常量
        private const val DEFAULT_POLLING_INTERVAL = 30 // 默认轮询间隔（秒）
        private const val INITIAL_POLLING_INTERVAL = 5 // 初始轮询间隔（秒）
        private const val MIN_POLLING_INTERVAL = 5 // 最小轮询间隔（秒）
        
        // 清理间隔
        private const val CLEANUP_INTERVAL = 10 * 60 * 1000L // 10分钟清理一次
        private const val MAX_PROCESSED_IDS = 500 // 最大保留订单ID数量
    }

    @Inject
    lateinit var orderRepository: DomainOrderRepository

    @Inject
    lateinit var wooCommerceConfig: WooCommerceConfig

    @Inject
    lateinit var printerManager: PrinterManager

    @Inject
    lateinit var settingsRepository: DomainSettingRepository

    // 使用IO调度器创建协程作用域，减少主线程负担
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var intervalMonitorJob: Job? = null // 独立的间隔监听任务
    private var latestPolledDate: Date? = null
    
    // 使用LRU方式管理处理过的订单ID，减少内存占用
    private val processedOrderIds = LinkedHashSet<Long>(MAX_PROCESSED_IDS)
    
    // 轮询控制参数
    private var currentPollingInterval = DEFAULT_POLLING_INTERVAL
    private var isAppInForeground = false // 追踪应用是否在前台
    private var initialPollingComplete = false // 标记初始快速轮询是否完成
    
    // 并发控制
    private val restartMutex = Mutex() // 防止重复重启轮询
    private var isPollingActive = false // 轮询状态标记
    
    // 定义一个在前台时使用较短轮询间隔的倍数因子
    private val FOREGROUND_INTERVAL_FACTOR = 0.5f // 前台时轮询间隔是后台的一半

    // 添加网络状态监听相关组件
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isNetworkAvailable = true

    // 添加轮询看门狗相关属性
    private var watchdogJob: Job? = null
    private var lastPollingActivity = System.currentTimeMillis()
    private val WATCHDOG_CHECK_INTERVAL = 60 * 1000L // 每分钟检查一次
    private val POLLING_TIMEOUT_THRESHOLD = 3 * 60 * 1000L // 3分钟无活动则认为轮询可能停止

    // 添加电源管理和WiFi锁定相关组件
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var highPerfWifiLock: WifiManager.WifiLock? = null // 添加高性能WiFi锁
    private var powerManager: PowerManager? = null
    private var wifiManager: WifiManager? = null
    
    // 网络心跳检测相关
    private var networkHeartbeatJob: Job? = null
    private var lastNetworkCheckTime = 0L
    private val NETWORK_HEARTBEAT_INTERVAL = 30 * 1000L // 30秒检查一次网络
    private val MAX_NETWORK_RETRY_COUNT = 3
    private var networkRetryCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "【服务初始化】后台轮询服务开始初始化")
        
        // 创建通知渠道并启动前台服务
        createNotificationChannel()
        startForeground()
        
        // 初始化电源管理和WiFi锁定
        initPowerManagement()
        
        // 初始化网络监听器
        initNetworkListener()
        
        // 延迟初始化，确保应用组件完全加载
        serviceScope.launch {
            try {
                // 等待应用完成基础初始化
                delay(2000) // 给应用2秒时间完成基础初始化
                
                Log.d(TAG, "【服务初始化】开始读取轮询配置")
                
                // 读取保存的轮询间隔设置
                currentPollingInterval = withTimeoutOrNull(5000) {
                    wooCommerceConfig.pollingInterval.first()
                } ?: DEFAULT_POLLING_INTERVAL
                
                if (currentPollingInterval < MIN_POLLING_INTERVAL) {
                    currentPollingInterval = DEFAULT_POLLING_INTERVAL
                }
                
                Log.d(TAG, "【服务初始化】轮询间隔配置完成: ${currentPollingInterval}秒")
                
                // 注册前台状态监听器
                registerForegroundStateReceiver()
                
                Log.d(TAG, "【服务初始化】后台服务初始化完成")
            } catch (e: Exception) {
                Log.e(TAG, "【服务初始化】初始化失败: ${e.message}", e)
                currentPollingInterval = DEFAULT_POLLING_INTERVAL
                
                // 即使初始化失败，也要注册监听器
                try {
                    registerForegroundStateReceiver()
                } catch (e2: Exception) {
                    Log.e(TAG, "【服务初始化】注册监听器失败: ${e2.message}")
                }
            }
        }
        
        Log.d(TAG, "服务创建完成")
    }
    
    /**
     * 注册前台状态监听器
     * 根据应用是否在前台调整轮询间隔
     */
    private fun registerForegroundStateReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction("android.intent.action.SCREEN_ON")
                addAction("android.intent.action.SCREEN_OFF")
                addAction("android.intent.action.USER_PRESENT")
            }
            
            ContextCompat.registerReceiver(
                this,
                object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        when (intent?.action) {
                            "android.intent.action.SCREEN_ON" -> {
                                // 屏幕点亮，可能在前台
                                Log.d(TAG, "屏幕点亮，设置前台状态为true")
                                isAppInForeground = true
                                adjustPollingInterval()
                            }
                            "android.intent.action.SCREEN_OFF" -> {
                                // 屏幕关闭，一定在后台
                                Log.d(TAG, "屏幕关闭，设置前台状态为false，轮询将继续运行")
                                isAppInForeground = false
                                
                                // 息屏时加强网络保持措施
                                Log.d(TAG, "【息屏处理】屏幕关闭，加强网络保持措施")
                                serviceScope.launch {
                                    try {
                                        // 重新获取所有锁，确保在息屏状态下保持最强的网络保持
                                        delay(1000) // 等待系统稳定
                                        
                                        Log.d(TAG, "【息屏处理】重新获取电源和网络锁")
                                        acquireWakeLock()
                                        acquireWifiLock()
                                        acquireHighPerfWifiLock()
                                        
                                        // 检查WiFi状态
                                        val wifiState = wifiManager?.wifiState
                                        Log.d(TAG, "【息屏处理】息屏后WiFi状态: $wifiState")
                                        
                                        // 等待5秒后进行网络连接检查
                                        delay(5000)
                                        val isConnected = checkNetworkConnectivity()
                                        Log.d(TAG, "【息屏处理】息屏5秒后网络状态: $isConnected")
                                        
                                        if (!isConnected) {
                                            Log.w(TAG, "【息屏处理】息屏后检测到网络断开，立即处理")
                                            handleNetworkDisconnection()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "【息屏处理】息屏网络保持处理失败: ${e.message}", e)
                                    }
                                }
                                
                                adjustPollingInterval()
                            }
                            "android.intent.action.USER_PRESENT" -> {
                                // 用户解锁屏幕，确定在前台
                                Log.d(TAG, "用户解锁屏幕，确认前台状态为true")
                                isAppInForeground = true
                                adjustPollingInterval()
                            }
                        }
                    }
                },
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            
            Log.d(TAG, "已注册前台状态监听器")
        } catch (e: Exception) {
            Log.e(TAG, "注册前台状态监听器失败: ${e.message}", e)
        }
    }
    
    /**
     * 根据应用是否在前台调整轮询间隔
     */
    private fun adjustPollingInterval() {
        serviceScope.launch {
            try {
                // 安全地获取基础轮询间隔
                val baseInterval = withTimeoutOrNull(3000) {
                    wooCommerceConfig.pollingInterval.first()
                } ?: DEFAULT_POLLING_INTERVAL
                
                val newInterval = if (isAppInForeground) {
                    // 前台使用较短的间隔
                    (baseInterval * FOREGROUND_INTERVAL_FACTOR).toInt().coerceAtLeast(MIN_POLLING_INTERVAL)
                } else {
                    // 后台使用正常间隔
                    baseInterval
                }
                
                if (newInterval != currentPollingInterval) {
                    Log.d(TAG, "调整轮询间隔: ${currentPollingInterval}秒 -> ${newInterval}秒 (前台状态: $isAppInForeground)")
                    currentPollingInterval = newInterval
                    
                    // 不再重启轮询任务，只更新间隔值
                    // 避免在屏幕锁定时中断轮询任务
                    Log.d(TAG, "轮询间隔已更新，下次轮询周期将使用新间隔")
                }
            } catch (e: Exception) {
                Log.e(TAG, "调整轮询间隔失败: ${e.message}", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 检查是否是重启轮询的请求
        val shouldRestartPolling = intent?.getBooleanExtra(EXTRA_RESTART_POLLING, false) ?: false
        
        if (shouldRestartPolling) {
            Log.d(TAG, "收到重启轮询请求")
            restartPolling()
        } else {
            startPolling()
        }
        
        // 启动定期清理任务
        startPeriodicCleanupTask()
        
        // 启动轮询看门狗
        startPollingWatchdog()
        
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "【服务销毁】开始清理资源")
        
        // 标记轮询为非活动状态
        isPollingActive = false
        
        // 取消所有协程任务
        pollingJob?.cancel()
        intervalMonitorJob?.cancel()
        watchdogJob?.cancel() // 取消看门狗任务
        networkHeartbeatJob?.cancel() // 取消网络心跳检测
        
        // 取消整个服务协程作用域
        serviceScope.cancel()
        
        // 取消网络监听器注册
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback?.let { callback ->
                connectivityManager?.unregisterNetworkCallback(callback)
                Log.d(TAG, "网络监听器已注销")
            }
        }
        
        // 释放电源锁
        releaseWakeLock()
        releaseWifiLock()
        releaseHighPerfWifiLock() // 释放高性能WiFi锁
        
        Log.d(TAG, "【服务销毁】资源清理完成")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW // 降低通知重要性以减少用户干扰
            ).apply {
                description = "WooAuto订单同步服务"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForeground() {
        val notification = createNotification(getString(R.string.app_name), "正在同步订单数据...")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    Log.d(TAG, "前台服务启动成功 (带类型)")
                } catch (e: Exception) {
                    // 如果带类型启动失败，尝试不带类型启动
                    Log.w(TAG, "带类型启动前台服务失败，尝试不带类型启动: ${e.message}")
                    startForeground(NOTIFICATION_ID, notification)
                    Log.d(TAG, "前台服务启动成功 (不带类型)")
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
                Log.d(TAG, "前台服务启动成功 (Android 9及以下)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "前台服务启动失败: ${e.message}", e)
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // 降低通知优先级
            .build()
    }

    /**
     * 重启轮询任务
     * 用于配置更改后刷新
     */
    private fun restartPolling() {
        serviceScope.launch {
            try {
                restartMutex.withLock {
                    Log.d(TAG, "重启轮询任务 (已加锁)")
                    
                    // 标记轮询为非活动状态，停止当前轮询循环
                    isPollingActive = false
                    Log.d(TAG, "已标记轮询为非活动状态")
                    
                    // 等待当前轮询循环自然结束
                    try {
                        val pollingJobToWait = pollingJob
                        if (pollingJobToWait?.isActive == true) {
                            Log.d(TAG, "等待当前轮询任务结束...")
                            withTimeoutOrNull(5000) { // 最多等待5秒
                                pollingJobToWait.join()
                            }
                            Log.d(TAG, "当前轮询任务已结束")
                        } else {
                            Log.d(TAG, "当前轮询任务已停止或不存在")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "等待轮询任务结束异常: ${e.message}")
                        pollingJob?.cancel() // 强制取消
                    }
                    pollingJob = null
                    
                    // 等待间隔监听任务结束
                    try {
                        val intervalJobToWait = intervalMonitorJob
                        if (intervalJobToWait?.isActive == true) {
                            Log.d(TAG, "等待间隔监听任务结束...")
                            withTimeoutOrNull(2000) { // 最多等待2秒
                                intervalJobToWait.join()
                            }
                            Log.d(TAG, "间隔监听任务已结束")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "等待间隔监听任务结束异常: ${e.message}")
                        intervalMonitorJob?.cancel() // 强制取消
                    }
                    intervalMonitorJob = null
                    
                    // 短暂延迟确保资源释放
                    delay(200)
                    
                    // 重置状态标志
                    initialPollingComplete = false
                    Log.d(TAG, "重置轮询状态标志")
                    
                    // 启动新的轮询任务
                    Log.d(TAG, "启动新的轮询任务")
                    startPolling()
                    
                    // 验证轮询是否成功启动
                    delay(1000) // 等待1秒让轮询启动
                    if (isPollingActive && pollingJob?.isActive == true) {
                        Log.d(TAG, "轮询重启成功")
                    } else {
                        Log.w(TAG, "轮询重启可能失败，尝试再次启动")
                        startPolling()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "重启轮询过程中发生异常: ${e.message}", e)
                // 如果重启失败，确保至少启动基本轮询
                try {
                    delay(500)
                    if (!isPollingActive) {
                        Log.w(TAG, "重启失败，尝试基本启动")
                        startPolling()
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "基本启动也失败: ${e2.message}", e2)
                }
            }
        }
    }

    private fun startPolling() {
        if (isPollingActive) {
            Log.w(TAG, "轮询已在运行中，跳过重复启动")
            return
        }
        
        try {
            isPollingActive = true
            Log.d(TAG, "开始启动轮询任务")
            
            // 启动独立的间隔监听任务（避免重复创建）
            startIntervalMonitor()
            
            // 在开始轮询时获取锁
            acquireWakeLock()
            acquireWifiLock()
            acquireHighPerfWifiLock() // 获取高性能WiFi锁
            
            // 启动网络心跳检测
            startNetworkHeartbeat()
            
            // 检查电池优化状态
            checkBatteryOptimization()
            
            pollingJob = serviceScope.launch {
                try {
                    // 应用启动后先使用短间隔进行初始轮询
                    var useInitialInterval = !initialPollingComplete
                    
                    if (useInitialInterval) {
                        Log.d(TAG, "使用初始快速轮询间隔: ${INITIAL_POLLING_INTERVAL}秒")
                    }
                    
                    // 首先获取一次当前轮询间隔
                    try {
                        if (!useInitialInterval) {
                            currentPollingInterval = withTimeoutOrNull(3000) {
                                wooCommerceConfig.pollingInterval.first()
                            } ?: DEFAULT_POLLING_INTERVAL
                            Log.d(TAG, "初始化轮询间隔: ${currentPollingInterval}秒")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "获取初始轮询间隔失败，使用默认值: ${DEFAULT_POLLING_INTERVAL}秒", e)
                        currentPollingInterval = DEFAULT_POLLING_INTERVAL
                    }
                    
                    Log.d(TAG, "轮询主循环启动")
                    
                    try {
                        // 主轮询循环
                        while (isActive && isPollingActive) {
                            try {
                                // 动态获取当前有效轮询间隔
                                val effectiveInterval = if (useInitialInterval) {
                                    INITIAL_POLLING_INTERVAL
                                } else {
                                    // 根据前台状态实时计算间隔
                                    val baseInterval = currentPollingInterval
                                    if (isAppInForeground) {
                                        (baseInterval * FOREGROUND_INTERVAL_FACTOR).toInt().coerceAtLeast(MIN_POLLING_INTERVAL)
                                    } else {
                                        baseInterval
                                    }
                                }
                                
                                // 检查配置有效性
                                val isValid = checkConfigurationValid()
                                if (isValid) {
                                    Log.d(TAG, "开始执行轮询周期，间隔: ${effectiveInterval}秒 (前台状态: $isAppInForeground)")
                                    
                                    // 记录轮询开始时间
                                    val pollStartTime = System.currentTimeMillis()
                                    
                                    // 自适应打印机检查 - 只在应用启动和有必要时检查
                                    if (!initialPollingComplete || (pollStartTime - (latestPolledDate?.time ?: 0)) > 5 * 60 * 1000) { // 5分钟检查一次
                                        checkPrinterConnection()
                                    }
                                    
                                    // 执行轮询操作
                                    pollOrders()
                                    
                                    // 如果这是初始轮询，标记完成
                                    if (useInitialInterval) {
                                        useInitialInterval = false
                                        initialPollingComplete = true
                                        Log.d(TAG, "初始快速轮询完成，切换到正常轮询间隔: ${currentPollingInterval}秒")
                                    }
                                    
                                    // 计算轮询执行时间
                                    val pollExecutionTime = System.currentTimeMillis() - pollStartTime
                                    Log.d(TAG, "轮询执行耗时: ${pollExecutionTime}ms")
                                    
                                    // 计算需要等待的时间，确保按照用户设置的间隔精确轮询
                                    val waitTime = (effectiveInterval * 1000L) - pollExecutionTime
                                    if (waitTime > 0) {
                                        Log.d(TAG, "等待下次轮询: ${waitTime}ms")
                                        delay(waitTime)
                                    } else {
                                        Log.w(TAG, "轮询执行时间超过间隔设置，无需等待立即开始下次轮询")
                                        delay(100) // 短暂等待以避免CPU占用过高
                                    }
                                } else {
                                    Log.d(TAG, "配置未完成，跳过轮询，等待${effectiveInterval}秒后重试")
                                    delay(effectiveInterval * 1000L)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "轮询周期执行出错: ${e.message}", e)
                                // 如果是取消异常，直接退出循环
                                if (e is kotlinx.coroutines.CancellationException) {
                                    Log.d(TAG, "轮询任务被取消，正常退出")
                                    break
                                }
                                // 其他异常继续轮询，但等待指定间隔
                                delay(currentPollingInterval * 1000L)
                            }
                        }
                    } finally {
                        isPollingActive = false
                        Log.d(TAG, "轮询主循环结束，isActive: $isActive, isPollingActive: $isPollingActive")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "轮询任务外层异常: ${e.message}", e)
                    isPollingActive = false
                } finally {
                    Log.d(TAG, "轮询任务结束")
                }
            }
            
            // 验证轮询任务是否成功启动
            serviceScope.launch {
                delay(500) // 给轮询任务一些启动时间
                if (pollingJob?.isActive != true) {
                    Log.w(TAG, "轮询任务启动失败或立即停止")
                    isPollingActive = false
                } else {
                    Log.d(TAG, "轮询任务启动验证成功")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "启动轮询失败: ${e.message}", e)
            isPollingActive = false
        }
    }
    
    /**
     * 启动独立的间隔监听任务
     */
    private fun startIntervalMonitor() {
        if (intervalMonitorJob?.isActive == true) {
            Log.d(TAG, "间隔监听任务已在运行，跳过重复启动")
            return
        }
        
        intervalMonitorJob = serviceScope.launch {
            try {
                wooCommerceConfig.pollingInterval.collect { newInterval ->
                    // 直接更新间隔值，不重启轮询任务
                    if (newInterval != currentPollingInterval && initialPollingComplete) {
                        Log.d(TAG, "检测到轮询间隔变更: ${currentPollingInterval}秒 -> ${newInterval}秒，下次轮询周期生效")
                        currentPollingInterval = newInterval
                    } else if (newInterval != currentPollingInterval) {
                        // 如果还未完成初始轮询，只更新间隔值
                        Log.d(TAG, "更新轮询间隔: ${currentPollingInterval}秒 -> ${newInterval}秒")
                        currentPollingInterval = newInterval
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "间隔监听任务异常: ${e.message}", e)
            }
        }
    }
    
    /**
     * 检查配置是否有效
     * @return 配置是否有效
     */
    private suspend fun checkConfigurationValid(): Boolean {
        return try {
            val siteUrl = wooCommerceConfig.siteUrl.first()
            val consumerKey = wooCommerceConfig.consumerKey.first()
            val consumerSecret = wooCommerceConfig.consumerSecret.first()
            val isValid = siteUrl.isNotBlank() && consumerKey.isNotBlank() && consumerSecret.isNotBlank()
            if (!isValid) {
                Log.w(TAG, "WooCommerce配置无效: siteUrl=${siteUrl.isNotBlank()}, key=${consumerKey.isNotBlank()}, secret=${consumerSecret.isNotBlank()}")
            }
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "检查配置有效性出错", e)
            false
        }
    }

    /**
     * 执行一次轮询
     */
    private suspend fun pollOrders() {
        try {
            // 更新轮询活动时间戳
            updatePollingActivity()
            
            // 记录上次轮询时间，用于日志
            val lastPollTime = latestPolledDate
            
            Log.d(TAG, "【自动打印调试】开始执行轮询，上次轮询时间: ${lastPollTime?.toString() ?: "首次轮询"}")
            
            // 执行轮询
            val result = orderRepository.refreshProcessingOrdersForPolling(lastPollTime)
            
            // 更新最新轮询时间为当前时间
            latestPolledDate = Date()
            
            // 处理结果
            if (result.isSuccess) {
                val orders = result.getOrDefault(emptyList())
                
                // 有新订单时记录日志
                if (orders.isNotEmpty()) {
                    Log.d(TAG, "【自动打印调试】轮询成功，获取了 ${orders.size} 个处理中订单")
                    
                    // 过滤并处理新订单
                    val newOrderCount = processNewOrders(orders)
                    
                    // 只有真正有新订单时才发送广播
                    if (newOrderCount > 0) {
                        // 发送广播通知界面更新
                        sendOrdersUpdatedBroadcast()
                        
                        // 发送新订单广播
                        sendNewOrdersBroadcast(newOrderCount)
                        
                        // 通知UI层刷新数据
                        sendRefreshOrdersBroadcast()
                    } else {
                        Log.d(TAG, "【自动打印调试】虽然获取了订单，但都是已处理过的，不发送广播")
                    }
                } else {
                    // 减少无新订单时的日志输出频率
                    val minutes = System.currentTimeMillis() / 60000
                    if (!initialPollingComplete || (minutes % 10L == 0L)) { // 每10分钟记录一次，修改为Long类型比较
                        Log.d(TAG, "【自动打印调试】轮询成功，但没有新的处理中订单")
                    }
                }
            } else {
                // 处理错误
                val error = result.exceptionOrNull()
                Log.e(TAG, "【自动打印调试】轮询订单失败: ${error?.message}", error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "【自动打印调试】轮询过程中发生异常: ${e.message}", e)
        }
    }

    /**
     * 检查打印机连接状态并尝试重新连接
     * 减少与心跳机制的冲突，降低检查频率
     */
    private suspend fun checkPrinterConnection() {
        try {
            // 获取默认打印机配置
            val printerConfig = settingsRepository.getDefaultPrinterConfig()
            if (printerConfig != null) {
                // 检查打印机连接状态
                val status = printerManager.getPrinterStatus(printerConfig)
                
                // 只在状态为ERROR时进行重连，让心跳机制处理DISCONNECTED状态
                if (status == PrinterStatus.ERROR) {
                    Log.d(TAG, "打印机状态错误，尝试重新连接...")
                    
                    // 延迟一点时间再连接，防止与心跳机制冲突
                    delay(1000)
                    
                    // 使用超时机制避免连接挂起
                    val connected = withTimeoutOrNull(15000) { // 与蓝牙管理器保持一致
                        printerManager.connect(printerConfig)
                    } ?: false
                    
                    if (connected) {
                        Log.d(TAG, "后台服务成功重新连接打印机")
                    } else {
                        Log.e(TAG, "后台服务无法重新连接打印机")
                    }
                } else if (status == PrinterStatus.CONNECTED) {
                    // 降低测试频率，避免与心跳冲突
                    val minutes = System.currentTimeMillis() / 60000
                    if (minutes % 10L == 0L) { // 改为每10分钟测试一次
                        Log.d(TAG, "定期检查：打印机连接状态测试")
                        try {
                            val testResult = printerManager.testConnection(printerConfig)
                            if (!testResult) {
                                Log.w(TAG, "定期检查：打印机测试失败，标记为错误状态")
                                // 不直接重连，让心跳机制处理
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "定期检查：测试连接异常", e)
                        }
                    }
                }
                // DISCONNECTED状态由心跳机制处理，避免重复处理
            } else {
                Log.d(TAG, "没有配置默认打印机")
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查打印机连接状态失败", e)
        }
    }

    private suspend fun processNewOrders(orders: List<Order>): Int {
        var newOrderCount = 0
        
        Log.d(TAG, "处理新订单，共 ${orders.size} 个")
        
        // 确定应用启动后的首次轮询
        val isFirstPolling = latestPolledDate == null
        Log.d(TAG, "是否首次轮询: $isFirstPolling")
        
        // 获取当前时间
        val currentTime = System.currentTimeMillis()
        // 计算5分钟前的时间戳，用于过滤旧订单
        val fiveMinutesAgo = currentTime - (5 * 60 * 1000)
        
        for (order in orders) {
            // 首先检查订单是否已处理，避免在同步块中进行复杂操作
            val isProcessed = synchronized(processedOrderIds) {
                processedOrderIds.contains(order.id)
            }
            
            // 检查是否处理过此订单
            val isNewOrder = !isProcessed
            
            // 首次轮询时，只处理5分钟内的新订单，避免处理历史订单
            val isRecentOrder = if (isFirstPolling) {
                order.dateCreated.time > fiveMinutesAgo
            } else {
                true // 非首次轮询时，所有未处理订单都视为新订单
            }
            
            // 记录订单时间与当前时间的差距
            val timeDiff = (currentTime - order.dateCreated.time) / 1000 // 秒
            Log.d(TAG, "订单 #${order.number} 创建于 ${timeDiff}秒前, 首次轮询: $isFirstPolling, 是否新订单: $isNewOrder, 是否最近订单: $isRecentOrder")
            
            if (isNewOrder && isRecentOrder) {
                Log.d(TAG, "【打印状态保护】处理新订单: #${order.number}, ID: ${order.id}, 状态: ${order.status}, API中打印状态: ${order.isPrinted}")
                
                // 获取数据库中的打印状态 - 数据库是打印状态的真实来源
                val latestOrder = orderRepository.getOrderById(order.id)
                val isPrintedInDb = latestOrder?.isPrinted ?: false
                
                Log.d(TAG, "【打印状态保护】订单 #${order.number} 的数据库打印状态: $isPrintedInDb")
                
                // 创建最终处理的订单对象，以数据库打印状态为准
                val finalOrder = if (latestOrder != null) {
                    // 使用数据库订单，但保留API订单的其他字段
                    order.copy(isPrinted = latestOrder.isPrinted)
                } else {
                    // 如果数据库中没有此订单，使用API订单状态
                    order
                }
                
                Log.d(TAG, "【打印状态保护】最终处理的订单 #${finalOrder.number} 打印状态: ${finalOrder.isPrinted}")
                
                // 不再根据API和数据库的不一致进行更新，而是始终以数据库为主
                // 这确保了手动标记的打印状态不会被API覆盖
                
                // 发送通知
                sendNewOrderNotification(finalOrder)
                
                // 启用自动打印功能，并添加详细日志
                Log.d(TAG, "====== 开始处理订单自动打印 ======")
                
                // 使用最终订单的打印状态判断是否需要打印
                val shouldPrint = !finalOrder.isPrinted && finalOrder.status == "processing"
                Log.d(TAG, "【打印状态保护】是否需要打印: $shouldPrint (最终打印状态=${finalOrder.isPrinted}, status=${finalOrder.status})")
                
                if (shouldPrint) {
                    // 调用printOrder方法处理打印
                    printOrder(finalOrder)
                } else if (finalOrder.isPrinted) {
                    Log.d(TAG, "【打印状态保护】订单 #${finalOrder.number} 已标记为已打印，跳过打印")
                }
                
                // 先标记订单通知已显示，然后再添加到已处理集合
                orderRepository.markOrderNotificationShown(finalOrder.id)
                
                // 添加到已处理集合
                synchronized(processedOrderIds) {
                    processedOrderIds.add(finalOrder.id)
                    
                    // 维护LRU缓存大小
                    if (processedOrderIds.size > MAX_PROCESSED_IDS) {
                        removeOldestProcessedId()
                    }
                }
                
                newOrderCount++
            } else {
                val skipReason = if (!isNewOrder) "已处理过" else "非最近订单"
                Log.d(TAG, "订单跳过处理，原因: $skipReason: #${order.number}")
            }
        }
        
        return newOrderCount
    }
    
    /**
     * 移除最旧的处理过的订单ID
     * 使用LinkedHashSet实现LRU缓存功能
     */
    private fun removeOldestProcessedId() {
        synchronized(processedOrderIds) {
            if (processedOrderIds.isNotEmpty()) {
                val oldestId = processedOrderIds.iterator().next()
                processedOrderIds.remove(oldestId)
                Log.d(TAG, "从处理记录中移除最旧的订单ID: $oldestId, 当前缓存大小: ${processedOrderIds.size}")
            }
        }
    }

    private fun sendNewOrderNotification(order: Order) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.new_order_received))
            .setContentText("订单号: ${order.number}, 金额: ${order.total}")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(order.id.toInt(), notification)
        
        // 在此处可以发送广播，通知应用内的UI组件显示弹窗
        val intent = Intent("com.example.wooauto.NEW_ORDER_RECEIVED")
        intent.putExtra("orderId", order.id)
        sendBroadcast(intent)
    }

    private fun printOrder(order: Order) {
        serviceScope.launch {
            try {
                Log.d(TAG, "【自动打印调试】=====================================")
                Log.d(TAG, "【自动打印调试】开始执行自动打印订单: #${order.number}")
                Log.d(TAG, "【自动打印调试】订单信息: ID=${order.id}, 状态=${order.status}, 已打印=${order.isPrinted}")
                
                // 检查订单是否已打印
                if (order.isPrinted) {
                    Log.d(TAG, "【自动打印调试】订单已打印，跳过: #${order.number}")
                    return@launch
                }
                
                // 检查订单状态是否为"处理中"
                if (order.status != "processing") {
                    Log.d(TAG, "【自动打印调试】订单状态非处理中(${order.status})，不自动打印")
                    Log.d(TAG, "【自动打印调试】提示：只有状态为'processing'的订单才会自动打印")
                    return@launch
                }
                
                // 获取默认打印机配置
                val printerConfig = settingsRepository.getDefaultPrinterConfig()
                if (printerConfig == null) {
                    Log.e(TAG, "【自动打印调试】❌ 未设置默认打印机，无法打印订单")
                    Log.e(TAG, "【自动打印调试】请在设置->打印机设置中添加并设置默认打印机")
                    return@launch
                }
                
                Log.d(TAG, "【自动打印调试】默认打印机: ${printerConfig.name} (${printerConfig.address})")
                
                // 检查是否开启自动打印 - 需要同时检查全局设置和打印机设置
                val globalAutoPrintEnabled = settingsRepository.getAutoPrintEnabled()
                Log.d(TAG, "【自动打印调试】✓ 检查全局自动打印设置: ${if(globalAutoPrintEnabled) "已开启" else "未开启"}")
                Log.d(TAG, "【自动打印调试】✓ 检查打印机自动打印设置: ${if(printerConfig.isAutoPrint) "已开启" else "未开启"}")
                
                if (!globalAutoPrintEnabled) {
                    Log.e(TAG, "【自动打印调试】❌ 全局自动打印功能未开启")
                    Log.e(TAG, "【自动打印调试】请在设置->自动化设置中开启自动打印")
                    return@launch
                }
                
                if (!printerConfig.isAutoPrint) {
                    Log.e(TAG, "【自动打印调试】❌ 打印机配置未开启自动打印")
                    Log.e(TAG, "【自动打印调试】请在打印机设置中开启该打印机的自动打印功能")
                    return@launch
                }
                
                Log.d(TAG, "【自动打印调试】准备自动打印新订单: #${order.number}, 打印机: ${printerConfig.name}")
                
                // 获取用户设置的默认模板类型
                val defaultTemplateType = settingsRepository.getDefaultTemplateType()
                Log.d(TAG, "【自动打印调试】使用默认打印模板: $defaultTemplateType")
                
                // 检查打印机是否已连接，如果未连接则先尝试连接
                val printerStatus = printerManager.getPrinterStatus(printerConfig)
                Log.d(TAG, "【自动打印调试】当前打印机状态: $printerStatus")
                
                if (printerStatus != PrinterStatus.CONNECTED) {
                    Log.d(TAG, "【自动打印调试】打印机未连接，尝试连接打印机: ${printerConfig.name}")
                    
                    // 使用超时机制避免连接挂起
                    val connected = withTimeoutOrNull(15000) { // 与蓝牙管理器保持一致
                        printerManager.connect(printerConfig)
                    } ?: false
                    
                    Log.d(TAG, "【自动打印调试】打印机连接结果: $connected")
                    
                    if (!connected) {
                        Log.e(TAG, "【自动打印调试】无法连接打印机，打印失败: ${printerConfig.name}")
                        return@launch
                    }
                }
                
                // 再次检查订单是否已经被标记为已打印（可能在轮询期间被手动打印）
                val updatedOrder = orderRepository.getOrderById(order.id)
                Log.d(TAG, "【自动打印调试】再次检查订单打印状态: ${updatedOrder?.isPrinted}")
                
                if (updatedOrder?.isPrinted == true) {
                    Log.d(TAG, "【自动打印调试】订单在轮询间隔内已被标记为已打印，跳过打印: #${order.number}")
                    return@launch
                }
                
                // 打印订单
                Log.d(TAG, "【自动打印调试】开始执行打印订单: #${order.number}")
                
                // 使用超时机制避免打印操作挂起
                val printResult = withTimeoutOrNull(30000) { // 30秒超时
                    printerManager.printOrder(order, printerConfig)
                } ?: false
                
                Log.d(TAG, "【自动打印调试】打印结果: ${if (printResult) "成功" else "失败"}")
                
                if (printResult) {
                    Log.d(TAG, "【自动打印调试】打印成功，订单将由打印管理器自动标记为已打印: #${order.number}")
                    
                    // 验证标记是否成功（仅用于日志验证）
                    val finalOrder = orderRepository.getOrderById(order.id)
                    if (finalOrder?.isPrinted == true) {
                        Log.d(TAG, "【自动打印调试】验证：订单 #${order.number} 已被正确标记为已打印")
                    } else {
                        Log.w(TAG, "【自动打印调试】注意：订单 #${order.number} 可能还未被标记为已打印，状态: ${finalOrder?.isPrinted}")
                    }
                } else {
                    Log.e(TAG, "【自动打印调试】打印失败，订单 #${order.number} 维持未打印状态")
                }
            } catch (e: Exception) {
                Log.e(TAG, "【自动打印调试】自动打印订单时发生异常: ${e.message}", e)
            }
        }
    }
    
    /**
     * 发送订单更新广播
     * 通知前端界面刷新订单列表（无论是否有新订单）
     */
    private fun sendOrdersUpdatedBroadcast() {
        Log.d(TAG, "发送订单更新广播")
        val intent = Intent(ACTION_ORDERS_UPDATED)
        sendBroadcast(intent)
    }
    
    /**
     * 发送新订单广播
     * 通知前端有新订单到达
     */
    private fun sendNewOrdersBroadcast(count: Int) {
        Log.d(TAG, "发送新订单广播，订单数量: $count")
        val intent = Intent(ACTION_NEW_ORDERS_RECEIVED)
        intent.putExtra(EXTRA_ORDER_COUNT, count)
        sendBroadcast(intent)
    }

    /**
     * 发送刷新订单的广播
     * 通知UI层刷新订单数据
     */
    private fun sendRefreshOrdersBroadcast() {
        try {
            val intent = Intent("com.example.wooauto.REFRESH_ORDERS")
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            Log.d(TAG, "已发送刷新订单广播")
        } catch (e: Exception) {
            Log.e(TAG, "发送刷新订单广播失败: ${e.message}", e)
        }
    }

    /**
     * 启动定期清理任务
     * 每10分钟清理一次处理队列和缓存
     */
    private fun startPeriodicCleanupTask() {
        serviceScope.launch {
            while (true) {
                try {
                    // 等待清理间隔
                    delay(CLEANUP_INTERVAL)
                    
                    synchronized(processedOrderIds) {
                        // 如果处理队列过大，清理到最大限制的一半
                        if (processedOrderIds.size > MAX_PROCESSED_IDS) {
                            val targetSize = MAX_PROCESSED_IDS / 2
                            val sizeBefore = processedOrderIds.size
                            
                            while (processedOrderIds.size > targetSize) {
                                // 移除最旧的元素
                                removeOldestProcessedId()
                            }
                            
                            Log.d(TAG, "【自动打印调试】已清理订单处理队列: ${sizeBefore} -> ${processedOrderIds.size}")
                        }
                    }
                    
                    // 释放运行时内存
                    System.gc()
                } catch (e: Exception) {
                    Log.e(TAG, "【自动打印调试】定期清理任务异常: ${e.message}", e)
                }
            }
        }
    }

    // 初始化网络监听器
    private fun initNetworkListener() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    val wasOffline = !isNetworkAvailable
                    isNetworkAvailable = true
                    
                    // 只有在确实从离线状态恢复且当前正在轮询时，才触发一次额外轮询
                    if (wasOffline && isPollingActive && initialPollingComplete) {
                        Log.d(TAG, "网络从离线状态恢复，触发一次恢复轮询")
                        serviceScope.launch {
                            try {
                                // 等待网络完全稳定
                                delay(1000)
                                // 只执行一次轮询，不发送额外广播
                                val result = orderRepository.refreshProcessingOrdersForPolling(latestPolledDate)
                                if (result.isSuccess) {
                                    val orders = result.getOrDefault(emptyList())
                                    if (orders.isNotEmpty()) {
                                        Log.d(TAG, "网络恢复轮询成功，获取了 ${orders.size} 个订单")
                                        // 只处理新订单，不发送多余广播
                                        processNewOrders(orders)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "网络恢复后轮询失败: ${e.message}")
                            }
                        }
                    } else {
                        Log.d(TAG, "网络连接已恢复 (无需额外轮询)")
                    }
                }
                
                override fun onLost(network: Network) {
                    super.onLost(network)
                    isNetworkAvailable = false
                    Log.d(TAG, "网络连接已断开")
                }
                
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                     networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    
                    if (hasInternet != isNetworkAvailable) {
                        val previousState = isNetworkAvailable
                        isNetworkAvailable = hasInternet
                        Log.d(TAG, "网络状态变化: $previousState -> $isNetworkAvailable")
                        
                        // 避免频繁的网络质量变化触发轮询
                        if (hasInternet && !previousState && isPollingActive && initialPollingComplete) {
                            Log.d(TAG, "网络质量恢复，但不触发额外轮询（由正常轮询周期处理）")
                        }
                    }
                }
            }
            
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()
                
            connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
            Log.d(TAG, "网络监听器已注册")
        } else {
            Log.d(TAG, "Android版本不支持网络回调，使用传统方式检查网络")
        }
    }

    /**
     * 启动轮询看门狗，定期检查轮询状态
     */
    private fun startPollingWatchdog() {
        if (watchdogJob?.isActive == true) {
            Log.d(TAG, "轮询看门狗已在运行")
            return
        }
        
        watchdogJob = serviceScope.launch {
            try {
                while (isActive) {
                    delay(WATCHDOG_CHECK_INTERVAL)
                    
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastActivity = currentTime - lastPollingActivity
                    
                    // 检查轮询是否正常运行
                    if (!isPollingActive || pollingJob?.isActive != true) {
                        Log.w(TAG, "【看门狗】检测到轮询停止，尝试重新启动")
                        try {
                            startPolling()
                            Log.d(TAG, "【看门狗】轮询重启完成")
                        } catch (e: Exception) {
                            Log.e(TAG, "【看门狗】轮询重启失败: ${e.message}", e)
                        }
                    } else if (timeSinceLastActivity > POLLING_TIMEOUT_THRESHOLD) {
                        Log.w(TAG, "【看门狗】轮询可能卡住，上次活动: ${timeSinceLastActivity}ms前，尝试重启")
                        try {
                            restartPolling()
                            Log.d(TAG, "【看门狗】轮询重启完成")
                        } catch (e: Exception) {
                            Log.e(TAG, "【看门狗】轮询重启失败: ${e.message}", e)
                        }
                    } else {
                        Log.d(TAG, "【看门狗】轮询状态正常，上次活动: ${timeSinceLastActivity}ms前")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "【看门狗】运行异常: ${e.message}", e)
            }
        }
        
        Log.d(TAG, "轮询看门狗已启动")
    }

    /**
     * 更新轮询活动时间戳
     */
    private fun updatePollingActivity() {
        lastPollingActivity = System.currentTimeMillis()
    }

    /**
     * 初始化电源管理和WiFi锁定机制
     * 防止息屏时断网和蓝牙断连
     */
    private fun initPowerManagement() {
        try {
            // 初始化电源管理器
            powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            
            // 创建部分唤醒锁，防止CPU休眠
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WooAuto:PollingWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
            
            // 初始化WiFi管理器
            wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            
            // 创建WiFi锁，防止WiFi在息屏时断开
            wifiLock = wifiManager?.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "WooAuto:PollingWiFiLock"
            )?.apply {
                setReferenceCounted(false)
            }
            
            // 创建高性能WiFi锁，防止WiFi在息屏时断开
            highPerfWifiLock = wifiManager?.createWifiLock(
                WifiManager.WIFI_MODE_FULL,
                "WooAuto:HighPerfWiFiLock"
            )?.apply {
                setReferenceCounted(false)
            }
            
            Log.d(TAG, "【电源管理】电源管理和WiFi锁定初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "【电源管理】初始化失败: ${e.message}", e)
        }
    }

    /**
     * 获取WakeLock，防止CPU休眠
     */
    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(60 * 60 * 1000L) // 1小时超时，防止永久持有
                Log.d(TAG, "【电源管理】已获取WakeLock，防止CPU休眠")
            }
        } catch (e: Exception) {
            Log.e(TAG, "【电源管理】获取WakeLock失败: ${e.message}", e)
        }
    }

    /**
     * 释放WakeLock
     */
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "【电源管理】已释放WakeLock")
            }
        } catch (e: Exception) {
            Log.e(TAG, "【电源管理】释放WakeLock失败: ${e.message}", e)
        }
    }

    /**
     * 获取WiFi锁，防止WiFi断开
     */
    private fun acquireWifiLock() {
        try {
            if (wifiLock?.isHeld != true) {
                wifiLock?.acquire()
                Log.d(TAG, "【电源管理】已获取WiFi锁，防止WiFi断开")
            }
        } catch (e: Exception) {
            Log.e(TAG, "【电源管理】获取WiFi锁失败: ${e.message}", e)
        }
    }

    /**
     * 释放WiFi锁
     */
    private fun releaseWifiLock() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                Log.d(TAG, "【电源管理】已释放WiFi锁")
            }
        } catch (e: Exception) {
            Log.e(TAG, "【电源管理】释放WiFi锁失败: ${e.message}", e)
        }
    }

    /**
     * 检查是否需要忽略电池优化
     */
    private fun checkBatteryOptimization() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                val packageName = packageName
                
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    Log.w(TAG, "【电源管理】应用未被加入电池优化白名单，可能影响后台运行")
                    
                    // 检查是否为激进省电策略设备
                    if (PowerManagementUtils.isAggressivePowerManagementDevice()) {
                        Log.w(TAG, "【电源管理】检测到激进省电策略设备: ${Build.MANUFACTURER}")
                        showAdvancedPowerManagementNotification()
                    } else {
                        showBatteryOptimizationNotification()
                    }
                } else {
                    Log.d(TAG, "【电源管理】应用已被加入电池优化白名单")
                }
                
                // 检查其他省电设置
                checkAdditionalPowerSavingSettings()
            }
        } catch (e: Exception) {
            Log.e(TAG, "【电源管理】检查电池优化状态失败: ${e.message}", e)
        }
    }

    /**
     * 检查其他省电相关设置
     */
    private fun checkAdditionalPowerSavingSettings() {
        try {
            // 检查省电模式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (PowerManagementUtils.isPowerSaveMode(this)) {
                    Log.w(TAG, "【电源管理】设备处于省电模式，可能影响后台运行")
                }
            }
            
            // 检查数据保护模式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (PowerManagementUtils.isDataSaverMode(this)) {
                    Log.w(TAG, "【电源管理】数据保护模式已开启，可能影响网络同步")
                }
            }
            
            // 获取并记录建议
            val recommendations = PowerManagementUtils.getPowerSavingRecommendations(this)
            if (recommendations.isNotEmpty()) {
                Log.i(TAG, "【电源管理】省电优化建议:")
                recommendations.forEachIndexed { index, recommendation ->
                    Log.i(TAG, "【电源管理】${index + 1}. $recommendation")
                }
            }
            
            // 获取网络保持相关建议
            val networkRecommendations = PowerManagementUtils.getNetworkKeepAliveRecommendations(this)
            if (networkRecommendations.isNotEmpty()) {
                Log.i(TAG, "【网络保持】网络保持优化建议:")
                networkRecommendations.forEachIndexed { index, recommendation ->
                    Log.i(TAG, "【网络保持】${index + 1}. $recommendation")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "【电源管理】检查省电设置失败: ${e.message}", e)
        }
    }

    /**
     * 显示电池优化提醒通知
     */
    private fun showBatteryOptimizationNotification() {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("建议关闭电池优化")
                .setContentText("为确保订单同步正常运行，建议将WooAuto加入电池优化白名单")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1002, notification)
            
            Log.d(TAG, "【电源管理】已显示电池优化提醒通知")
        } catch (e: Exception) {
            Log.e(TAG, "【电源管理】显示电池优化通知失败: ${e.message}", e)
        }
    }

    /**
     * 显示高级电源管理提醒通知（针对激进省电策略设备）
     */
    private fun showAdvancedPowerManagementNotification() {
        try {
            val guidance = PowerManagementUtils.getManufacturerSpecificGuidance()
            
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("检测到${Build.MANUFACTURER}设备")
                .setContentText("该设备可能有激进的省电策略，点击查看优化建议")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "为确保WooAuto正常运行，建议进行以下设置：\n\n$guidance"
                ))
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1003, notification)
            
            Log.d(TAG, "【电源管理】已显示高级电源管理提醒通知")
        } catch (e: Exception) {
            Log.e(TAG, "【电源管理】显示高级电源管理通知失败: ${e.message}", e)
        }
    }

    /**
     * 获取高性能WiFi锁，防止WiFi在息屏时进入省电模式
     */
    private fun acquireHighPerfWifiLock() {
        try {
            if (highPerfWifiLock?.isHeld != true) {
                highPerfWifiLock?.acquire()
                Log.d(TAG, "【电源管理】已获取高性能WiFi锁，防止WiFi省电")
            }
        } catch (e: Exception) {
            Log.e(TAG, "【电源管理】获取高性能WiFi锁失败: ${e.message}", e)
        }
    }

    /**
     * 释放高性能WiFi锁
     */
    private fun releaseHighPerfWifiLock() {
        try {
            if (highPerfWifiLock?.isHeld == true) {
                highPerfWifiLock?.release()
                Log.d(TAG, "【电源管理】已释放高性能WiFi锁")
            }
        } catch (e: Exception) {
            Log.e(TAG, "【电源管理】释放高性能WiFi锁失败: ${e.message}", e)
        }
    }

    /**
     * 启动网络心跳检测
     * 定期检查网络连接状态，发现断网时主动重连
     */
    private fun startNetworkHeartbeat() {
        if (networkHeartbeatJob?.isActive == true) {
            Log.d(TAG, "【网络心跳】网络心跳检测已在运行")
            return
        }
        
        networkHeartbeatJob = serviceScope.launch {
            try {
                while (isActive && isPollingActive) {
                    delay(NETWORK_HEARTBEAT_INTERVAL)
                    
                    Log.d(TAG, "【网络心跳】执行网络连接检查")
                    
                    val currentTime = System.currentTimeMillis()
                    lastNetworkCheckTime = currentTime
                    
                    // 检查网络连接状态
                    val isConnected = checkNetworkConnectivity()
                    
                    if (!isConnected) {
                        Log.w(TAG, "【网络心跳】检测到网络断开，尝试恢复")
                        handleNetworkDisconnection()
                    } else {
                        Log.d(TAG, "【网络心跳】网络连接正常")
                        networkRetryCount = 0 // 重置重试计数
                        
                        // 额外检查：尝试一个简单的网络请求
                        val canReachInternet = testInternetConnectivity()
                        if (!canReachInternet) {
                            Log.w(TAG, "【网络心跳】虽然WiFi已连接，但无法访问互联网")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "【网络心跳】网络心跳检测异常: ${e.message}", e)
            }
        }
        
        Log.d(TAG, "【网络心跳】网络心跳检测已启动")
    }

    /**
     * 检查网络连接状态
     */
    private fun checkNetworkConnectivity(): Boolean {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            Log.e(TAG, "【网络心跳】检查网络连接状态失败: ${e.message}", e)
            false
        }
    }

    /**
     * 测试互联网连接
     */
    private suspend fun testInternetConnectivity(): Boolean {
        return try {
            withTimeoutOrNull(5000) {
                // 尝试简单的网络请求来测试连接
                val url = java.net.URL("https://www.google.com")
                val connection = url.openConnection()
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.connect()
                true
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "【网络心跳】互联网连接测试失败: ${e.message}")
            false
        }
    }

    /**
     * 处理网络断开情况
     */
    private fun handleNetworkDisconnection() {
        serviceScope.launch {
            try {
                networkRetryCount++
                Log.w(TAG, "【网络心跳】处理网络断开，重试次数: $networkRetryCount/$MAX_NETWORK_RETRY_COUNT")
                
                if (networkRetryCount <= MAX_NETWORK_RETRY_COUNT) {
                    // 尝试重新获取WiFi锁
                    Log.d(TAG, "【网络心跳】重新获取WiFi锁")
                    releaseWifiLock()
                    releaseHighPerfWifiLock()
                    delay(1000)
                    acquireWifiLock()
                    acquireHighPerfWifiLock()
                    
                    // 检查WiFi状态
                    val wifiState = wifiManager?.wifiState
                    Log.d(TAG, "【网络心跳】当前WiFi状态: $wifiState")
                    
                    // 如果WiFi已禁用，尝试启用
                    if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
                        Log.w(TAG, "【网络心跳】WiFi已禁用，尝试启用")
                        try {
                            @Suppress("DEPRECATION")
                            wifiManager?.isWifiEnabled = true
                        } catch (e: Exception) {
                            Log.e(TAG, "【网络心跳】无法启用WiFi (需要用户权限): ${e.message}")
                        }
                    }
                    
                    // 等待网络恢复
                    delay(5000)
                    
                    // 再次检查连接
                    val isReconnected = checkNetworkConnectivity()
                    if (isReconnected) {
                        Log.i(TAG, "【网络心跳】网络连接已恢复")
                        networkRetryCount = 0
                        
                        // 网络恢复后，触发一次立即轮询
                        if (isPollingActive) {
                            Log.d(TAG, "【网络心跳】网络恢复后触发立即轮询")
                            delay(1000) // 稍等片刻确保网络完全稳定
                            try {
                                val result = orderRepository.refreshProcessingOrdersForPolling(latestPolledDate)
                                if (result.isSuccess) {
                                    val orders = result.getOrDefault(emptyList())
                                    Log.d(TAG, "【网络心跳】网络恢复轮询获取了 ${orders.size} 个订单")
                                    if (orders.isNotEmpty()) {
                                        processNewOrders(orders)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "【网络心跳】网络恢复后轮询失败: ${e.message}")
                            }
                        }
                    } else {
                        Log.w(TAG, "【网络心跳】网络连接仍未恢复，将继续监控")
                    }
                } else {
                    Log.e(TAG, "【网络心跳】网络重连失败，已达到最大重试次数")
                    // 显示网络问题通知
                    showNetworkIssueNotification()
                }
            } catch (e: Exception) {
                Log.e(TAG, "【网络心跳】处理网络断开异常: ${e.message}", e)
            }
        }
    }

    /**
     * 显示网络问题通知
     */
    private fun showNetworkIssueNotification() {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("网络连接问题")
                .setContentText("检测到持续的网络连接问题，请检查WiFi设置")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1004, notification)
            
            Log.d(TAG, "【网络心跳】已显示网络问题通知")
        } catch (e: Exception) {
            Log.e(TAG, "【网络心跳】显示网络问题通知失败: ${e.message}", e)
        }
    }
} 