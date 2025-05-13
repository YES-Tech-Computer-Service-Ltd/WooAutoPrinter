package com.example.wooauto.di

import com.example.wooauto.updater.GitHubUpdater
import com.example.wooauto.updater.UpdaterInterface
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 更新系统模块
 * 提供UpdaterInterface的实现
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdaterModule {
    
    /**
     * 绑定GitHubUpdater作为UpdaterInterface的实现
     */
    @Binds
    @Singleton
    abstract fun bindUpdater(updater: GitHubUpdater): UpdaterInterface
} 