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
import androidx.lifecycle.lifecycleScope
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
import java.util.*
import javax.inject.Inject
import kotlin.collections.HashSet
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.BroadcastReceiver

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
        
        // 前台状态变化广播
        const val ACTION_FOREGROUND_STATE_CHANGED = "com.example.wooauto.FOREGROUND_STATE_CHANGED"
        
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
    private var cleanupJob: Job? = null
    private var latestPolledDate: Date? = null
    
    // 使用LRU方式管理处理过的订单ID，减少内存占用
    private val processedOrderIds = LinkedHashSet<Long>(MAX_PROCESSED_IDS)
    
    // 轮询控制参数
    private var currentPollingInterval = DEFAULT_POLLING_INTERVAL
    private var isAppInForeground = false // 追踪应用是否在前台
    private var initialPollingComplete = false // 标记初始快速轮询是否完成
    
    // 定义一个在前台时使用较短轮询间隔的倍数因子
    private val FOREGROUND_INTERVAL_FACTOR = 0.5f // 前台时轮询间隔是后台的一半
    
    // 前台状态广播接收器
    private val foregroundStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_FOREGROUND_STATE_CHANGED) {
                val isInForeground = intent.getBooleanExtra("isInForeground", false)
                isAppInForeground = isInForeground
                adjustPollingInterval()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            // 读取轮询配置
            serviceScope.launch {
                try {
                    val savedInterval = wooCommerceConfig.pollingInterval.first()
                    currentPollingInterval = savedInterval
                } catch (e: Exception) {
                    Log.e(TAG, "获取轮询间隔失败，使用默认值: $DEFAULT_POLLING_INTERVAL")
                    currentPollingInterval = DEFAULT_POLLING_INTERVAL
                }
            }
            
            // 注册前台状态监听器
            try {
                LocalBroadcastManager.getInstance(this).registerReceiver(
                    foregroundStateReceiver,
                    IntentFilter(ACTION_FOREGROUND_STATE_CHANGED)
                )
            } catch (e: Exception) {
                Log.e(TAG, "注册前台状态监听器失败: ${e.message}", e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "【服务初始化】初始化失败: ${e.message}", e)
        }
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
        // 获取当前保存的轮询间隔设置
        var baseInterval = DEFAULT_POLLING_INTERVAL
        
        // 尝试安全地获取Flow的当前值
        serviceScope.launch {
            try {
                baseInterval = wooCommerceConfig.pollingInterval.first()
            } catch (e: Exception) {
                Log.e(TAG, "获取轮询间隔失败，使用默认值: $DEFAULT_POLLING_INTERVAL")
            }
        }
        
        currentPollingInterval = if (isAppInForeground) {
            // 前台使用较短的间隔
            (baseInterval * FOREGROUND_INTERVAL_FACTOR).toInt().coerceAtLeast(MIN_POLLING_INTERVAL)
        } else {
            // 后台使用正常间隔
            baseInterval
        }
        
        Log.d(TAG, "调整轮询间隔: ${currentPollingInterval}秒 (前台状态: $isAppInForeground)")
        
        // 重启轮询以应用新间隔
        restartPolling()
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
        
        // 启动定期清理任务（只在首次启动时）
        if (cleanupJob == null || cleanupJob?.isCompleted == true) {
            startPeriodicCleanupTask()
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        cleanupJob?.cancel()
        serviceScope.cancel()
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
        Log.d(TAG, "重启轮询任务")
        pollingJob?.cancel()
        startPolling()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            // 应用启动后先使用短间隔进行初始轮询
            var useInitialInterval = !initialPollingComplete
            
            if (useInitialInterval) {
                Log.d(TAG, "使用初始快速轮询间隔: ${INITIAL_POLLING_INTERVAL}秒")
            }
            
            // 首先获取一次当前轮询间隔
            val initialInterval = try {
                wooCommerceConfig.pollingInterval.first().also { 
                    if (!useInitialInterval) {
                        currentPollingInterval = it
                        Log.d(TAG, "初始化轮询间隔: ${it}秒")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取初始轮询间隔失败，使用默认值: ${DEFAULT_POLLING_INTERVAL}秒", e)
                DEFAULT_POLLING_INTERVAL
            }
            
            // 监听轮询间隔变化
            launch {
                wooCommerceConfig.pollingInterval.collect { newInterval ->
                    if (newInterval != currentPollingInterval && !useInitialInterval) {
                        Log.d(TAG, "轮询间隔变更: ${currentPollingInterval}秒 -> ${newInterval}秒")
                        currentPollingInterval = newInterval
                        // 重新启动轮询以立即应用新间隔
                        restartPolling()
                    }
                }
            }
            
            // 主轮询循环
            performPolling()
        }
    }
    
    private suspend fun performPolling() {
        val effectiveInterval = getEffectivePollingInterval()
        
        while (serviceScope.isActive) {
            try {
                // 检查配置是否完成
                if (!isWooCommerceConfigComplete()) {
                    delay(effectiveInterval * 1000L)
                    continue
                }

                // 执行轮询
                val pollStartTime = System.currentTimeMillis()
                pollForOrders()
                val pollExecutionTime = System.currentTimeMillis() - pollStartTime

                // 计算等待时间
                val waitTime = (effectiveInterval * 1000L) - pollExecutionTime
                if (waitTime > 0) {
                    delay(waitTime)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "轮询周期执行出错: ${e.message}", e)
                delay(effectiveInterval * 1000L)
            }
        }
    }
    
    private suspend fun pollForOrders() {
        try {
            // 检查证书状态
            val app = applicationContext as com.example.wooauto.WooAutoApplication
            val hasEligibility = app.licenseManager.hasEligibility
            if (!hasEligibility) {
                return
            }
            
            // 获取待处理订单
            val pollResult = orderRepository.refreshProcessingOrdersForPolling(latestPolledDate)
            
            if (pollResult.isSuccess) {
                val orders: List<Order> = pollResult.getOrNull() ?: emptyList()
                
                if (orders.isNotEmpty()) {
                    updateLatestPolledDate(orders)
                    
                    // 发送新订单广播
                    sendNewOrdersBroadcast(orders)
                    
                    // 执行自动打印
                    autoProcessOrders(orders)
                }
            } else {
                Log.e(TAG, "轮询订单失败: ${pollResult.exceptionOrNull()?.message}", pollResult.exceptionOrNull())
            }
        } catch (e: Exception) {
            Log.e(TAG, "轮询过程中发生异常: ${e.message}", e)
        }
    }

    private fun updateLatestPolledDate(orders: List<Order>) {
        if (orders.isNotEmpty()) {
            val latestOrder = orders.maxByOrNull { it.dateCreated }
            latestPolledDate = latestOrder?.dateCreated
        }
    }

    private suspend fun autoProcessOrders(orders: List<Order>) {
        processNewOrders(orders)
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
                
                // 检查是否开启自动打印
                Log.d(TAG, "【自动打印调试】检查打印机自动打印设置: isAutoPrint=${printerConfig.isAutoPrint}")
                if (!printerConfig.isAutoPrint) {
                    Log.d(TAG, "【自动打印调试】打印机配置未开启自动打印，跳过")
                    return@launch
                }
                
                Log.d(TAG, "【自动打印调试】准备自动打印新订单: #${order.number}, 打印机: ${printerConfig.name}")
                
                // 获取用户设置的默认模板类型
                val defaultTemplateType = settingsRepository.getDefaultTemplateType() ?: TemplateType.FULL_DETAILS
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
    private fun sendNewOrdersBroadcast(orders: List<Order>) {
        Log.d(TAG, "发送新订单广播，订单数量: ${orders.size}")
        val intent = Intent(ACTION_NEW_ORDERS_RECEIVED)
        intent.putExtra(EXTRA_ORDER_COUNT, orders.size)
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
        cleanupJob?.cancel()
        cleanupJob = serviceScope.launch {
            try {
                while (isActive) {
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
                            
                            Log.d(TAG, "已清理订单处理队列: ${sizeBefore} -> ${processedOrderIds.size}")
                        }
                    }
                    
                    // 释放运行时内存
                    System.gc()
                }
            } catch (e: CancellationException) {
                // 协程被取消是正常情况，不需要记录错误
                Log.d(TAG, "定期清理任务已取消")
            } catch (e: Exception) {
                Log.e(TAG, "定期清理任务异常: ${e.message}", e)
            }
        }
    }

    private suspend fun getEffectivePollingInterval(): Long {
        val baseInterval = currentPollingInterval
        return if (isAppInForeground) {
            (baseInterval * FOREGROUND_INTERVAL_FACTOR).toInt().coerceAtLeast(MIN_POLLING_INTERVAL).toLong()
        } else {
            baseInterval.toLong()
        }
    }

    private suspend fun isWooCommerceConfigComplete(): Boolean {
        return checkConfigurationValid()
    }
} 