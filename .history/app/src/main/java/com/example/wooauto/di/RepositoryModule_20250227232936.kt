package com.example.wooauto.di

import com.example.wooauto.data.repository.OrderRepositoryImpl
import com.example.wooauto.data.repository.ProductRepositoryImpl
import com.example.wooauto.data.repository.SettingsRepositoryImpl
import com.example.wooauto.domain.repository.OrderRepository
import com.example.wooauto.domain.repository.ProductRepository
import com.example.wooauto.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindProductRepository(
        productRepositoryImpl: ProductRepositoryImpl
    ): ProductRepository

    @Binds
    @Singleton
    abstract fun bindOrderRepository(
        orderRepositoryImpl: OrderRepositoryImpl
    ): OrderRepository
} 