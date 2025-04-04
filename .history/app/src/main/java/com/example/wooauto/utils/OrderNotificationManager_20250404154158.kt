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
    private val NOTIFICATION_BACKOFF = 50L // 多个通知间隔时间
    
    // 通知回调接口
    interface NotificationCallback {
        fun onNewOrderReceived(order: Order)
    }
    
    private var callback: NotificationCallback? = null
    
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
        
        newOrderReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.example.wooauto.NEW_ORDER_RECEIVED") {
                    val orderId = intent.getLongExtra("orderId", -1L)
                    Log.d(TAG, "收到新订单广播: orderId=$orderId")
                    
                    if (orderId != -1L) {
                        if (isBatchNotificationEnabled) {
                            // 批量处理模式
                            addToPendingOrders(orderId)
                        } else {
                            // 单独处理模式
                            loadOrderDetails(orderId)
                        }
                    }
                }
            }
        }
        
        val filter = IntentFilter("com.example.wooauto.NEW_ORDER_RECEIVED")
        context.registerReceiver(newOrderReceiver, filter)
        Log.d(TAG, "已注册新订单广播接收器")
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
                // 只对第一个订单播放声音
                var isFirstOrder = true
                
                for (orderId in pendingIds) {
                    val order = withContext(Dispatchers.IO) {
                        orderRepository.getOrderById(orderId)
                    }
                    
                    if (order != null) {
                        Log.d(TAG, "成功加载订单详情: #${order.number}")
                        
                        // 提示音只播放一次
                        if (isFirstOrder) {
                            soundManager.playOrderNotificationSound()
                            isFirstOrder = false
                        }
                        
                        callback?.onNewOrderReceived(order)
                        
                        // 添加短暂延迟，避免UI过快更新
                        delay(NOTIFICATION_BACKOFF)
                    }
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
                    callback?.onNewOrderReceived(order)
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
} 