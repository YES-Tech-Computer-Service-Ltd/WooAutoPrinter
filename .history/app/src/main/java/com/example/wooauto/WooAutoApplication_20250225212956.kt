package com.example.wooauto

import android.app.Application
import android.content.Context
import android.content.res.Configuration as AndroidConfig
import androidx.work.Configuration as WorkConfig
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.wooauto.service.OrderPollingWorker
import com.example.wooauto.utils.LanguageHelper
import com.example.wooauto.utils.NotificationHelper
import com.example.wooauto.utils.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class WooAutoApplication : Application(), WorkConfig.Provider {

    companion object {
        private const val ORDER_POLLING_WORK = "order_polling_work"
    }

    private lateinit var preferencesManager: PreferencesManager

    override fun attachBaseContext(base: Context) {
        // 在 Application 初始化前使用静态方法获取默认语言
        val languageCode = PreferencesManager.getSystemDefaultLanguage()
        
        // 应用语言设置到基础 Context
        val context = LanguageHelper.updateBaseContextLocale(base, languageCode)
        super.attachBaseContext(context)
    }

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)

        // Initialize notification channels
        NotificationHelper.createNotificationChannel(this)

        // Initialize language based on preferences
        runBlocking {
            val languageCode = preferencesManager.language.first()
            
            // 确保在应用启动时正确设置语言
            LanguageHelper.setLocale(this@WooAutoApplication, languageCode)
        }

        // Schedule background polling for new orders
        scheduleOrderPolling()
    }

    override fun onConfigurationChanged(newConfig: AndroidConfig) {
        super.onConfigurationChanged(newConfig)
        // 确保配置更改时保持语言设置
        runBlocking {
            val languageCode = preferencesManager.language.first()
            LanguageHelper.setLocale(this@WooAutoApplication, languageCode)
        }
    }

    private fun scheduleOrderPolling() {
        runBlocking {
            // 只在有 API 凭证时调度
            val apiKey = preferencesManager.apiKey.first()
            val apiSecret = preferencesManager.apiSecret.first()
            val pollingInterval = preferencesManager.pollingInterval.first().toLong()

            if (apiKey.isNotEmpty() && apiSecret.isNotEmpty()) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val orderPollingRequest = PeriodicWorkRequestBuilder<OrderPollingWorker>(
                    pollingInterval, TimeUnit.SECONDS
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

    override val workManagerConfiguration: WorkConfig
        get() = WorkConfig.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}