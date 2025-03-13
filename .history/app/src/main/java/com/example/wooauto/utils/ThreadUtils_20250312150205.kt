package com.example.wooauto.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 线程处理工具类，确保IO操作和耗时任务在正确的线程上执行
 */
object ThreadUtils {
    private const val TAG = "ThreadUtils"
    
    /**
     * 在IO线程上异步执行操作，避免在主线程上执行耗时任务
     * @param operationName 操作名称，用于日志
     * @param action 要执行的操作
     */
    fun runOnIoThread(operationName: String, action: suspend () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "开始IO操作: $operationName")
                action()
                Log.d(TAG, "完成IO操作: $operationName")
            } catch (e: Exception) {
                Log.e(TAG, "IO操作异常($operationName): ${e.message}", e)
            }
        }
    }
    
    /**
     * 在IO线程上异步执行操作，并在主线程上处理结果
     * @param operationName 操作名称，用于日志
     * @param action 要执行的操作，应返回结果
     * @param onComplete 在主线程上执行的结果处理回调
     * @param onError 在主线程上执行的错误处理回调
     */
    fun <T> runOnIoThreadWithResult(
        operationName: String,
        action: suspend () -> T,
        onComplete: (T) -> Unit,
        onError: (Exception) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "开始IO操作: $operationName")
                val result = action()
                Log.d(TAG, "完成IO操作: $operationName")
                
                // 在主线程上处理结果
                withContext(Dispatchers.Main) {
                    onComplete(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "IO操作异常($operationName): ${e.message}", e)
                
                // 在主线程上处理错误
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }
    
    /**
     * 判断当前是否在主线程上执行
     * @return 如果在主线程上返回true，否则返回false
     */
    fun isOnMainThread(): Boolean {
        return android.os.Looper.getMainLooper().thread == Thread.currentThread()
    }
    
    /**
     * 日志工具方法，记录当前线程信息
     * @param tag 日志标签
     * @param message 日志消息
     */
    fun logThreadInfo(tag: String, message: String) {
        val threadName = Thread.currentThread().name
        val isMainThread = isOnMainThread()
        Log.d(tag, "$message | 线程: $threadName | 主线程: $isMainThread")
    }
} 