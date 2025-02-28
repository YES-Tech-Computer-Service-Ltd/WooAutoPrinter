package com.example.wooauto.domain.usecases.settings

import com.example.wooauto.domain.repositories.DomainSettingRepository
import kotlinx.coroutines.flow.Flow

/**
 * 打印机设置用例
 *
 * 该用例负责处理与打印机相关的设置，包括：
 * - 打印机类型设置
 * - 打印机连接状态管理
 */
class PrinterSettingsUseCase(
    private val settingRepository: DomainSettingRepository
) {
    /**
     * 获取打印机类型的Flow
     * @return 打印机类型的Flow
     */
    fun getPrinterType(): Flow<String> {
        return settingRepository.getPrinterTypeFlow()
    }

    /**
     * 设置打印机类型
     * @param type 打印机类型
     */
    suspend fun setPrinterType(type: String) {
        settingRepository.setPrinterType(type)
    }

    /**
     * 获取打印机连接状态的Flow
     * @return 打印机连接状态的Flow
     */
    fun getPrinterConnection(): Flow<Boolean> {
        return settingRepository.getPrinterConnectionFlow()
    }

    /**
     * 设置打印机连接状态
     * @param isConnected 是否已连接
     */
    suspend fun setPrinterConnection(isConnected: Boolean) {
        settingRepository.setPrinterConnection(isConnected)
    }
} 