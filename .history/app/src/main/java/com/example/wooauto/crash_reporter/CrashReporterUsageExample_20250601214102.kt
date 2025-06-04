package com.example.wooauto.crash_reporter

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * 崩溃报告系统使用示例
 * 展示如何在实际应用中集成和使用增强的crash reporter
 */
class CrashReporterUsageExample : AppCompatActivity() {
    
    private val TAG = "MainActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 记录屏幕变化
        CrashAwareLogger.logScreenChange("Unknown", "MainActivity")
        
        // 使用上下文感知的日志
        CrashAwareLogger.d(TAG, "Activity创建开始")
        
        // 记录方法执行
        CrashAwareLogger.logMethodEntry(TAG, "onCreate", mapOf(
            "savedInstanceState" to (savedInstanceState != null).toString()
        ))
        
        try {
            // 启动性能监控
            val performanceId = PerformanceMonitor.startMonitoring("activity_creation")
            
            // 模拟一些初始化工作
            initializeComponents()
            
            // 结束性能监控
            PerformanceMonitor.endMonitoring(performanceId, mapOf(
                "components_count" to "5"
            ))
            
            CrashAwareLogger.logMethodExit(TAG, "onCreate", "success")
            
        } catch (e: Exception) {
            // 报告非致命异常，包含上下文信息
            CrashReporterManager.reportException(e, mapOf(
                "screen" to "MainActivity",
                "phase" to "onCreate",
                "user_id" to getCurrentUserId()
            ))
            
            CrashAwareLogger.e(TAG, "Activity创建失败", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // 更新应用状态
        AppStateCollector.setAppState("Foreground", "activity_resume")
        CrashAwareLogger.i(TAG, "Activity恢复")
    }
    
    override fun onPause() {
        super.onPause()
        
        // 更新应用状态
        AppStateCollector.setAppState("Background", "activity_pause")
        CrashAwareLogger.i(TAG, "Activity暂停")
    }
    
    /**
     * 模拟用户点击事件
     */
    private fun onButtonClick(buttonId: String) {
        // 记录用户操作
        CrashAwareLogger.logUserAction("MainActivity", "button_click", mapOf(
            "button_id" to buttonId,
            "timestamp" to System.currentTimeMillis().toString()
        ))
        
        try {
            // 模拟可能出错的操作
            when (buttonId) {
                "sync_data" -> performDataSync()
                "load_orders" -> loadOrders()
                "print_receipt" -> printReceipt()
            }
        } catch (e: Exception) {
            // 报告用户操作相关的错误
            CrashReporterUtils.reportUserActionError(
                action = "button_click_$buttonId",
                screen = "MainActivity",
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * 模拟数据同步
     */
    private fun performDataSync() {
        val performanceId = PerformanceMonitor.startMonitoring("data_sync")
        
        try {
            CrashAwareLogger.d(TAG, "开始数据同步")
            
            // 模拟网络请求
            CrashAwareLogger.logNetworkRequest("GET", "https://api.example.com/sync")
            
            // 模拟处理时间
            Thread.sleep(2000)
            
            CrashAwareLogger.logNetworkResponse("GET", "https://api.example.com/sync", 200, 2000)
            CrashAwareLogger.i(TAG, "数据同步完成")
            
        } catch (e: Exception) {
            CrashReporterUtils.reportNetworkError(
                url = "https://api.example.com/sync",
                method = "GET",
                responseCode = 500,
                errorMessage = e.message ?: "网络同步失败"
            )
        } finally {
            PerformanceMonitor.endMonitoring(performanceId)
        }
    }
    
    /**
     * 模拟加载订单
     */
    private fun loadOrders() {
        CrashAwareLogger.d(TAG, "加载订单列表")
        
        // 使用工具函数捕获异常
        val orders = CrashReporterUtils.catchAndReport(
            errorType = "OrderLoading",
            customData = mapOf(
                "screen" to "MainActivity",
                "operation" to "load_orders"
            )
        ) {
            // 模拟数据库查询
            queryOrdersFromDatabase()
        }
        
        if (orders != null) {
            CrashAwareLogger.i(TAG, "成功加载 ${orders.size} 个订单")
        } else {
            CrashAwareLogger.w(TAG, "订单加载失败")
        }
    }
    
    /**
     * 模拟打印收据
     */
    private fun printReceipt() {
        val performanceId = PerformanceMonitor.startMonitoring("print_receipt")
        
        try {
            CrashAwareLogger.d(TAG, "开始打印收据")
            
            // 模拟打印过程
            Thread.sleep(1500)
            
            CrashAwareLogger.i(TAG, "收据打印完成")
            
        } catch (e: Exception) {
            CrashReporterManager.reportError(
                errorType = ErrorTypes.UI_ERROR,
                errorMessage = "打印收据失败: ${e.message}",
                customData = mapOf(
                    "screen" to "MainActivity",
                    "printer_status" to "unknown",
                    "error_category" to "hardware"
                )
            )
        } finally {
            PerformanceMonitor.endMonitoring(performanceId, mapOf(
                "print_success" to "true"
            ))
        }
    }
    
    /**
     * 模拟数据库查询
     */
    private fun queryOrdersFromDatabase(): List<String> {
        // 模拟可能出现的数据库错误
        if (Math.random() < 0.1) { // 10%概率出错
            throw RuntimeException("数据库连接失败")
        }
        
        return listOf("Order1", "Order2", "Order3")
    }
    
    /**
     * 获取当前用户ID
     */
    private fun getCurrentUserId(): String {
        return "user_12345" // 在实际应用中从用户会话中获取
    }
    
    /**
     * 初始化组件
     */
    private fun initializeComponents() {
        CrashAwareLogger.d(TAG, "初始化UI组件")
        // 模拟初始化工作
        Thread.sleep(500)
    }
}

/**
 * 应用生命周期监听示例
 */
class AppLifecycleListener : androidx.lifecycle.DefaultLifecycleObserver {
    
    override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
        super.onStart(owner)
        AppStateCollector.setAppState("Foreground", "lifecycle_start")
        ContextLogger.i("AppLifecycle", "应用进入前台")
    }
    
    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
        super.onStop(owner)
        AppStateCollector.setAppState("Background", "lifecycle_stop")
        ContextLogger.i("AppLifecycle", "应用进入后台")
    }
    
    override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
        super.onDestroy(owner)
        // 清理性能监控数据
        PerformanceMonitor.cleanup()
        ContextLogger.i("AppLifecycle", "应用销毁")
    }
} 