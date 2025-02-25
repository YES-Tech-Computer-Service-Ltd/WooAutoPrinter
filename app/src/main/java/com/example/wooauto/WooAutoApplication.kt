package com.wooauto

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
import com.example.wooauto.utils.SharedPreferencesManager
import java.util.concurrent.TimeUnit

abstract class WooAutoApplication : Application(), Configuration.Provider {

    companion object {
        private const val ORDER_POLLING_WORK = "order_polling_work"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize language based on preferences
        val prefsManager = SharedPreferencesManager(this)
        LanguageHelper.setLocale(this, prefsManager.getLanguage())

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

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    }
}