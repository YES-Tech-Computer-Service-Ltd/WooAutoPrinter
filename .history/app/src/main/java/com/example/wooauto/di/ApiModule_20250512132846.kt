package com.example.wooauto.di

import com.example.wooauto.data.remote.WooCommerceApiFactory
import com.example.wooauto.data.remote.WooCommerceApiFactoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * API模块
 * 绑定API接口与其实现
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ApiModule {

    /**
     * 绑定WooCommerceApiFactory接口与其实现
     * @param factory WooCommerceApiFactoryImpl实例
     * @return WooCommerceApiFactory实例
     */
    @Binds
    @Singleton
    abstract fun bindWooCommerceApiFactory(
        factory: WooCommerceApiFactoryImpl
    ): WooCommerceApiFactory
} 