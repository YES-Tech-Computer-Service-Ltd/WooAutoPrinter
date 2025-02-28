package com.wooauto.di

import com.wooauto.domain.repositories.DomainOrderRepository
import com.wooauto.domain.usecases.orders.GetOrdersUseCase
import com.wooauto.domain.usecases.orders.ManageOrderUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    
    @Provides
    @Singleton
    fun provideManageOrderUseCase(
        orderRepository: DomainOrderRepository
    ): ManageOrderUseCase {
        return ManageOrderUseCase(orderRepository)
    }

    @Provides
    @Singleton
    fun provideGetOrdersUseCase(
        orderRepository: DomainOrderRepository
    ): GetOrdersUseCase {
        return GetOrdersUseCase(orderRepository)
    }
} 