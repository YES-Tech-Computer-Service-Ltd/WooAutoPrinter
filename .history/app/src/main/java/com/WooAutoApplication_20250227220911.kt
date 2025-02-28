package com

import android.app.Application
import android.content.Intent
import com.wooauto.data.local.WooCommerceConfig
import com.wooauto.service.BackgroundPollingService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WooAutoApplication : Application() {

    @Inject
    lateinit var wooCommerceConfig: WooCommerceConfig

    private val applicationScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        
        // 检查配置是否完成，如果完成则启动轮询服务
        applicationScope.launch {
            if (wooCommerceConfig.isConfigured.first()) {
                startBackgroundPollingService()
            }
        }
    }

    private fun startBackgroundPollingService() {
        val serviceIntent = Intent(this, BackgroundPollingService::class.java)
        startService(serviceIntent)
    }
} 