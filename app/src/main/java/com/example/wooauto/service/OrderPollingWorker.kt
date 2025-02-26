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

    private val TAG = "OrderPolling_DEBUG"

    override suspend fun doWork(): Result {
        Log.d(TAG, "===== 开始后台轮询订单任务 =====")
        try {
            // 获取API凭证
            val websiteUrl = preferencesManager.websiteUrl.first()
            val apiKey = preferencesManager.apiKey.first()
            val apiSecret = preferencesManager.apiSecret.first()
            Log.d(TAG, "API配置: URL=${websiteUrl}, Key=${apiKey.take(4)}***, Secret=${apiSecret.take(4)}***")

            if (websiteUrl.isEmpty() || apiKey.isEmpty() || apiSecret.isEmpty()) {
                Log.w(TAG, "API凭证未配置，无法继续")
                return Result.failure()
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
                // 首次运行，使用365天前的时间
                val fromTime = System.currentTimeMillis() - 365 * 24 * 60 * 60 * 1000L
                Log.d(TAG, "首次运行，使用365天前的时间: ${Date(fromTime)}")
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
                    Log.d(TAG, "新订单: ID=${order.id}, 编号=${order.number}, 状态=${order.status}, 日期=${order.dateCreated}")
                    NotificationHelper.showNewOrderNotification(applicationContext, order)
                }
                return Result.success()
            } else {
                Log.e(TAG, "获取订单失败", result.exceptionOrNull())
                return Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "轮询订单时发生异常", e)
            e.printStackTrace()
            return Result.retry()
        } finally {
            Log.d(TAG, "===== 后台轮询订单任务结束 =====")
        }
    }

    companion object {
        private const val TAG = "OrderPollingWorker"
    }
}