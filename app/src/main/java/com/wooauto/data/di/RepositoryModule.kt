package com.wooauto.data.di

import com.wooauto.data.repository.OrderRepositoryImpl
import com.wooauto.data.repository.ProductRepositoryImpl
import com.wooauto.data.repository.SettingRepositoryImpl
import com.wooauto.domain.repositories.OrderRepository_domain
import com.wooauto.domain.repositories.ProductRepository_domain
import com.wooauto.domain.repositories.SettingRepository_domain
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 仓库模块
 * 将仓库接口与其实现绑定
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * 绑定OrderRepository接口与其实现
     * @param repository OrderRepositoryImpl实例
     * @return OrderRepository实例
     */
    @Binds
    abstract fun bindOrderRepository(
        repository: OrderRepositoryImpl
    ): OrderRepository_domain

    /**
     * 绑定ProductRepository接口与其实现
     * @param repository ProductRepositoryImpl实例
     * @return ProductRepository实例
     */
    @Binds
    abstract fun bindProductRepository(
        repository: ProductRepositoryImpl
    ): ProductRepository_domain

    /**
     * 绑定SettingRepository接口与其实现
     * @param repository SettingRepositoryImpl实例
     * @return SettingRepository实例
     */
    @Binds
    abstract fun bindSettingRepository(
        repository: SettingRepositoryImpl
    ): SettingRepository_domain
} 