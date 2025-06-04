package com.example.wooauto.domain.repositories

import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.templates.TemplateType
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

    /**
     * 获取商店名称
     * @return 商店名称，如果未设置返回null
     */
    suspend fun getStoreName(): String?
    
    /**
     * 设置商店名称
     * @param name 商店名称
     */
    suspend fun setStoreName(name: String)
    
    /**
     * 获取商店地址
     * @return 商店地址，如果未设置返回null
     */
    suspend fun getStoreAddress(): String?
    
    /**
     * 设置商店地址
     * @param address 商店地址
     */
    suspend fun setStoreAddress(address: String)
    
    /**
     * 获取商店电话
     * @return 商店电话，如果未设置返回null
     */
    suspend fun getStorePhone(): String?
    
    /**
     * 设置商店电话
     * @param phone 商店电话
     */
    suspend fun setStorePhone(phone: String)
    
    /**
     * 获取货币符号
     * @return 货币符号，如果未设置返回null
     */
    suspend fun getCurrencySymbol(): String?
    
    /**
     * 设置货币符号
     * @param symbol 货币符号
     */
    suspend fun setCurrencySymbol(symbol: String)
    
    /**
     * 获取商店名称Flow
     * @return 商店名称的数据流
     */
    fun getStoreNameFlow(): Flow<String>
    
    /**
     * 获取商店地址Flow
     * @return 商店地址的数据流
     */
    fun getStoreAddressFlow(): Flow<String>
    
    /**
     * 获取商店电话Flow
     * @return 商店电话的数据流
     */
    fun getStorePhoneFlow(): Flow<String>
    
    /**
     * 获取货币符号Flow
     * @return 货币符号的数据流
     */
    fun getCurrencySymbolFlow(): Flow<String>

    /**
     * 获取默认模板类型
     * @return 默认模板类型
     */
    suspend fun getDefaultPrintTemplate(): TemplateType
    
    /**
     * 保存默认模板类型
     * @param templateType 模板类型
     */
    suspend fun setDefaultPrintTemplate(templateType: TemplateType)
    
    /**
     * 获取默认模板类型 (用于兼容旧代码)
     * @return 默认模板类型
     */
    suspend fun getDefaultTemplateType(): TemplateType
    
    /**
     * 保存默认模板类型 (用于兼容旧代码)
     * @param templateType 模板类型
     */
    suspend fun saveDefaultTemplateType(templateType: TemplateType)
    
    /**
     * 获取当前使用的自定义模板ID
     * @return 自定义模板ID，如果没有则返回null
     */
    suspend fun getCurrentCustomTemplateId(): String?
    
    /**
     * 保存当前使用的自定义模板ID
     * @param templateId 自定义模板ID
     */
    suspend fun saveCustomTemplateId(templateId: String)
    
    /**
     * 获取默认自动打印模板ID
     * @return 默认模板ID，如果没有则返回null
     */
    suspend fun getDefaultAutoPrintTemplateId(): String?
    
    /**
     * 保存默认自动打印模板ID
     * @param templateId 模板ID
     */
    suspend fun saveDefaultAutoPrintTemplateId(templateId: String)
    
    /**
     * 设置临时手动打印标志（用于区分手动打印和自动打印）
     * @param isManualPrint 是否为手动打印
     */
    suspend fun setTemporaryManualPrintFlag(isManualPrint: Boolean)
    
    /**
     * 获取并清除临时手动打印标志
     * @return 是否为手动打印
     */
    suspend fun getAndClearTemporaryManualPrintFlag(): Boolean

    /**
     * 获取自动打印启用状态
     * @return 是否启用自动打印
     */
    suspend fun getAutoPrintEnabled(): Boolean

    /**
     * 设置自动打印启用状态
     * @param enabled 是否启用
     */
    suspend fun setAutoPrintEnabled(enabled: Boolean)

    /**
     * 获取自动打印开关状态
     * @return 自动打印是否开启，如果未设置默认返回false
     */
    suspend fun getAutomaticPrintingEnabled(): Boolean?
    
    /**
     * 设置自动打印开关状态
     * @param enabled 是否开启自动打印
     */
    suspend fun setAutomaticPrintingEnabled(enabled: Boolean)
    
    /**
     * 获取自动接单开关状态
     * @return 自动接单是否开启，如果未设置默认返回false
     */
    suspend fun getAutomaticOrderProcessingEnabled(): Boolean?
    
    /**
     * 设置自动接单开关状态
     * @param enabled 是否开启自动接单
     */
    suspend fun setAutomaticOrderProcessingEnabled(enabled: Boolean)

    /**
     * 获取声音设置
     * @return 声音设置对象
     */
    suspend fun getSoundSettings(): com.example.wooauto.domain.models.SoundSettings
    
    /**
     * 保存声音设置
     * @param settings 声音设置对象
     */
    suspend fun saveSoundSettings(settings: com.example.wooauto.domain.models.SoundSettings)
    
    /**
     * 获取通知音量
     * @return 通知音量 (0-100)
     */
    suspend fun getNotificationVolume(): Int
    
    /**
     * 设置通知音量
     * @param volume 通知音量 (0-100)
     */
    suspend fun setNotificationVolume(volume: Int)
    
    /**
     * 获取提示音类型
     * @return 提示音类型
     */
    suspend fun getSoundType(): String
    
    /**
     * 设置提示音类型
     * @param type 提示音类型
     */
    suspend fun setSoundType(type: String)
    
    /**
     * 获取声音启用状态
     * @return 声音是否启用
     */
    suspend fun getSoundEnabled(): Boolean
    
    /**
     * 设置声音启用状态
     * @param enabled 声音是否启用
     */
    suspend fun setSoundEnabled(enabled: Boolean)

    /**
     * 获取自定义声音URI
     * @return 自定义声音URI
     */
    suspend fun getCustomSoundUri(): String

    /**
     * 设置自定义声音URI
     * @param uri 自定义声音URI
     */
    suspend fun setCustomSoundUri(uri: String)
    
    /**
     * 获取自动更新状态
     * @return 是否开启自动更新
     */
    suspend fun getAutoUpdate(): Boolean
    
    /**
     * 设置自动更新状态
     * @param enabled 是否开启自动更新
     */
    suspend fun setAutoUpdate(enabled: Boolean)
    
    /**
     * 获取自动更新状态Flow
     * @return 自动更新状态的数据流
     */
    fun getAutoUpdateFlow(): Flow<Boolean>
    
    /**
     * 获取上次检查更新时间
     * @return 上次检查更新的时间戳
     */
    suspend fun getLastUpdateCheckTime(): Long
    
    /**
     * 设置上次检查更新时间
     * @param timestamp 检查更新的时间戳
     */
    suspend fun setLastUpdateCheckTime(timestamp: Long)
    
    /**
     * 获取屏幕常亮设置
     * @return 屏幕常亮设置的数据流
     */
    fun getKeepScreenOn(): Flow<Boolean>
    
    /**
     * 设置屏幕常亮
     * @param keepOn 是否保持屏幕常亮
     */
    suspend fun setKeepScreenOn(keepOn: Boolean)
} 