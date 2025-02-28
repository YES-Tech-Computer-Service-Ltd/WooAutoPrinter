package com.wooauto.di

import android.content.Context
import androidx.startup.Initializer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 确保 Hilt 依赖注入在应用启动时正确初始化
 * 这个类解决了在 Application.onCreate 中访问 @Inject 属性太早的问题
 */
class HiltStartupInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        // 获取 HiltInitializerEntryPoint 以触发 Hilt 依赖的初始化
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            HiltInitializerEntryPoint::class.java
        )
        
        // 初始化所需的依赖项，这里我们不需要做任何操作，只是确保 Hilt 会处理所有依赖注入
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // 返回此初始化器依赖的其他初始化器
        return emptyList()
    }

    /**
     * Hilt 入口点接口，用于从 ApplicationComponent 访问依赖项
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HiltInitializerEntryPoint {
        // 如果需要，可以在这里添加获取依赖项的方法
    }
} 