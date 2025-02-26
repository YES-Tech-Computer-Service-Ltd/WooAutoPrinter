package com.example.wooauto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.wooauto.MainActivity
import com.example.wooauto.R
import com.example.wooauto.data.api.RetrofitClient
import com.example.wooauto.data.database.AppDatabase
import com.example.wooauto.data.repositories.OrderRepository
import com.example.wooauto.utils.NotificationHelper
import com.example.wooauto.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date

class BackgroundPollingService : Service() {
    private val TAG = "BGPollingService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var pollingHandler: Handler

    // 使用函数方式实现轮询循环，避免Runnable自引用
    private fun pollOrders() {
        Log.d(TAG, "执行轮询任务")
        serviceScope.launch {
            try {
                pollForNewOrders()
            } catch (e: Exception) {
                Log.e(TAG, "轮询过程中发生错误", e)
            }

            // 获取当前的轮询间隔
            val interval = try {
                preferencesManager.pollingInterval.first()
            } catch (e: Exception) {
                POLLING_DEFAULT_INTERVAL
            }

            // 安排下一次轮询，使用函数引用
            Log.d(TAG, "下一次轮询将在 ${interval} 秒后执行")
            pollingHandler.postDelayed({ pollOrders() }, interval * 1000)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 12345
        private const val NOTIFICATION_CHANNEL_ID = "polling_channel"
        private const val POLLING_DEFAULT_INTERVAL = 60L  // 默认60秒

        fun startService(context: Context) {
            val intent = Intent(context, BackgroundPollingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundPollingService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "后台轮询服务创建")
        preferencesManager = PreferencesManager(this)
        createNotificationChannel()

        // 在 Android 12 及以上版本需要指定前台服务类型，但我们已经在清单文件中声明了
        startForeground(NOTIFICATION_ID, createNotification())

        pollingHandler = Handler(Looper.getMainLooper())
        // 开始轮询
        pollOrders()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "后台轮询服务启动")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "后台轮询服务销毁")
        // 移除所有回调
        pollingHandler.removeCallbacksAndMessages(null)
    }

    private suspend fun pollForNewOrders() {
        Log.d(TAG, "===== 开始后台轮询订单任务 =====")
        try {
            // 获取API凭证
            val websiteUrl = preferencesManager.websiteUrl.first()
            val apiKey = preferencesManager.apiKey.first()
            val apiSecret = preferencesManager.apiSecret.first()
            Log.d(TAG, "API配置: URL=${websiteUrl}, Key=${apiKey.take(4)}***, Secret=${apiSecret.take(4)}***")

            if (websiteUrl.isEmpty() || apiKey.isEmpty() || apiSecret.isEmpty()) {
                Log.w(TAG, "API凭证未配置，无法继续")
                return
            }

            // 初始化API服务
            val apiService = RetrofitClient.getWooCommerceApiService(websiteUrl)
            val orderDao = AppDatabase.getInstance(applicationContext).orderDao()
            val repository = OrderRepository(
                orderDao,
                apiService,
                apiKey,
                apiSecret
            )

            // 获取上次检查时间
            val lastCheckedTime = preferencesManager.lastCheckedDate.first()
            val checkFrom = if (lastCheckedTime == 0L) {
                // 首次运行，使用24小时前的时间
                val fromTime = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                Log.d(TAG, "首次运行，使用24小时前的时间: ${Date(fromTime)}")
                fromTime
            } else {
                Log.d(TAG, "使用上次检查时间: ${Date(lastCheckedTime)}")
                lastCheckedTime
            }

            // 获取新订单
            val checkFromDate = Date(checkFrom)
            Log.d(TAG, "开始获取从 ${checkFromDate} 开始的新订单")
            val result = repository.fetchNewOrders(checkFromDate)

            // 更新上次检查时间为当前时间
            val currentTime = System.currentTimeMillis()
            Log.d(TAG, "更新上次检查时间为当前时间: ${Date(currentTime)}")
            preferencesManager.setLastCheckedDate(currentTime)

            if (result.isSuccess) {
                val orders = result.getOrNull() ?: emptyList()
                Log.d(TAG, "获取到 ${orders.size} 个新订单")
                orders.forEach { order ->
                    Log.d(TAG, "新订单: ID=${order.id}, 编号=${order.number}, 状态=${order.status}")
                    NotificationHelper.showNewOrderNotification(applicationContext, order)
                }
            } else {
                Log.e(TAG, "获取订单失败", result.exceptionOrNull())
            }
        } catch (e: Exception) {
            Log.e(TAG, "轮询订单时发生异常", e)
            e.printStackTrace()
        } finally {
            Log.d(TAG, "===== 后台轮询订单任务结束 =====")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "WooAuto Polling Service"
            val descriptionText = "Background service for polling new orders"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("WooAuto 正在运行")
            .setContentText("正在监控新订单")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}