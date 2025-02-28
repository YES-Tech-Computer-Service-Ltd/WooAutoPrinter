package com.wooauto.domain.usecases.settings

import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.repositories.DomainSettingRepository
import kotlinx.coroutines.flow.Flow

/**
 * 打印机设置相关用例
 */
class PrinterSettingsUseCase(private val repository: DomainSettingRepository) {
    
    /**
     * 获取打印机类型
     */
    fun getPrinterType(): Flow<String> {
        return repository.getPrinterTypeFlow()
    }
    
    /**
     * 设置打印机类型
     */
    suspend fun setPrinterType(type: String) {
        repository.setPrinterType(type)
    }
    
    /**
     * 获取打印机连接状态
     */
    fun getPrinterConnection(): Flow<Boolean> {
        return repository.getPrinterConnectionFlow()
    }
    
    /**
     * 设置打印机连接状态
     */
    suspend fun setPrinterConnection(isConnected: Boolean) {
        repository.setPrinterConnection(isConnected)
    }
    
    /**
     * 获取所有打印机配置
     */
    suspend fun getAllPrinterConfigs(): List<PrinterConfig> {
        return repository.getAllPrinterConfigs()
    }
    
    /**
     * 获取所有打印机配置流
     */
    fun getAllPrinterConfigsFlow(): Flow<List<PrinterConfig>> {
        return repository.getAllPrinterConfigsFlow()
    }
    
    /**
     * 获取特定打印机配置
     */
    suspend fun getPrinterConfig(printerId: String): PrinterConfig? {
        return repository.getPrinterConfig(printerId)
    }
    
    /**
     * 获取特定打印机配置流
     */
    fun getPrinterConfigFlow(printerId: String): Flow<PrinterConfig?> {
        return repository.getPrinterConfigFlow(printerId)
    }
    
    /**
     * 获取默认打印机配置
     */
    suspend fun getDefaultPrinterConfig(): PrinterConfig? {
        return repository.getDefaultPrinterConfig()
    }
    
    /**
     * 保存打印机配置
     */
    suspend fun savePrinterConfig(config: PrinterConfig) {
        repository.savePrinterConfig(config)
    }
    
    /**
     * 删除打印机配置
     */
    suspend fun deletePrinterConfig(printerId: String) {
        repository.deletePrinterConfig(printerId)
    }
    
    /**
     * 设置默认打印机
     */
    suspend fun setDefaultPrinter(printerId: String) {
        // 获取所有打印机配置
        val allPrinters = repository.getAllPrinterConfigs()
        
        // 更新每个打印机的默认状态
        allPrinters.forEach { printer ->
            val isDefault = printer.id == printerId
            if (printer.isDefault != isDefault) {
                repository.savePrinterConfig(printer.copy(isDefault = isDefault))
            }
        }
    }
} 