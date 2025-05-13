package com.example.wooauto

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.wooauto.data.local.WooCommerceConfig
import com.example.wooauto.data.remote.metadata.MetadataProcessorFactory
import com.example.wooauto.data.remote.metadata.MetadataProcessorRegistry
import com.example.wooauto.service.BackgroundPollingService
import com.example.wooauto.utils.OrderNotificationManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

@HiltAndroidApp
class WooAutoApplication : MultiDexApplication(), Configuration.Provider {

    @Inject
    lateinit var wooCommerceConfig: WooCommerceConfig
    
    @Inject
    lateinit var orderNotificationManager: OrderNotificationManager

    // 使用IO调度器而非Main调度器，减少主线程负担
    private val applicationScope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    // 实现Configuration.Provider接口，提供WorkManager配置
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        Log.d("WooAutoApplication", "应用程序启动")
        
        // 使用协程并行初始化，提高启动效率
        applicationScope.launch {
            try {
                // 并行执行初始化任务
                coroutineScope {
                    // 初始化元数据处理系统（异步）
                    val metadataJob = async { initializeMetadataProcessors() }
                    
                    // 初始化订单通知管理器（异步）
                    val notificationJob = async { initializeOrderNotificationManager() }
                    
                    // 等待所有初始化完成
                    metadataJob.await()
                    notificationJob.await()
                    
                    Log.d("WooAutoApplication", "所有并行初始化任务完成")
                }
            } catch (e: Exception) {
                Log.e("WooAutoApplication", "并行初始化过程中出错: ${e.message}", e)
            }
        }
        
        // 确保WorkManager正确初始化 - 必须在主线程执行
        try {
            WorkManager.initialize(this, workManagerConfiguration)
            Log.d("WooAutoApplication", "WorkManager初始化完成")
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "WorkManager初始化失败: ${e.message}", e)
        }
        
        // 立即启动服务检查，不再延迟
        checkConfigAndStartService()
    }
    
    override fun onTerminate() {
        super.onTerminate()
        
        // 注销订单通知管理器
        orderNotificationManager.unregisterReceiver()
        Log.d("WooAutoApplication", "应用程序终止")
    }
    
    /**
     * 初始化订单通知管理器
     */
    private fun initializeOrderNotificationManager() {
        try {
            // 注册订单通知接收器
            orderNotificationManager.registerReceiver()
            Log.d("WooAutoApplication", "订单通知管理器初始化完成")
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "初始化订单通知管理器时出错: ${e.message}", e)
        }
    }
    
    /**
     * 初始化元数据处理系统（懒加载方式）
     */
    private fun initializeMetadataProcessors() {
        try {
            Log.d("WooAutoApplication", "初始化元数据处理系统")
            
            // 使用懒加载模式，只创建注册表而不立即初始化所有处理器
            MetadataProcessorFactory.createDefaultRegistry()
            
            // 注册表初始化推迟到首次使用时，减少启动负担
            Log.d("WooAutoApplication", "元数据处理系统初始化完成")
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "初始化元数据处理系统时出错: ${e.message}", e)
        }
    }
    
    private fun checkConfigAndStartService() {
        // 检查配置是否完成，如果完成则启动轮询服务
        applicationScope.launch {
            try {
                // 确保wooCommerceConfig已经被初始化
                if (::wooCommerceConfig.isInitialized) {
                    Log.d("WooAutoApplication", "依赖注入已完成，正在检查配置")
                    // 检查配置是否有效
                    val isConfigValid = checkConfigurationValid()
                    if (isConfigValid) {
                        // 立即启动服务，不再延迟
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
    
    /**
     * 检查配置是否有效
     */
    private suspend fun checkConfigurationValid(): Boolean {
        return try {
            val siteUrl = wooCommerceConfig.siteUrl.first()
            val consumerKey = wooCommerceConfig.consumerKey.first()
            val consumerSecret = wooCommerceConfig.consumerSecret.first()
            
            val isValid = siteUrl.isNotBlank() && consumerKey.isNotBlank() && consumerSecret.isNotBlank()
            if (isValid) {
                Log.d("WooAutoApplication", "WooCommerce配置有效")
            } else {
                Log.w("WooAutoApplication", "WooCommerce配置无效: URL=${siteUrl.isNotBlank()}, Key=${consumerKey.isNotBlank()}, Secret=${consumerSecret.isNotBlank()}")
            }
            isValid
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "检查配置有效性出错: ${e.message}", e)
            false
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