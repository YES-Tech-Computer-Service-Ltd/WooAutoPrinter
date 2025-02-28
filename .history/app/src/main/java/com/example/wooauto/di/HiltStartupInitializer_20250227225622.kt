package com.example.wooauto.di

import android.content.Context
import android.util.Log
import androidx.startup.Initializer

/**
 * 简化的启动初始化器
 * 避免在启动时尝试通过EntryPoint访问Hilt组件
 */
class HiltStartupInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        // 简单记录日志，不执行任何可能导致循环依赖的操作
        Log.d("HiltStartupInitializer", "初始化器已启动")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // 返回此初始化器依赖的其他初始化器
        return emptyList()
    }
} 