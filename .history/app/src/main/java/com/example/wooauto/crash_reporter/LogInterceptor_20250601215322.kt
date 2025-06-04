package com.example.wooauto.crash_reporter

import android.util.Log

/**
 * 日志拦截器
 * 自动拦截Android Log输出并保存到上下文日志缓冲区
 */
object LogInterceptor {
    
    private var isEnabled = false
    private val originalLogHandlers = mutableMapOf<String, ((Int, String?, String, Throwable?) -> Int)>()
    
    /**
     * 启用日志拦截
     */
    fun enable() {
        if (isEnabled) return
        
        isEnabled = true
        
        // 这里可以添加自定义的日志拦截逻辑
        // 由于Android Log类是final的，我们需要通过其他方式来拦截日志
        // 一个简单的方法是提供替代的日志函数
        
        ContextLogger.i("LogInterceptor", "日志拦截器已启用")
    }
    
    /**
     * 禁用日志拦截
     */
    fun disable() {
        if (!isEnabled) return
        
        isEnabled = false
        ContextLogger.i("LogInterceptor", "日志拦截器已禁用")
    }
    
    /**
     * 检查是否启用
     */
    fun isEnabled(): Boolean = isEnabled
}

/**
 * 自定义Logger类
 * 提供与Android Log相同的接口，但会同时保存到上下文日志
 */
object CrashAwareLogger {
    
    private const val MAX_LOG_LENGTH = 4000 // Android Log限制
    
    @JvmStatic
    fun v(tag: String, msg: String): Int {
        ContextLogger.log("VERBOSE", tag, msg)
        return Log.v(tag, msg)
    }
    
    @JvmStatic
    fun v(tag: String, msg: String, tr: Throwable?): Int {
        ContextLogger.log("VERBOSE", tag, msg, tr)
        return Log.v(tag, msg, tr)
    }
    
    @JvmStatic
    fun d(tag: String, msg: String): Int {
        ContextLogger.d(tag, msg)
        return Log.d(tag, msg)
    }
    
    @JvmStatic
    fun d(tag: String, msg: String, tr: Throwable?): Int {
        ContextLogger.log("DEBUG", tag, msg, tr)
        return Log.d(tag, msg, tr)
    }
    
    @JvmStatic
    fun i(tag: String, msg: String): Int {
        ContextLogger.i(tag, msg)
        return Log.i(tag, msg)
    }
    
    @JvmStatic
    fun i(tag: String, msg: String, tr: Throwable?): Int {
        ContextLogger.log("INFO", tag, msg, tr)
        return Log.i(tag, msg, tr)
    }
    
    @JvmStatic
    fun w(tag: String, msg: String): Int {
        ContextLogger.w(tag, msg)
        return Log.w(tag, msg)
    }
    
    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable?): Int {
        ContextLogger.log("WARN", tag, msg, tr)
        return Log.w(tag, msg, tr)
    }
    
    @JvmStatic
    fun w(tag: String, tr: Throwable?): Int {
        ContextLogger.log("WARN", tag, tr?.message ?: "Throwable", tr)
        return Log.w(tag, tr)
    }
    
    @JvmStatic
    fun e(tag: String, msg: String): Int {
        ContextLogger.e(tag, msg)
        return Log.e(tag, msg)
    }
    
    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable?): Int {
        ContextLogger.log("ERROR", tag, msg, tr)
        return Log.e(tag, msg, tr)
    }
    
    @JvmStatic
    fun wtf(tag: String, msg: String): Int {
        ContextLogger.log("WTF", tag, msg)
        return Log.wtf(tag, msg)
    }
    
    @JvmStatic
    fun wtf(tag: String, tr: Throwable?): Int {
        ContextLogger.log("WTF", tag, tr?.message ?: "Throwable", tr)
        return Log.wtf(tag, tr)
    }
    
    @JvmStatic
    fun wtf(tag: String, msg: String, tr: Throwable?): Int {
        ContextLogger.log("WTF", tag, msg, tr)
        return Log.wtf(tag, msg, tr)
    }
    
    /**
     * 记录方法执行
     */
    @JvmStatic
    fun logMethodEntry(tag: String, methodName: String, args: Map<String, Any?> = emptyMap()) {
        val argsStr = if (args.isNotEmpty()) {
            args.entries.joinToString(", ") { "${it.key}=${it.value}" }
        } else ""
        
        ContextLogger.d(tag, "→ $methodName${if (argsStr.isNotEmpty()) "($argsStr)" else "()"}")
    }
    
    /**
     * 记录方法退出
     */
    @JvmStatic
    fun logMethodExit(tag: String, methodName: String, result: Any? = null, durationMs: Long? = null) {
        val resultStr = result?.toString() ?: "void"
        val durationStr = durationMs?.let { " [${it}ms]" } ?: ""
        
        ContextLogger.d(tag, "← $methodName -> $resultStr$durationStr")
    }
    
    /**
     * 记录用户操作
     */
    @JvmStatic
    fun logUserAction(screen: String, action: String, details: Map<String, String> = emptyMap()) {
        AppStateCollector.logUserInteraction(action, null, details)
        ContextLogger.i("UserAction", "[$screen] $action")
    }
    
    /**
     * 记录屏幕变化
     */
    @JvmStatic
    fun logScreenChange(fromScreen: String, toScreen: String) {
        AppStateCollector.setCurrentScreen(toScreen)
        ContextLogger.i("Navigation", "Screen: $fromScreen -> $toScreen")
    }
    
    /**
     * 记录网络请求
     */
    @JvmStatic
    fun logNetworkRequest(method: String, url: String, headers: Map<String, String> = emptyMap()) {
        val headerStr = if (headers.isNotEmpty()) {
            headers.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        } else ""
        
        ContextLogger.d("Network", "$method $url${if (headerStr.isNotEmpty()) " [$headerStr]" else ""}")
    }
    
    /**
     * 记录网络响应
     */
    @JvmStatic
    fun logNetworkResponse(method: String, url: String, responseCode: Int, responseTime: Long) {
        ContextLogger.i("Network", "$method $url -> $responseCode (${responseTime}ms)")
        
        if (responseCode >= 400) {
            ContextLogger.w("Network", "HTTP错误: $responseCode for $method $url")
        }
    }
} 