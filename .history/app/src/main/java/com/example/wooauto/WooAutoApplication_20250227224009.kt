package com.example.wooauto

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.wooauto.data.local.WooCommerceConfig
import com.example.wooauto.service.BackgroundPollingService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WooAutoApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var wooCommerceConfig: WooCommerceConfig

    private val applicationScope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    // 实现Configuration.Provider接口，提供WorkManager配置
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        Log.d("WooAutoApplication", "应用程序启动")
        
        // 确保WorkManager正确初始化
        WorkManager.initialize(this, workManagerConfiguration)
        
        // 延迟检查配置，确保Hilt依赖注入有足够时间完成
        mainHandler.postDelayed({
            checkConfigAndStartService()
        }, 1000) // 延迟1秒
    }
    
    private fun checkConfigAndStartService() {
        // 检查配置是否完成，如果完成则启动轮询服务
        applicationScope.launch {
            try {
                // 确保wooCommerceConfig已经被初始化
                if (::wooCommerceConfig.isInitialized) {
                    Log.d("WooAutoApplication", "依赖注入已完成，正在检查配置")
                    if (wooCommerceConfig.isConfigured.first()) {
                        startBackgroundPollingService()
                    } else {
                        Log.d("WooAutoApplication", "配置未完成，不启动服务")
                    }
                } else {
                    Log.e("WooAutoApplication", "wooCommerceConfig 未初始化")
                }
            } catch (e: Exception) {
                Log.e("WooAutoApplication", "检查配置时出错: ${e.message}", e)
            }
        }
    }

    private fun startBackgroundPollingService() {
        try {
            Log.d("WooAutoApplication", "正在启动后台轮询服务")
            val serviceIntent = Intent(this, BackgroundPollingService::class.java)
            startService(serviceIntent)
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "启动服务时出错: ${e.message}", e)
        }
    }
} 