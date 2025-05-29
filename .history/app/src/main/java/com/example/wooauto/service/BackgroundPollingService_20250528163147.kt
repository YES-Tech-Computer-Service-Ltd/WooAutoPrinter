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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "【服务初始化】后台轮询服务开始初始化")
        
        // 创建通知渠道并启动前台服务
        createNotificationChannel()
        startForeground()
        
        // 注册应用前后台状态监听器
        registerAppStateListener()
        
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
                                isAppInForeground = true
                                adjustPollingInterval()
                            }
                            "android.intent.action.SCREEN_OFF" -> {
                                // 屏幕关闭，一定在后台
                                isAppInForeground = false
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
                    
                    // 使用互斥锁重启轮询以应用新间隔
                    restartPolling()
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
        
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "【服务销毁】开始清理资源")
        
        // 标记轮询为非活动状态
        isPollingActive = false
        
        // 取消所有协程任务
        pollingJob?.cancel()
        intervalMonitorJob?.cancel()
        
        // 取消整个服务协程作用域
        serviceScope.cancel()
        
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
            restartMutex.withLock {
                Log.d(TAG, "重启轮询任务 (已加锁)")
                
                // 标记轮询为非活动状态，停止当前轮询循环
                isPollingActive = false
                
                // 等待当前轮询循环自然结束
                try {
                    pollingJob?.join()
                } catch (e: Exception) {
                    Log.d(TAG, "等待轮询任务结束: ${e.message}")
                }
                pollingJob = null
                
                // 等待间隔监听任务结束
                try {
                    intervalMonitorJob?.join()
                } catch (e: Exception) {
                    Log.d(TAG, "等待间隔监听任务结束: ${e.message}")
                }
                intervalMonitorJob = null
                
                // 短暂延迟确保资源释放
                delay(100)
                
                // 启动新的轮询任务
                startPolling()
            }
        }
    }

    private fun startPolling() {
        if (isPollingActive) {
            Log.w(TAG, "轮询已在运行中，跳过重复启动")
            return
        }
        
        isPollingActive = true
        Log.d(TAG, "开始启动轮询任务")
        
        // 启动独立的间隔监听任务（避免重复创建）
        startIntervalMonitor()
        
        pollingJob = serviceScope.launch {
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
            
            try {
                // 主轮询循环
                while (isActive && isPollingActive) {
                    try {
                        // 使用当前有效轮询间隔
                        val effectiveInterval = if (useInitialInterval) {
                            INITIAL_POLLING_INTERVAL
                        } else {
                            currentPollingInterval
                        }
                        
                        // 检查配置有效性
                        val isValid = checkConfigurationValid()
                        if (isValid) {
                            Log.d(TAG, "开始执行轮询周期，间隔: ${effectiveInterval}秒")
                            
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
                Log.d(TAG, "轮询任务结束")
            }
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
                    // 只有在轮询活动且间隔确实改变且初始轮询完成时才重启
                    if (newInterval != currentPollingInterval && 
                        initialPollingComplete && 
                        isPollingActive &&
                        !restartMutex.isLocked) {
                        Log.d(TAG, "检测到轮询间隔变更: ${currentPollingInterval}秒 -> ${newInterval}秒")
                        currentPollingInterval = newInterval
                        // 重新启动轮询以立即应用新间隔
                        restartPolling()
                    } else if (newInterval != currentPollingInterval) {
                        // 如果不满足重启条件，只更新间隔值
                        Log.d(TAG, "更新轮询间隔但不重启: ${currentPollingInterval}秒 -> ${newInterval}秒")
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
                    
                    // 发送广播通知界面更新
                    sendOrdersUpdatedBroadcast()
                    
                    // 过滤并处理新订单
                    processNewOrders(orders)
                    
                    // 发送新订单广播
                    sendNewOrdersBroadcast(orders.size)
                    
                    // 通知UI层刷新数据
                    sendRefreshOrdersBroadcast()
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
                    
                    // 尝试连接打印机，添加超时控制
                    val connected = withTimeoutOrNull(8000) {
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

    private suspend fun processNewOrders(orders: List<Order>) {
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
                Log.d(TAG, "【自动打印调试】开始执行自动打印订单: #${order.number}")
                
                // 检查订单是否已打印
                if (order.isPrinted) {
                    Log.d(TAG, "【自动打印调试】订单已打印，跳过: #${order.number}")
                    return@launch
                }
                
                // 检查订单状态是否为"处理中"
                if (order.status != "processing") {
                    Log.d(TAG, "【自动打印调试】订单状态非处理中，不自动打印: ${order.status}")
                    return@launch
                }
                
                // 获取默认打印机配置
                val printerConfig = settingsRepository.getDefaultPrinterConfig()
                if (printerConfig == null) {
                    Log.e(TAG, "【自动打印调试】未设置默认打印机，无法打印订单")
                    return@launch
                }
                
                // 检查是否开启自动打印 - 需要同时检查全局设置和打印机设置
                val globalAutoPrintEnabled = settingsRepository.getAutoPrintEnabled()
                Log.d(TAG, "【自动打印调试】检查全局自动打印设置: enabled=$globalAutoPrintEnabled")
                Log.d(TAG, "【自动打印调试】检查打印机自动打印设置: isAutoPrint=${printerConfig.isAutoPrint}")
                
                if (!globalAutoPrintEnabled) {
                    Log.d(TAG, "【自动打印调试】全局自动打印功能未开启，跳过")
                    return@launch
                }
                
                if (!printerConfig.isAutoPrint) {
                    Log.d(TAG, "【自动打印调试】打印机配置未开启自动打印，跳过")
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
                    val connected = withTimeoutOrNull(10000) { // 10秒超时
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
                
                // 如果打印成功，更新订单已打印状态
                if (printResult) {
                    Log.d(TAG, "【自动打印调试】打印成功，现在标记订单为已打印, ID: ${order.id}")
                    val markResult = orderRepository.markOrderAsPrinted(order.id)
                    Log.d(TAG, "【自动打印调试】标记订单为已打印结果: $markResult, 订单ID: ${order.id}")
                    
                    // 验证更新是否成功
                    val finalOrder = orderRepository.getOrderById(order.id)
                    if (finalOrder?.isPrinted == true) {
                        Log.d(TAG, "【自动打印调试】成功验证订单 #${order.number} 已被标记为已打印")
                    } else {
                        Log.e(TAG, "【自动打印调试】警告：订单 #${order.number} 状态更新验证失败，数据库记录: ${finalOrder?.isPrinted}")
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

    // 注册应用前后台状态监听器
    private fun registerAppStateListener() {
        // Implementation of registerAppStateListener method
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
                    Log.d(TAG, "网络连接已恢复")
                    
                    // 如果之前是离线状态，现在恢复了，立即触发一次轮询
                    if (wasOffline && isPollingActive) {
                        Log.d(TAG, "网络恢复，立即触发轮询")
                        serviceScope.launch {
                            // 等待200ms确保网络完全稳定
                            delay(200)
                            try {
                                // 执行一次轮询
                                pollForOrders()
                            } catch (e: Exception) {
                                Log.e(TAG, "网络恢复后轮询失败: ${e.message}")
                            }
                        }
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
                        isNetworkAvailable = hasInternet
                        Log.d(TAG, "网络状态变化: 可用=${isNetworkAvailable}")
                        
                        if (hasInternet && isPollingActive) {
                            Log.d(TAG, "网络质量改善，可以进行轮询")
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
} 