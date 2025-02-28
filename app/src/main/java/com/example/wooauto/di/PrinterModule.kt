package com.example.wooauto.di

import com.example.wooauto.data.printer.BluetoothPrinterManager
import com.example.wooauto.data.templates.DefaultOrderPrintTemplate
import com.example.wooauto.domain.printer.PrinterManager
import com.example.wooauto.domain.templates.OrderPrintTemplate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 打印机模块
 * 提供打印相关的依赖
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PrinterModule {
    
    /**
     * 提供打印机管理器实现
     */
    @Binds
    @Singleton
    abstract fun providePrinterManager(
        bluetoothPrinterManager: BluetoothPrinterManager
    ): PrinterManager
    
    /**
     * 提供订单打印模板实现
     */
    @Binds
    @Singleton
    abstract fun provideOrderPrintTemplate(
        defaultOrderPrintTemplate: DefaultOrderPrintTemplate
    ): OrderPrintTemplate
} 