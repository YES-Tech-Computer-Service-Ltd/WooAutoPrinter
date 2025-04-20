package com.example.wooauto.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.utils.SoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 订单通知管理器
 * 负责接收和处理新订单通知
 */
@Singleton
class OrderNotificationManager @Inject constructor(
    private val context: Context,
    private val orderRepository: DomainOrderRepository,
    private val soundManager: SoundManager
) {
    private val TAG = "OrderNotificationManager"
    private var newOrderReceiver: BroadcastReceiver? = null
    private val mainScope = CoroutineScope(Dispatchers.Main)
    
    // 批量通知处理
    private val pendingOrderIds = ConcurrentHashMap<Long, Boolean>()
    private val isProcessingBatch = AtomicBoolean(false)
    private val isBatchNotificationEnabled = true // 可以通过设置来控制是否开启批量通知
    private val BATCH_PROCESSING_DELAY = 300L // 批量处理延迟，单位毫秒
    
    // 应用启动标记和启动静默期
    private val APP_STARTUP_QUIET_PERIOD = 5000L // 应用启动后5秒内不发送通知
    private val appStartTime = System.currentTimeMillis()
    private var startupSilenceEnded = false
    
    // 存储已处理过的订单ID，防止重复通知
    private val processedOrderIds = CopyOnWriteArraySet<Long>()
    
    // 通知回调接口
    interface NotificationCallback {
        fun onNewOrderReceived(order: Order)
    }
    
    private var callback: NotificationCallback? = null
    
    init {
        // 在初始化时设置启动静默期，经过一段时间后才开始处理通知
        Log.d(TAG, "初始化OrderNotificationManager，设置启动静默期: ${APP_STARTUP_QUIET_PERIOD}ms")
        mainScope.launch {
            delay(APP_STARTUP_QUIET_PERIOD)
            startupSilenceEnded = true
            Log.d(TAG, "应用启动静默期结束，开始处理通知")
        }
    }
    
    /**
     * 注册通知回调
     */
    fun registerCallback(callback: NotificationCallback) {
        this.callback = callback
        Log.d(TAG, "注册通知回调")
    }
    
    /**
     * 注销通知回调
     */
    fun unregisterCallback() {
        this.callback = null
        Log.d(TAG, "注销通知回调")
    }
    
    /**
     * 注册广播接收器
     */
    fun registerReceiver() {
        if (newOrderReceiver != null) {
            Log.d(TAG, "广播接收器已经注册过，不重复注册")
            return
        }
        
        // 在注册接收器时，预先加载已有订单ID，防止重复通知
        mainScope.launch {
            try {
                val existingOrders = withContext(Dispatchers.IO) {
                    orderRepository.getOrders("processing")
                }
                existingOrders.forEach { order ->
                    processedOrderIds.add(order.id)
                }
                Log.d(TAG, "预加载了 ${processedOrderIds.size} 个已有订单ID，防止重复通知")
            } catch (e: Exception) {
                Log.e(TAG, "预加载订单ID失败: ${e.message}", e)
            }
        }
        
        newOrderReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.example.wooauto.NEW_ORDER_RECEIVED") {
                    val orderId = intent.getLongExtra("orderId", -1L)
                    val isInStartupPeriod = !startupSilenceEnded
                    val isAlreadyProcessed = processedOrderIds.contains(orderId)
                    
                    Log.d(TAG, "收到新订单广播: orderId=$orderId, 是否在启动静默期: $isInStartupPeriod, 是否已处理: $isAlreadyProcessed")
                    
                    if (orderId != -1L && !isAlreadyProcessed) {
                        // 如果在启动静默期内，只记录ID但不产生通知
                        if (isInStartupPeriod) {
                            Log.d(TAG, "在应用启动静默期内，记录订单ID但不发送通知: $orderId")
                            processedOrderIds.add(orderId)
                            return
                        }
                        
                        // 添加到已处理ID集合
                        processedOrderIds.add(orderId)
                        
                        // 正常处理通知
                        if (isBatchNotificationEnabled) {
                            // 批量处理模式
                            addToPendingOrders(orderId)
                        } else {
                            // 单独处理模式
                            loadOrderDetails(orderId)
                        }
                    } else if (isAlreadyProcessed) {
                        Log.d(TAG, "订单ID已处理过，忽略: $orderId")
                    }
                }
            }
        }
        
        val filter = IntentFilter("com.example.wooauto.NEW_ORDER_RECEIVED")
        
        // 根据API级别使用相应的注册方法
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(newOrderReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            Log.d(TAG, "使用RECEIVER_NOT_EXPORTED标志注册新订单广播接收器(Android 13+)")
        } else {
            context.registerReceiver(newOrderReceiver, filter)
            Log.d(TAG, "标准方式注册新订单广播接收器(Android 12及以下)")
        }
    }
    
    /**
     * 添加到待处理订单列表
     */
    private fun addToPendingOrders(orderId: Long) {
        pendingOrderIds[orderId] = true
        
        // 如果没有正在处理的批次，启动一个新的处理批次
        if (isProcessingBatch.compareAndSet(false, true)) {
            mainScope.launch {
                // 延迟一小段时间，收集可能同时到达的多个通知
                delay(BATCH_PROCESSING_DELAY)
                processPendingOrders()
            }
        }
    }
    
    /**
     * 处理待处理的订单通知
     */
    private suspend fun processPendingOrders() {
        try {
            val pendingIds = pendingOrderIds.keys.toList()
            pendingOrderIds.clear()
            
            Log.d(TAG, "开始批量处理 ${pendingIds.size} 个订单通知")
            
            if (pendingIds.isNotEmpty()) {
                // 获取所有订单详情
                val orders = mutableListOf<Order>()
                
                for (orderId in pendingIds) {
                    withContext(Dispatchers.IO) {
                        orderRepository.getOrderById(orderId)
                    }?.let { order ->
                        Log.d(TAG, "已加载订单详情: #${order.number}")
                        orders.add(order)
                    }
                }
                
                // 只有在不在启动静默期时才播放声音和发送通知
                if (orders.isNotEmpty() && startupSilenceEnded) {
                    // 只播放一次声音
                    soundManager.playOrderNotificationSound()
                    
                    // 重要：仅向MainActivity发送一个通知，优先使用最近的订单
                    val newestOrder = orders.maxByOrNull { it.dateCreated.time }
                    newestOrder?.let { order ->
                        Log.d(TAG, "向MainActivity发送批量通知，显示最新订单 #${order.number}，实际共有 ${orders.size} 个新订单")
                        callback?.onNewOrderReceived(order)
                    }
                } else if (!startupSilenceEnded) {
                    Log.d(TAG, "在启动静默期内，跳过通知声音和UI显示，订单数量: ${orders.size}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "批量处理订单时出错: ${e.message}", e)
        } finally {
            // 检查是否有新的待处理订单
            if (pendingOrderIds.isNotEmpty()) {
                // 有新的待处理订单，继续处理
                processPendingOrders()
            } else {
                // 没有更多待处理订单，重置处理状态
                isProcessingBatch.set(false)
            }
        }
    }
    
    /**
     * 注销广播接收器
     */
    fun unregisterReceiver() {
        newOrderReceiver?.let {
            try {
                context.unregisterReceiver(it)
                newOrderReceiver = null
                Log.d(TAG, "已注销新订单广播接收器")
            } catch (e: Exception) {
                Log.e(TAG, "注销广播接收器失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 加载订单详情
     */
    private fun loadOrderDetails(orderId: Long) {
        mainScope.launch {
            try {
                Log.d(TAG, "开始加载订单详情: $orderId")
                val order = withContext(Dispatchers.IO) {
                    orderRepository.getOrderById(orderId)
                }
                
                if (order != null) {
                    Log.d(TAG, "成功加载订单详情: #${order.number}")
                    
                    // 只有在不在启动静默期时才播放声音和发送通知
                    if (startupSilenceEnded) {
                        soundManager.playOrderNotificationSound()
                        callback?.onNewOrderReceived(order)
                    } else {
                        Log.d(TAG, "在启动静默期内，记录但不通知: #${order.number}")
                    }
                } else {
                    Log.e(TAG, "加载订单详情失败，未找到订单: $orderId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载订单详情时出错: ${e.message}", e)
            }
        }
    }
    
    /**
     * 标记订单为已读
     */
    fun markOrderAsRead(orderId: Long) {
        mainScope.launch {
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
    fun markOrderAsPrinted(orderId: Long, callback: (Order?) -> Unit) {
        mainScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    orderRepository.markOrderAsPrinted(orderId)
                }
                if (success) {
                    Log.d(TAG, "成功标记订单为已打印: $orderId")
                    // 刷新订单数据
                    val updatedOrder = withContext(Dispatchers.IO) {
                        orderRepository.getOrderById(orderId)
                    }
                    callback(updatedOrder)
                } else {
                    Log.e(TAG, "标记订单为已打印失败: $orderId")
                    callback(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "标记订单为已打印时出错: ${e.message}", e)
                callback(null)
            }
        }
    }
    
    /**
     * 清理缓存，用于测试
     */
    fun clearProcessedIds() {
        processedOrderIds.clear()
        Log.d(TAG, "已清除所有处理过的订单ID")
    }
} 