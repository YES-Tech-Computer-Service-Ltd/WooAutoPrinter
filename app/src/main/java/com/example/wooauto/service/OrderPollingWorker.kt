package com.example.wooauto.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.wooauto.data.api.RetrofitClient
import com.example.wooauto.data.database.AppDatabase
import com.example.wooauto.data.repositories.OrderRepository
import com.example.wooauto.utils.NotificationHelper
import com.example.wooauto.utils.PreferencesManager
import kotlinx.coroutines.flow.first
import java.util.Date

class OrderPollingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val preferencesManager = PreferencesManager(context)

    override suspend fun doWork(): Result {
        try {
            // 获取API凭证
            val websiteUrl = preferencesManager.websiteUrl.first()
            val apiKey = preferencesManager.apiKey.first()
            val apiSecret = preferencesManager.apiSecret.first()

            if (websiteUrl.isEmpty() || apiKey.isEmpty() || apiSecret.isEmpty()) {
                Log.w(TAG, "API凭证未配置")
                return Result.failure()
            }

            // 初始化API服务
            val apiService = RetrofitClient.getWooCommerceApiService(websiteUrl)
            val orderDao = AppDatabase.getInstance(applicationContext).orderDao()
            val repository = OrderRepository(orderDao, apiService, apiKey, apiSecret)

            // 获取上次检查时间，如果不存在则使用24小时前的时间
            val lastCheckedTime = preferencesManager.lastCheckedDate.first()
            val checkFrom = if (lastCheckedTime == 0L) {
                // 首次运行，使用24小时前的时间
                System.currentTimeMillis() - 24 * 60 * 60 * 1000
            } else {
                lastCheckedTime
            }

            // 获取新订单
            val checkFromDate = Date(checkFrom)
            Log.d(TAG, "检查从 ${checkFromDate} 开始的新订单")
            val result = repository.fetchNewOrders(checkFromDate)

            // 更新上次检查时间为当前时间
            preferencesManager.setLastCheckedDate(System.currentTimeMillis())

            if (result.isSuccess) {
                val orders = result.getOrNull() ?: emptyList()
                Log.d(TAG, "获取到 ${orders.size} 个新订单")
                if (orders.isNotEmpty()) {
                    // 显示通知
                    orders.forEach { order ->
                        NotificationHelper.showNewOrderNotification(applicationContext, order)
                    }
                }
                return Result.success()
            } else {
                Log.e(TAG, "获取订单失败", result.exceptionOrNull())
                return Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "轮询订单时发生错误", e)
            return Result.retry()
        }
    }

    companion object {
        private const val TAG = "OrderPollingWorker"
    }
}