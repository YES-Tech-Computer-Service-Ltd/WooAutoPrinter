package com.example.wooauto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
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
import java.util.*
import javax.inject.Inject
import kotlin.collections.HashSet
import com.example.wooauto.utils.SoundManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager

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
    }

    @Inject
    lateinit var orderRepository: DomainOrderRepository

    @Inject
    lateinit var wooCommerceConfig: WooCommerceConfig

    @Inject
    lateinit var printerManager: PrinterManager

    @Inject
    lateinit var settingsRepository: DomainSettingRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var latestPolledDate: Date? = null
    private val processedOrderIds = HashSet<Long>()
    private var currentPollingInterval = 30 // 默认30秒

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground()
        
        // 创建协程作用域
        serviceScope.launch {
            try {
                currentPollingInterval = wooCommerceConfig.pollingInterval.first()
                Log.d(TAG, "服务初始化，当前轮询间隔: ${currentPollingInterval}秒")
            } catch (e: Exception) {
                Log.e(TAG, "初始化轮询间隔失败: ${e.message}")
                currentPollingInterval = 30 // 使用默认值
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
        
        return START_STICKY
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_DEFAULT
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
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "前台服务启动成功")
        } catch (e: Exception) {
            Log.e(TAG, "前台服务启动失败: ${e.message}", e)
            // 降级处理：尝试不带类型启动
            try {
                startForeground(NOTIFICATION_ID, notification)
                Log.d(TAG, "降级方式启动前台服务成功")
            } catch (e2: Exception) {
                Log.e(TAG, "降级方式启动前台服务仍然失败: ${e2.message}", e2)
            }
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
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
            // 首先获取一次当前轮询间隔
            val initialInterval = try {
                wooCommerceConfig.pollingInterval.first().also { 
                    currentPollingInterval = it
                    Log.d(TAG, "初始化轮询间隔: ${it}秒")
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取初始轮询间隔失败，使用默认值: 30秒", e)
                30
            }
            
            // 监听轮询间隔变化
            launch {
                wooCommerceConfig.pollingInterval.collect { newInterval ->
                    if (newInterval != currentPollingInterval) {
                        Log.d(TAG, "轮询间隔变更: ${currentPollingInterval}秒 -> ${newInterval}秒")
                        currentPollingInterval = newInterval
                        // 重新启动轮询以立即应用新间隔
                        restartPolling()
                    }
                }
            }
            
            // 主轮询循环
            while (isActive) {
                try {
                    // 检查配置有效性
                    val isValid = checkConfigurationValid()
                    if (isValid) {
                        Log.d(TAG, "开始执行轮询周期，间隔: ${currentPollingInterval}秒")
                        
                        // 先进行打印机状态检查
                        checkPrinterConnection()
                        
                        // 记录轮询开始时间
                        val pollStartTime = System.currentTimeMillis()
                        
                        // 执行轮询操作
                        pollOrders()
                        
                        // 计算轮询执行时间
                        val pollExecutionTime = System.currentTimeMillis() - pollStartTime
                        Log.d(TAG, "轮询执行耗时: ${pollExecutionTime}ms")
                        
                        // 计算需要等待的时间，确保按照用户设置的间隔精确轮询
                        val waitTime = (currentPollingInterval * 1000L) - pollExecutionTime
                        if (waitTime > 0) {
                            Log.d(TAG, "等待下次轮询: ${waitTime}ms")
                            delay(waitTime)
                        } else {
                            Log.w(TAG, "轮询执行时间超过间隔设置，无需等待立即开始下次轮询")
                            delay(100) // 短暂等待以避免CPU占用过高
                        }
                    } else {
                        Log.d(TAG, "配置未完成，跳过轮询，等待${currentPollingInterval}秒后重试")
                        delay(currentPollingInterval * 1000L)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "轮询周期执行出错: ${e.message}", e)
                    delay(currentPollingInterval * 1000L) // 出错时仍然等待指定间隔
                }
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
            
            // 执行轮询
            val result = orderRepository.refreshProcessingOrdersForPolling(lastPollTime)
            
            // 更新最新轮询时间为当前时间
            latestPolledDate = Date()
            
            // 处理结果
            if (result.isSuccess) {
                val orders = result.getOrDefault(emptyList())
                
                // 有新订单时记录日志
                if (orders.isNotEmpty()) {
                    Log.d(TAG, "轮询成功，获取了 ${orders.size} 个处理中订单")
                    
                    // 发送广播通知界面更新
                    sendOrdersUpdatedBroadcast()
                    
                    // 过滤并处理新订单
                    processNewOrders(orders)
                    
                    // 发送新订单广播
                    sendNewOrdersBroadcast(orders.size)
                    
                    // 通知UI层刷新数据
                    sendRefreshOrdersBroadcast()
                }
            } else {
                // 处理错误
                val error = result.exceptionOrNull()
                Log.e(TAG, "轮询订单失败: ${error?.message}", error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "轮询过程中发生异常: ${e.message}", e)
        }
    }

    /**
     * 检查打印机连接状态并尝试重新连接
     */
    private suspend fun checkPrinterConnection() {
        try {
            // 获取默认打印机配置
            val printerConfig = settingsRepository.getDefaultPrinterConfig()
            if (printerConfig != null) {
                // 检查打印机连接状态
                val status = printerManager.getPrinterStatus(printerConfig)
                Log.d(TAG, "当前打印机状态: $status")
                
                // 如果打印机未连接或处于错误状态，尝试重新连接
                if (status == PrinterStatus.DISCONNECTED || status == PrinterStatus.ERROR) {
                    Log.d(TAG, "打印机未连接，尝试重新连接...")
                    // 尝试连接打印机
                    val connected = printerManager.connect(printerConfig)
                    if (connected) {
                        Log.d(TAG, "成功重新连接打印机")
                    } else {
                        Log.e(TAG, "无法重新连接打印机")
                        // 添加重试逻辑
                        delay(3000) // 等待3秒后重试
                        val retryConnect = printerManager.connect(printerConfig)
                        if (retryConnect) {
                            Log.d(TAG, "第二次尝试连接成功")
                        } else {
                            Log.e(TAG, "第二次尝试连接仍然失败")
                        }
                    }
                } else if (status == PrinterStatus.CONNECTED) {
                    // 即使显示已连接，也发送测试指令确认连接状态
                    Log.d(TAG, "打印机显示已连接，发送测试指令确认状态")
                    val testResult = printerManager.testConnection(printerConfig)
                    if (!testResult) {
                        Log.w(TAG, "打印机测试指令失败，尝试重新连接")
                        val reconnected = printerManager.connect(printerConfig)
                        Log.d(TAG, "重新连接结果: ${if (reconnected) "成功" else "失败"}")
                    }
                }
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
        
        orders.forEach { order ->
            if (!processedOrderIds.contains(order.id)) {
                Log.d(TAG, "处理新订单: #${order.number}, ID: ${order.id}, 状态: ${order.status}, 已打印: ${order.isPrinted}")
                
                // 获取数据库中的打印状态
                val latestOrder = orderRepository.getOrderById(order.id)
                val isPrintedInDb = latestOrder?.isPrinted ?: false
                
                // 检查内存状态和数据库状态是否一致
                if (isPrintedInDb != order.isPrinted) {
                    Log.d(TAG, "订单 #${order.number} 打印状态不一致: 内存=${order.isPrinted}, 数据库=${isPrintedInDb}")
                    
                    // 以内存状态为准，更新数据库
                    if (order.isPrinted && !isPrintedInDb) {
                        Log.d(TAG, "以内存状态为准，将订单 #${order.number} 在数据库中标记为已打印")
                        orderRepository.markOrderAsPrinted(order.id)
                    } else if (!order.isPrinted && isPrintedInDb) {
                        Log.d(TAG, "以内存状态为准，将订单 #${order.number} 在数据库中标记为未打印")
                        orderRepository.markOrderAsUnprinted(order.id)
                    }
                }
                
                // 发送通知
                sendNewOrderNotification(order)
                
                // 播放声音
                playOrderSound()
                
                // 启用自动打印功能，并添加详细日志
                Log.d(TAG, "====== 开始处理订单自动打印 ======")
                
                // 使用内存中的打印状态判断是否需要打印
                val shouldPrint = !order.isPrinted && order.status == "processing"
                Log.d(TAG, "是否需要打印: $shouldPrint (isPrinted=${order.isPrinted}, status=${order.status})")
                
                if (shouldPrint) {
                    // 调用printOrder方法处理打印
                    printOrder(order)
                }
                
                // 添加到已处理集合
                processedOrderIds.add(order.id)
                newOrderCount++
                
                // 标记订单通知已显示
                orderRepository.markOrderNotificationShown(order.id)
            } else {
                Log.d(TAG, "订单已处理过，跳过: #${order.number}")
            }
        }
        
        // 限制已处理订单ID的数量，避免内存泄漏
        if (processedOrderIds.size > 1000) {
            val iterator = processedOrderIds.iterator()
            var count = 0
            while (iterator.hasNext() && count < 500) {
                iterator.next()
                iterator.remove()
                count++
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

    private fun playOrderSound() {
        // 使用声音管理器播放订单提示音
        try {
            val soundManager = SoundManager(applicationContext, settingsRepository)
            soundManager.playOrderNotificationSound()
        } catch (e: Exception) {
            Log.e(TAG, "播放订单声音失败", e)
        }
    }

    private fun printOrder(order: Order) {
        serviceScope.launch {
            try {
                // 检查订单是否已打印
                if (order.isPrinted) {
                    Log.d(TAG, "订单已打印，跳过: #${order.number}")
                    return@launch
                }
                
                // 检查订单状态是否为"处理中"
                if (order.status != "processing") {
                    Log.d(TAG, "订单状态非处理中，不自动打印: ${order.status}")
                    return@launch
                }
                
                // 获取默认打印机配置
                val printerConfig = settingsRepository.getDefaultPrinterConfig()
                if (printerConfig == null) {
                    Log.e(TAG, "未设置默认打印机，无法打印订单")
                    return@launch
                }
                
                // 检查是否开启自动打印
                if (!printerConfig.isAutoPrint) {
                    Log.d(TAG, "打印机配置未开启自动打印，跳过")
                    return@launch
                }
                
                Log.d(TAG, "准备自动打印新订单: #${order.number}, 打印机: ${printerConfig.name}")
                
                // 获取用户设置的默认模板类型
                val defaultTemplateType = settingsRepository.getDefaultTemplateType() ?: TemplateType.FULL_DETAILS
                Log.d(TAG, "使用默认打印模板: $defaultTemplateType")
                
                // 检查打印机是否已连接，如果未连接则先尝试连接
                val printerStatus = printerManager.getPrinterStatus(printerConfig)
                if (printerStatus != PrinterStatus.CONNECTED) {
                    Log.d(TAG, "打印机未连接，尝试连接打印机: ${printerConfig.name}")
                    val connected = printerManager.connect(printerConfig)
                    if (!connected) {
                        Log.e(TAG, "无法连接打印机，打印失败: ${printerConfig.name}")
                        return@launch
                    }
                }
                
                // 再次检查订单是否已经被标记为已打印（可能在轮询期间被手动打印）
                val updatedOrder = orderRepository.getOrderById(order.id)
                if (updatedOrder?.isPrinted == true) {
                    Log.d(TAG, "订单在轮询间隔内已被标记为已打印，跳过打印: #${order.number}")
                    return@launch
                }
                
                // 打印订单
                val printResult = printerManager.printOrder(order, printerConfig)
                Log.d(TAG, "打印结果: ${if (printResult) "成功" else "失败"}")
                
                // 如果打印成功，更新订单已打印状态
                if (printResult) {
                    orderRepository.markOrderAsPrinted(order.id)
                    Log.d(TAG, "已标记订单 #${order.number} 为已打印")
                }
            } catch (e: Exception) {
                Log.e(TAG, "自动打印订单时发生异常: ${e.message}", e)
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
} 