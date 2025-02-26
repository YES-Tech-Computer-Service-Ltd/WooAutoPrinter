package com.example.wooauto

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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

        // Schedule background polling for new orders
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
            // 从 PreferencesManager 获取轮询间隔和API凭证
            val pollingIntervalSeconds = preferencesManager.pollingInterval.first()
            val apiKey = preferencesManager.apiKey.first()
            val apiSecret = preferencesManager.apiSecret.first()

            // 只有在有API凭证的情况下才调度
            if (apiKey.isNotEmpty() && apiSecret.isNotEmpty()) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val orderPollingRequest = PeriodicWorkRequestBuilder<OrderPollingWorker>(
                    pollingIntervalSeconds, TimeUnit.SECONDS
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(this@WooAutoApplication).enqueueUniquePeriodicWork(
                    ORDER_POLLING_WORK,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    orderPollingRequest
                )
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}