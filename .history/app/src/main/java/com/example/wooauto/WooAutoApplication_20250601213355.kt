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
import com.example.wooauto.crash_reporter.CrashReporterManager
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
        
        // 首先初始化崩溃报告系统（在任何其他初始化之前）
        initializeCrashReporter()
        
        Log.d("WooAutoApplication", "【应用初始化】开始初始化WooAuto应用")
        
        // 确保WorkManager正确初始化
        initializeWorkManager()
        
        // 使用初始化管理器进行统一初始化
        applicationScope.launch {
            try {
                // 启动协调的初始化流程
                initializationManager.startInitialization()
                
                // 并行进行原有的初始化任务
                performLegacyInitialization()
                
                Log.d("WooAutoApplication", "【应用初始化】主要初始化流程完成")
            } catch (e: Exception) {
                Log.e("WooAutoApplication", "【应用初始化】初始化失败", e)
                // 报告初始化失败
                CrashReporterManager.reportException(e, mapOf(
                    "phase" to "application_initialization",
                    "component" to "main_initialization"
                ))
            }
        }
    }
    
    /**
     * 分阶段初始化，确保正确的启动顺序
     */
    private fun performStageInitialization() {
        applicationScope.launch {
            try {
                // 先检查是否已初始化
                val alreadyInitialized = synchronized(initializationLock) {
                    if (isInitialized) {
                        Log.w("WooAutoApplication", "【应用初始化】应用已初始化，跳过重复初始化")
                        true
                    } else {
                        false
                    }
                }
                
                if (alreadyInitialized) return@launch
                
                // 阶段1：基础组件初始化
                initializeBasicComponents()
                
                // 阶段2：权限相关初始化（延迟到需要时）
                preparePermissionDependentComponents()
                
                // 阶段3：配置加载（在同步块外执行）
                loadConfigurations()
                
                // 阶段4：服务准备（不立即启动）
                prepareServices()
                
                // 标记初始化完成
                synchronized(initializationLock) {
                    isInitialized = true
                }
                Log.d("WooAutoApplication", "【应用初始化】应用初始化完成")
            } catch (e: Exception) {
                Log.e("WooAutoApplication", "【应用初始化】初始化过程中发生异常", e)
            }
        }
    }
    
    /**
     * 阶段1：初始化基础组件
     */
    private fun initializeBasicComponents() {
        Log.d("WooAutoApplication", "【应用初始化】阶段1：初始化基础组件")
        
        // 初始化日志系统
        initializeLogging()
        
        // 初始化数据库相关组件
        initializeDatabase()
        
        Log.d("WooAutoApplication", "【应用初始化】阶段1完成")
    }
    
    /**
     * 阶段2：准备权限相关组件（不立即初始化）
     */
    private fun preparePermissionDependentComponents() {
        Log.d("WooAutoApplication", "【应用初始化】阶段2：准备权限相关组件")
        
        // 只是准备，不立即初始化蓝牙
        // 实际初始化会在权限授予后进行
        
        Log.d("WooAutoApplication", "【应用初始化】阶段2完成")
    }
    
    /**
     * 阶段3：加载配置
     */
    private suspend fun loadConfigurations() {
        Log.d("WooAutoApplication", "【应用初始化】阶段3：加载配置")
        
        try {
            // 预加载关键配置，避免在需要时才加载
            // 这里可以注入ConfigRepository来预加载配置
            
            Log.d("WooAutoApplication", "【应用初始化】配置加载完成")
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "【应用初始化】配置加载失败", e)
        }
        
        Log.d("WooAutoApplication", "【应用初始化】阶段3完成")
    }
    
    /**
     * 阶段4：准备服务（不立即启动）
     */
    private fun prepareServices() {
        Log.d("WooAutoApplication", "【应用初始化】阶段4：准备服务")
        
        // 只是准备服务，不立即启动
        // 服务的启动会在适当的时机进行
        
        Log.d("WooAutoApplication", "【应用初始化】阶段4完成")
    }
    
    private fun initializeLogging() {
        // 初始化日志配置
        Log.d("WooAutoApplication", "【应用初始化】日志系统初始化完成")
    }
    
    private fun initializeDatabase() {
        // 数据库初始化会由Room和Hilt自动处理
        Log.d("WooAutoApplication", "【应用初始化】数据库组件准备完成")
    }
    
    /**
     * 检查应用是否已完成初始化
     */
    fun isApplicationInitialized(): Boolean {
        return synchronized(initializationLock) { isInitialized }
    }
    
    /**
     * 等待应用初始化完成
     */
    suspend fun waitForInitialization() {
        while (!isApplicationInitialized()) {
            kotlinx.coroutines.delay(100)
        }
    }
    
    /**
     * 初始化WorkManager
     */
    private fun initializeWorkManager() {
        try {
            WorkManager.initialize(this, workManagerConfiguration)
            Log.d("WooAutoApplication", "【应用初始化】WorkManager初始化完成")
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "【应用初始化】WorkManager初始化失败: ${e.message}", e)
        }
    }
    
    /**
     * 执行遗留的初始化任务
     */
    private suspend fun performLegacyInitialization() {
        try {
            coroutineScope {
                // 并行执行原有的初始化任务
                val metadataJob = async { initializeMetadataProcessors() }
                val notificationJob = async { initializeOrderNotificationManager() }
                val licenseJob = async { initializeLicenseManager() }
                
                // 等待所有任务完成
                metadataJob.await()
                notificationJob.await()
                licenseJob.await()
                
                Log.d("WooAutoApplication", "【应用初始化】遗留初始化任务完成")
            }
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
    
    /**
     * 初始化证书管理器并执行首次验证
     */
    private fun initializeLicenseManager() {
        try {
            Log.d("WooAutoApplication", "初始化证书管理器")
            
            // 初始化旧版本的LicenseVerificationManager的静态实例访问点
            if (::licenseVerificationManager.isInitialized) {
                LicenseVerificationManager.initialize(licenseVerificationManager)
                Log.d("WooAutoApplication", "已初始化LicenseVerificationManager单例访问点")
            }
            
            // 执行首次证书验证
            licenseManager.verifyLicense(this, applicationScope) { isValid ->
                Log.d("WooAutoApplication", "证书验证结果: $isValid")
                
                // 证书无效时的处理可以放在这里
                if (!isValid) {
                    Log.w("WooAutoApplication", "证书无效，部分功能可能受限")
                }
            }
            
            Log.d("WooAutoApplication", "证书管理器初始化完成")
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "初始化证书管理器时出错: ${e.message}", e)
        }
    }
    
    /**
     * 初始化崩溃报告系统
     */
    private fun initializeCrashReporter() {
        try {
            // 从BuildConfig或配置文件中获取API配置
            val apiEndpoint = "https://yourwordpresssite.com/wp-json/android-crash/v2/report"
            val apiKey = "your_api_key_here" // 应该从安全存储中获取
            
            CrashReporterManager.initialize(
                context = this,
                apiEndpoint = apiEndpoint,
                apiKey = apiKey,
                enableDebugLogs = BuildConfig.DEBUG
            )
            
            Log.d("WooAutoApplication", "【应用初始化】崩溃报告系统初始化完成")
        } catch (e: Exception) {
            Log.e("WooAutoApplication", "【应用初始化】崩溃报告系统初始化失败", e)
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
                        // 检查统一资格是否有效 - 使用新的资格检查系统
                        if (licenseManager.hasEligibility) {
                            // 立即启动服务，不再延迟
                            startBackgroundPollingService()
                        } else {
                            Log.w("WooAutoApplication", "无使用资格，不启动服务")
                            // 如果需要定期检查资格状态，可以在这里启动
                            startLicenseMonitoring()
                        }
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
     * 开始证书状态监控
     */
    private fun startLicenseMonitoring() {
        // 每小时检查一次证书状态
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                if (licenseManager.shouldRevalidate(60)) { // 每60分钟重新验证一次
                    applicationScope.launch {
                        licenseManager.verifyLicense(this@WooAutoApplication, applicationScope) { isValid ->
                            if (isValid) {
                                // 在协程中检查配置有效性
                                applicationScope.launch {
                                    val configValid = checkConfigurationValid()
                                    if (configValid) {
                                        // 证书变为有效时，启动服务
                                        startBackgroundPollingService()
                                    }
                                }
                            }
                        }
                    }
                }
                // 继续定时检查
                mainHandler.postDelayed(this, 60 * 60 * 1000) // 每小时检查一次
            }
        }, 60 * 60 * 1000) // 首次延迟1小时
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