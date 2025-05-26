package com.example.wooauto.di

import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.data.repository.OrderRepositoryImpl
import com.example.wooauto.data.repository.ProductRepositoryImpl
import com.example.wooauto.data.repository.SettingsRepositoryImpl
import com.example.wooauto.domain.repositories.DomainProductRepository
import com.example.wooauto.data.repositories.TemplateConfigRepositoryImpl
import com.example.wooauto.domain.repositories.DomainTemplateConfigRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
    @Singleton
    abstract fun bindOrderRepository(
        repository: OrderRepositoryImpl
    ): DomainOrderRepository

    /**
     * 绑定ProductRepository接口与其实现
     * @param repository ProductRepositoryImpl实例
     * @return ProductRepository实例
     */
    @Binds
    @Singleton
    abstract fun bindProductRepository(
        repository: ProductRepositoryImpl
    ): DomainProductRepository

    /**
     * 绑定SettingRepository接口与其实现
     * @param repository SettingsRepositoryImpl实例
     * @return SettingRepository实例
     */
    @Binds
    @Singleton
    abstract fun bindSettingRepository(
        repository: SettingsRepositoryImpl
    ): DomainSettingRepository

    /**
     * 绑定模板配置仓库实现
     * @param impl 实现类
     * @return 接口
     */
    @Binds
    @Singleton
    abstract fun bindTemplateConfigRepository(
        impl: TemplateConfigRepositoryImpl
    ): DomainTemplateConfigRepository
} 