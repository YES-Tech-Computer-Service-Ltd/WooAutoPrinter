package com.example.wooauto

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.wooauto.service.OrderPollingWorker
import com.example.wooauto.utils.LanguageHelper
import com.example.wooauto.utils.NotificationHelper
import com.example.wooauto.utils.SharedPreferencesManager
import java.util.concurrent.TimeUnit

class WooAutoApplication : Application(), Configuration.Provider {

    companion object {
        private const val ORDER_POLLING_WORK = "order_polling_work"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize notification channels
        NotificationHelper.createNotificationChannel(this)

        // Initialize language based on preferences
        val prefsManager = SharedPreferencesManager(this)
        val languageCode = prefsManager.getLanguage()
        
        // 确保在应用启动时正确设置语言
        LanguageHelper.setLocale(this, languageCode)
        
        // 更新系统配置以应用语言设置
        val config = resources.configuration
        val locale = java.util.Locale(languageCode)
        config.setLocale(locale)
        createConfigurationContext(config)
        resources.updateConfiguration(config, resources.displayMetrics)

        // Schedule background polling for new orders
        scheduleOrderPolling()
    }

    override fun attachBaseContext(base: Context) {
        // Apply selected language to application context
        val prefsManager = SharedPreferencesManager(base)
        val languageCode = prefsManager.getLanguage()
        val context = LanguageHelper.updateBaseContextLocale(base, languageCode)
        super.attachBaseContext(context)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 确保配置更改时保持语言设置
        val prefsManager = SharedPreferencesManager(this)
        val languageCode = prefsManager.getLanguage()
        LanguageHelper.setLocale(this, languageCode)
    }

    private fun scheduleOrderPolling() {
        val prefsManager = SharedPreferencesManager(this)
        // Get polling interval in seconds, default to 60 if not set
        val pollingIntervalSeconds = prefsManager.getPollingInterval().toLong()

        // Only schedule if we have API credentials
        if (prefsManager.getApiKey().isNotEmpty() && prefsManager.getApiSecret().isNotEmpty()) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val orderPollingRequest = PeriodicWorkRequestBuilder<OrderPollingWorker>(
                pollingIntervalSeconds, TimeUnit.SECONDS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                ORDER_POLLING_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                orderPollingRequest
            )
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}