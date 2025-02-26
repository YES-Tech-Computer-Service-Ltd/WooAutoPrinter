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
import com.example.wooauto.utils.SharedPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class WooAutoApplication : Application(), Configuration.Provider {

    companion object {
        private const val ORDER_POLLING_WORK = "order_polling_work"
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Initialize notification channels
        NotificationHelper.createNotificationChannel(this)

        // 使用 DataStore 的 PreferencesManager 初始化语言设置
        val prefsManager = PreferencesManager(this)
        applicationScope.launch {
            val languageCode = prefsManager.language.first()
            LanguageHelper.setLocale(this@WooAutoApplication, languageCode)
        }

        // 为了向后兼容，同时更新 SharedPreferences
        val sharedPrefsManager = SharedPreferencesManager(this)
        applicationScope.launch {
            val languageCode = prefsManager.language.first()
            sharedPrefsManager.setLanguage(languageCode)
        }

        // Schedule background polling for new orders
        scheduleOrderPolling()
    }

    override fun attachBaseContext(base: Context) {
        // 使用 runBlocking 在主线程同步获取语言设置
        val prefsManager = PreferencesManager(base)
        val languageCode = runBlocking {
            try {
                prefsManager.language.first()
            } catch (e: Exception) {
                // 回退到 SharedPreferences
                val sharedPrefsManager = SharedPreferencesManager(base)
                sharedPrefsManager.getLanguage()
            }
        }

        val context = LanguageHelper.updateBaseContextLocale(base, languageCode)
        super.attachBaseContext(context)
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