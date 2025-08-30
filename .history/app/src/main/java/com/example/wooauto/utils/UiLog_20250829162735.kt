package com.example.wooauto.utils

import android.util.Log
import com.example.wooauto.BuildConfig

/**
 * UI 调试日志工具：在 DEBUG 构建下输出，Release 默认静默。
 * 统一使用 UiLog.d("Tag", "message") 代替 if (BuildConfig.DEBUG) Log.d(...)
 */
object UiLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        }
    }
}


