package com.example.wooauto.initialization

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.wooauto.utils.UiLog
import androidx.core.content.ContextCompat
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.service.BackgroundPollingService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InitializationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: DomainSettingRepository
) {
    
    companion object {
        private const val TAG = "InitializationManager"
    }
    
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 初始化状态
    private var configurationLoaded = false
    private var permissionsChecked = false
    private var servicesStarted = false
    
    /**
     * 启动完整的应用初始化流程
     */
    fun startInitialization() {
        initScope.launch {
            try {
                UiLog.d(TAG, "【初始化管理器】开始应用初始化流程")
                
                // 步骤1：加载基础配置
                loadBasicConfiguration()
                
                // 步骤2：检查并请求必要权限（延迟到用户交互时）
                preparePermissionChecks()
                
                // 步骤3：在配置完成后启动服务
                startServices()
                
                UiLog.d(TAG, "【初始化管理器】应用初始化流程完成")
            } catch (e: Exception) {
                Log.e(TAG, "【初始化管理器】初始化过程失败", e)
            }
        }
    }
    
    /**
     * 加载基础配置
     */
    private suspend fun loadBasicConfiguration() {
        UiLog.d(TAG, "【初始化管理器】步骤1：加载基础配置")
        
        try {
            // 预加载关键配置，确保服务启动时可用
            withTimeoutOrNull(5000) {
                // 读取WooCommerce配置
                val config = settingsRepository.getWooCommerceConfig()
                UiLog.d(TAG, "【初始化管理器】预加载配置: 轮询间隔=${config.pollingInterval}秒")
                
                // 读取其他关键配置
                val hasDefaultPrinter = settingsRepository.getDefaultPrinterConfig() != null
                UiLog.d(TAG, "【初始化管理器】是否有默认打印机: $hasDefaultPrinter")
            }
            
            configurationLoaded = true
            UiLog.d(TAG, "【初始化管理器】基础配置加载完成")
        } catch (e: Exception) {
            Log.e(TAG, "【初始化管理器】基础配置加载失败: ${e.message}")
            // 即使失败也继续，使用默认配置
            configurationLoaded = true
        }
    }
    
    /**
     * 准备权限检查（不立即执行）
     */
    private fun preparePermissionChecks() {
        UiLog.d(TAG, "【初始化管理器】步骤2：准备权限检查")
        
        // 权限检查将在用户首次使用相关功能时进行
        // 这里只是标记准备完成
        permissionsChecked = true
        
        UiLog.d(TAG, "【初始化管理器】权限检查准备完成")
    }
    
    /**
     * 启动服务
     */
    private suspend fun startServices() {
        if (!configurationLoaded) {
            Log.w(TAG, "【初始化管理器】配置未加载完成，延迟启动服务")
            return
        }
        
        UiLog.d(TAG, "【初始化管理器】步骤3：启动服务")
        
        try {
            // 检查是否应该启动后台服务
            val shouldStartService = shouldStartBackgroundService()
            
            if (shouldStartService) {
                UiLog.d(TAG, "【初始化管理器】启动后台轮询服务")
                val serviceIntent = Intent(context, BackgroundPollingService::class.java)
                // 兼容低版本：使用 ContextCompat，根据系统版本自动选择 startService/startForegroundService
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (_: NoSuchMethodError) {
                    // 极少数旧系统/自定义 ROM 兜底
                    context.startService(serviceIntent)
                }
            } else {
                UiLog.d(TAG, "【初始化管理器】配置未完成，暂不启动后台服务")
            }
            
            servicesStarted = true
            UiLog.d(TAG, "【初始化管理器】服务启动完成")
        } catch (e: Exception) {
            Log.e(TAG, "【初始化管理器】服务启动失败: ${e.message}")
        }
    }
    
    /**
     * 检查是否应该启动后台服务
     */
    private suspend fun shouldStartBackgroundService(): Boolean {
        return try {
            // 检查是否有基本的WooCommerce配置
            val config = settingsRepository.getWooCommerceConfig()
            val hasSiteUrl = config.siteUrl.isNotBlank()
            val hasConsumerKey = config.consumerKey.isNotBlank()
            val hasConsumerSecret = config.consumerSecret.isNotBlank()
            
            val hasBasicConfig = hasSiteUrl && hasConsumerKey && hasConsumerSecret
            UiLog.d(TAG, "【初始化管理器】基础配置检查: URL=$hasSiteUrl, Key=$hasConsumerKey, Secret=$hasConsumerSecret")
            
            hasBasicConfig
        } catch (e: Exception) {
            Log.e(TAG, "【初始化管理器】配置检查失败: ${e.message}")
            false
        }
    }
    
    /**
     * 重启服务（配置更改后调用）
     */
    fun restartServices() {
        initScope.launch {
            try {
                UiLog.d(TAG, "【初始化管理器】重启服务")
                
                // 发送重启轮询的广播
                val intent = Intent(context, BackgroundPollingService::class.java)
                intent.putExtra(BackgroundPollingService.EXTRA_RESTART_POLLING, true)
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (_: NoSuchMethodError) {
                    context.startService(intent)
                }
                
                UiLog.d(TAG, "【初始化管理器】服务重启完成")
            } catch (e: Exception) {
                Log.e(TAG, "【初始化管理器】服务重启失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取初始化状态
     */
    fun getInitializationStatus(): InitializationStatus {
        return InitializationStatus(
            configurationLoaded = configurationLoaded,
            permissionsChecked = permissionsChecked,
            servicesStarted = servicesStarted
        )
    }
    
    /**
     * 等待初始化完成
     */
    suspend fun waitForInitialization() {
        while (!configurationLoaded || !permissionsChecked || !servicesStarted) {
            delay(100)
        }
    }
}

/**
 * 初始化状态数据类
 */
data class InitializationStatus(
    val configurationLoaded: Boolean,
    val permissionsChecked: Boolean,
    val servicesStarted: Boolean
) {
    val isComplete: Boolean
        get() = configurationLoaded && permissionsChecked && servicesStarted
} 