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
import androidx.core.app.NotificationCompat
import com.example.wooauto.MainActivity
import com.example.wooauto.R
import com.example.wooauto.data.local.WooCommerceConfig
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.models.Order
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.util.*
import javax.inject.Inject
import kotlin.collections.HashSet

@AndroidEntryPoint
class BackgroundPollingService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "woo_auto_polling_channel"
    }

    @Inject
    lateinit var orderRepository: DomainOrderRepository

    @Inject
    lateinit var wooCommerceConfig: WooCommerceConfig

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var latestPolledDate: Date? = null
    private val processedOrderIds = HashSet<Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startPolling()
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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
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

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            wooCommerceConfig.pollingInterval.collectLatest { interval ->
                while (isActive) {
                    try {
                        if (wooCommerceConfig.isConfigured.first()) {
                            pollNewOrders()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    // 等待指定的轮询间隔
                    delay(interval * 1000L)
                }
            }
        }
    }

    private suspend fun pollNewOrders() {
        try {
            // 获取待处理订单
            val result = orderRepository.refreshOrders("processing", latestPolledDate)
            if (result.isSuccess) {
                val orders = result.getOrNull() ?: emptyList()
                if (orders.isNotEmpty()) {
                    processNewOrders(orders)
                    
                    // 更新最后轮询时间
                    latestPolledDate = Date()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun processNewOrders(orders: List<Order>) {
        orders.forEach { order ->
            if (!processedOrderIds.contains(order.id)) {
                sendNewOrderNotification(order)
                playOrderSound(order)
                printOrder(order)
                processedOrderIds.add(order.id)
                
                // 标记订单通知已显示
                orderRepository.markOrderNotificationShown(order.id)
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
        val intent = Intent("com.wooauto.NEW_ORDER_RECEIVED")
        intent.putExtra("orderId", order.id)
        sendBroadcast(intent)
    }

    private fun playOrderSound(order: Order) {
        // 播放声音的逻辑，可以通过调用SoundManager完成
    }

    private fun printOrder(order: Order) {
        serviceScope.launch {
            try {
                // 标记订单为已打印
                orderRepository.markOrderAsPrinted(order.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
} 