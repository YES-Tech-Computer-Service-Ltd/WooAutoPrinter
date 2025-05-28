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
import com.example.wooauto.licensing.LicenseManager
import com.example.wooauto.licensing.LicenseStatus
import com.example.wooauto.service.BackgroundPollingService
import com.example.wooauto.utils.OrderNotificationManager
import com.example.wooauto.licensing.LicenseVerificationManager
import com.example.wooauto.initialization.InitializationManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    
    @Inject
    lateinit var licenseManager: LicenseManager
    
    @Inject
    lateinit var licenseVerificationManager: LicenseVerificationManager
    
    @Inject
    lateinit var initializationManager: InitializationManager

    // 使用IO调度器而非Main调度器，减少主线程负担
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    // 实现Configuration.Provider接口，提供WorkManager配置
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    // 初始化状态标记
    private var isInitialized = false
    private val initializationLock = Any()

    override fun onCreate() {
        super.onCreate()
        
        try {
            initializeApplicationSafely()
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "【应用初始化】初始化失败", e)
        }
    }

    private fun initializeApplicationSafely() {
        if (isInitialized) {
            return
        }

        try {
            performMainInitialization()
            isInitialized = true
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "【应用初始化】初始化过程中发生异常", e)
        }
    }
    
    private fun performMainInitialization() {
        initializeStage1()
        initializeStage2()
        initializeStage3()
        initializeStage4()
    }

    private fun initializeStage1() {
        initializeLogging()
        initializeDatabase()
    }

    private fun initializeStage2() {
        initializePermissions()
    }

    private fun initializeStage3() {
        try {
            loadConfiguration()
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "【应用初始化】配置加载失败", e)
        }
    }

    private fun initializeStage4() {
        initializeServices()
    }

    private fun initializeLogging() {
        // Timber.plant(Timber.DebugTree())
    }

    private fun initializeDatabase() {
        // Database components preparation
    }

    private fun loadConfiguration() {
        // Configuration loading
    }

    private fun initializePermissions() {
        // Permission-related components preparation
    }

    private fun initializeServices() {
        // Services preparation
    }

    private fun initializeWorkManager() {
        try {
            val config = Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .build()
            WorkManager.initialize(this, config)
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "【应用初始化】WorkManager初始化失败: ${e.message}", e)
        }
    }

    private fun performPostStartupInitialization() {
        try {
            // Legacy initialization tasks
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "【应用初始化】遗留初始化任务失败: ${e.message}", e)
        }
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
            orderNotificationManager = OrderNotificationManager(this)
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "初始化订单通知管理器时出错: ${e.message}", e)
        }
    }
    
    /**
     * 初始化元数据处理系统（懒加载方式）
     */
    private fun initializeMetadataProcessor() {
        try {
            metadataProcessor = MetadataProcessor()
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "初始化元数据处理系统时出错: ${e.message}", e)
        }
    }
    
    /**
     * 初始化证书管理器并执行首次验证
     */
    private fun initializeLicenseManager() {
        try {
            licenseVerificationManager = LicenseVerificationManager.getInstance()
            licenseManager = licenseVerificationManager.licenseManager
            
            GlobalScope.launch(Dispatchers.IO) {
                val isValid = licenseManager.forceRevalidateAndSync(this@WooAutoApplication)
                
                if (!isValid) {
                    Log.w("WooAutoApplication", "证书无效，部分功能可能受限")
                }
            }
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "初始化证书管理器时出错: ${e.message}", e)
        }
    }
    
    fun checkConfigurationAndStartServices() {
        try {
            if (!licenseManager.hasEligibility) {
                return
            }

            if (!isConfigurationComplete()) {
                return
            }

            if (wooCommerceConfig == null) {
                Log.e("WooAutoApplication", "wooCommerceConfig 未初始化")
                return
            }
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "检查配置时出错: ${e.message}", e)
        }
    }

    private fun isConfigurationComplete(): Boolean {
        return try {
            val config = wooCommerceConfig
            if (config != null) {
                val siteUrl = config.siteUrl.orEmpty()
                val consumerKey = config.consumerKey.orEmpty()
                val consumerSecret = config.consumerSecret.orEmpty()
                
                siteUrl.isNotBlank() && consumerKey.isNotBlank() && consumerSecret.isNotBlank()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "检查配置有效性出错: ${e.message}", e)
            false
        }
    }

    private fun startBackgroundServices() {
        try {
            // Starting background polling service
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "启动服务时出错: ${e.message}", e)
        }
    }
} 