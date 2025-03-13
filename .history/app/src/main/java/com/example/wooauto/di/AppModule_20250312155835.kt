package com.example.wooauto.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 应用模块
 * 提供应用级别的依赖
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 提供应用Context
     * 这不是必需的，因为Hilt已经自动提供了@ApplicationContext
     * 但为了明确依赖图，我们显式声明
     */
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }
} 