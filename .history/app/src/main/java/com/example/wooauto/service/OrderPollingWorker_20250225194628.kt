package com.example.wooauto.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.wooauto.data.api.RetrofitClient
import com.example.wooauto.data.database.AppDatabase
import com.example.wooauto.data.repositories.OrderRepository
import com.example.wooauto.utils.NotificationHelper
import com.example.wooauto.utils.SharedPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class OrderPollingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val prefsManager = SharedPreferencesManager(context)
    private val TAG = "OrderPollingWorker"
    private var lastCheckTime: Date? = null

    // 初始化订单仓库
    private val orderRepository = OrderRepository(
        AppDatabase.getInstance(context).orderDao(),
        RetrofitClient.getWooCommerceApiService(prefsManager.getWebsiteUrl()),
        prefsManager.getApiKey(),
        prefsManager.getApiSecret()
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 检查API凭证
            if (prefsManager.getApiKey().isEmpty() || prefsManager.getApiSecret().isEmpty()) {
                Log.w(TAG, "API credentials not set")
                return@withContext Result.failure()
            }

            // 获取上次检查时间后的新订单
            val currentTime = Date()
            val result = orderRepository.fetchNewOrders(lastCheckTime ?: Date(0))
            lastCheckTime = currentTime

            if (result.isSuccess) {
                val orders = result.getOrNull() ?: emptyList()
                
                // 处理新订单
                orders.forEach { order ->
                    // 检查订单是否已经显示过通知
                    if (!order.notificationShown) {
                        // 发送通知
                        NotificationHelper.showNewOrderNotification(
                            applicationContext,
                            order
                        )
                        
                        // 标记订单通知已显示
                        orderRepository.markOrderNotificationShown(order.id)
                    }
                }

                Log.i(TAG, "Order polling completed successfully: ${orders.size} new orders")
                Result.success()
            } else {
                Log.e(TAG, "Error fetching orders", result.exceptionOrNull())
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during order polling", e)
            Result.retry()
        }
    }
} 