package com.example.wooauto.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.wooauto.utils.SharedPreferencesManager
import com.example.wooauto.utils.NotificationHelper
import com.example.wooauto.api.WooCommerceApi
import com.example.wooauto.data.Order
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

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 检查API凭证
            if (prefsManager.getApiKey().isEmpty() || prefsManager.getApiSecret().isEmpty()) {
                Log.w(TAG, "API credentials not set")
                return@withContext Result.failure()
            }

            // 初始化WooCommerce API客户端
            val wooCommerceApi = WooCommerceApi(
                applicationContext,
                prefsManager.getApiKey(),
                prefsManager.getApiSecret()
            )

            // 获取上次检查时间后的新订单
            val currentTime = Date()
            val orders = wooCommerceApi.getNewOrders(lastCheckTime)
            lastCheckTime = currentTime

            // 处理新订单
            orders.forEach { order ->
                // 发送通知
                NotificationHelper.showNewOrderNotification(
                    applicationContext,
                    order.number,
                    order.total
                )
                
                // TODO: 保存订单到本地数据库
                // TODO: 自动打印订单（如果启用）
            }

            Log.i(TAG, "Order polling completed successfully: ${orders.size} new orders")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during order polling", e)
            Result.retry()
        }
    }
} 