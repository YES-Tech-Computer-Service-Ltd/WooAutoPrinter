package com.example.wooauto

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.wooauto.service.BackgroundPollingService
import com.example.wooauto.service.OrderPollingWorker
import com.example.wooauto.utils.LanguageHelper
import com.example.wooauto.utils.NotificationHelper
import com.example.wooauto.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class WooAutoApplication : Application(), Configuration.Provider {

    companion object {
        private const val ORDER_POLLING_WORK = "order_polling_work"
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate() {
        super.onCreate()

        // 初始化 PreferencesManager
        preferencesManager = PreferencesManager(this)

        // Initialize notification channels
        NotificationHelper.createNotificationChannel(this)

        // 初始化语言设置
        applicationScope.launch {
            val languageCode = preferencesManager.language.first()
            LanguageHelper.setLocale(this@WooAutoApplication, languageCode)
        }

        // 启动前台轮询服务 - 新增的代码
        applicationScope.launch {
            // 检查API凭证是否已配置
            val apiKey = preferencesManager.apiKey.first()
            val apiSecret = preferencesManager.apiSecret.first()

            if (apiKey.isNotEmpty() && apiSecret.isNotEmpty()) {
                BackgroundPollingService.startService(this@WooAutoApplication)
            }
        }

        // 保留原来的 WorkManager 设置，虽然它不会提供实时轮询
        // 但是可以作为一个备份机制，确保即使前台服务被系统终止，
        // 仍然能通过 WorkManager 的周期性任务来检查新订单
        scheduleOrderPolling()
    }

    override fun attachBaseContext(base: Context) {
        // 使用系统默认语言作为初始语言
        val defaultLanguage = PreferencesManager.getSystemDefaultLanguage()
        val context = LanguageHelper.updateBaseContextLocale(base, defaultLanguage)
        super.attachBaseContext(context)
    }

    private fun scheduleOrderPolling() {
        applicationScope.launch {
            try {
                // 从 PreferencesManager 获取轮询间隔和API凭证
                val pollingIntervalSeconds = preferencesManager.pollingInterval.first()
                val apiKey = preferencesManager.apiKey.first()
                val apiSecret = preferencesManager.apiSecret.first()

                // 只有在有API凭证的情况下才调度
                if (apiKey.isNotEmpty() && apiSecret.isNotEmpty()) {
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                    // 注意：WorkManager最小间隔是15分钟，即使我们设置更短的时间
                    // 这里保留原始代码，作为备份轮询机制
                    val orderPollingRequest = PeriodicWorkRequestBuilder<OrderPollingWorker>(
                        15, TimeUnit.MINUTES  // 强制使用15分钟作为最小间隔
                    )
                        .setConstraints(constraints)
                        .build()

                    WorkManager.getInstance(this@WooAutoApplication).enqueueUniquePeriodicWork(
                        ORDER_POLLING_WORK,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        orderPollingRequest
                    )
                }
            } catch (e: Exception) {
                // 记录错误但不中断应用启动
                e.printStackTrace()
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}