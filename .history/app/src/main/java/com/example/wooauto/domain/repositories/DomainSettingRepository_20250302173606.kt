package com.example.wooauto.domain.repositories

import com.example.wooauto.data.remote.WooCommerceConfig
import kotlinx.coroutines.flow.Flow

/**
 * 设置仓库接口
 * 定义了与系统设置相关的数据操作
 */
interface DomainSettingRepository {
    /**
     * 获取WooCommerce配置信息
     * @return 配置对象
     */
    suspend fun getWooCommerceConfig(): WooCommerceConfig
    
    /**
     * 保存WooCommerce配置信息
     * @param config 要保存的配置对象
     */
    suspend fun saveWooCommerceConfig(config: WooCommerceConfig)
    
    /**
     * 清除WooCommerce配置信息
     */
    suspend fun clearWooCommerceConfig()

    /**
     * 获取通知开启状态
     * @return 通知开启状态的数据流
     */
    fun getNotificationEnabledFlow(): Flow<Boolean>

    /**
     * 设置通知开启状态
     * @param enabled 是否开启通知
     */
    suspend fun setNotificationEnabled(enabled: Boolean)

    /**
     * 获取API URL
     * @return API URL的数据流
     */
    fun getApiUrlFlow(): Flow<String>

    /**
     * 设置API URL
     * @param url API URL
     */
    suspend fun setApiUrl(url: String)

    /**
     * 获取Consumer Key
     * @return Consumer Key的数据流
     */
    fun getConsumerKeyFlow(): Flow<String>

    /**
     * 设置Consumer Key
     * @param consumerKey Consumer Key
     */
    suspend fun setConsumerKey(consumerKey: String)

    /**
     * 获取Consumer Secret
     * @return Consumer Secret的数据流
     */
    fun getConsumerSecretFlow(): Flow<String>

    /**
     * 设置Consumer Secret
     * @param consumerSecret Consumer Secret
     */
    suspend fun setConsumerSecret(consumerSecret: String)

    /**
     * 获取打印机类型
     * @return 打印机类型的数据流
     */
    fun getPrinterTypeFlow(): Flow<String>

    /**
     * 设置打印机类型
     * @param type 打印机类型
     */
    suspend fun setPrinterType(type: String)

    /**
     * 获取打印机连接状态
     * @return 打印机连接状态的数据流
     */
    fun getPrinterConnectionFlow(): Flow<Boolean>

    /**
     * 设置打印机连接状态
     * @param isConnected 是否已连接
     */
    suspend fun setPrinterConnection(isConnected: Boolean)

    /**
     * 获取应用语言
     * @return 应用语言的数据流
     */
    fun getLanguageFlow(): Flow<String>

    /**
     * 设置应用语言
     * @param language 语言代码
     */
    suspend fun setLanguage(language: String)

    /**
     * 获取货币设置
     * @return 货币代码的数据流
     */
    fun getCurrencyFlow(): Flow<String>

    /**
     * 设置货币
     * @param currency 货币代码
     */
    suspend fun setCurrency(currency: String)

    /**
     * 获取所有打印机配置
     * @return 打印机配置列表
     */
    suspend fun getAllPrinterConfigs(): List<PrinterConfig>
    
    /**
     * 获取打印机配置
     * @param printerId 打印机ID
     * @return 打印机配置，如果不存在返回null
     */
    suspend fun getPrinterConfig(printerId: String): PrinterConfig?
    
    /**
     * 获取默认打印机配置
     * @return 默认打印机配置，如果未设置返回null
     */
    suspend fun getDefaultPrinterConfig(): PrinterConfig?
    
    /**
     * 保存打印机配置
     * @param config 要保存的打印机配置
     */
    suspend fun savePrinterConfig(config: PrinterConfig)
    
    /**
     * 删除打印机配置
     * @param printerId 要删除的打印机ID
     */
    suspend fun deletePrinterConfig(printerId: String)
    
    /**
     * 获取打印机配置Flow
     * @param printerId 打印机ID
     * @return 打印机配置的数据流
     */
    fun getPrinterConfigFlow(printerId: String): Flow<PrinterConfig?>
    
    /**
     * 获取所有打印机配置Flow
     * @return 打印机配置列表的数据流
     */
    fun getAllPrinterConfigsFlow(): Flow<List<PrinterConfig>>
} 