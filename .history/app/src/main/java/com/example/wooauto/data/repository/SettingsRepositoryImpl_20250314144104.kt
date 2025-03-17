package com.example.wooauto.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.wooauto.data.local.dao.SettingDao
import com.example.wooauto.data.mappers.SettingMapper
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.data.local.WooCommerceConfig as LocalWooCommerceConfig
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.domain.templates.TemplateType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingDao: SettingDao,
    private val wooCommerceConfig: LocalWooCommerceConfig,
    private val gson: Gson
) : DomainSettingRepository {

    private object PreferencesKeys {
        val SITE_URL = stringPreferencesKey("site_url")
        val CONSUMER_KEY = stringPreferencesKey("consumer_key")
        val CONSUMER_SECRET = stringPreferencesKey("consumer_secret")
        val POLLING_INTERVAL = intPreferencesKey("polling_interval")
        val USE_WOOCOMMERCE_FOOD = booleanPreferencesKey("use_woocommerce_food")
    }

    // 设置键名常量
    companion object {
        const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        const val KEY_API_URL = "api_url"
        const val KEY_CONSUMER_KEY = "consumer_key"
        const val KEY_CONSUMER_SECRET = "consumer_secret"
        const val KEY_PRINTER_TYPE = "printer_type"
        const val KEY_PRINTER_CONNECTION = "printer_connection"
        const val KEY_LANGUAGE = "language"
        const val KEY_CURRENCY = "currency"
        
        // 打印机配置键前缀
        const val KEY_PREFIX_PRINTER_CONFIGS = "printer_configs_"
        const val KEY_DEFAULT_PRINTER_ID = "default_printer_id"
        
        // 商店信息相关键
        const val KEY_STORE_NAME = "store_name"
        const val KEY_STORE_ADDRESS = "store_address"
        const val KEY_STORE_PHONE = "store_phone"
        const val KEY_CURRENCY_SYMBOL = "currency_symbol"
        
        // 自动化任务相关键
        const val KEY_AUTO_PRINT_ENABLED = "auto_print_enabled"
        const val KEY_AUTO_ORDER_PROCESSING_ENABLED = "auto_order_processing_enabled"
        const val KEY_DEFAULT_TEMPLATE_TYPE = "default_template_type"
        
        // 默认值
        const val DEFAULT_API_URL = "https://example.com/wp-json/wc/v3/"
        const val DEFAULT_LANGUAGE = "en"
        const val DEFAULT_CURRENCY = "USD"
        const val DEFAULT_STORE_NAME = "我的商店"
        const val DEFAULT_CURRENCY_SYMBOL = "C$"
        
        // 声音设置相关键名
        private const val KEY_NOTIFICATION_VOLUME = "notification_volume"
        private const val KEY_SOUND_TYPE = "sound_type"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
    }

    init {
        // 在初始化时检查配置并更新状态
        GlobalScope.launch {
            try {
                // 使用本地配置类更新状态
                wooCommerceConfig.isConfiguredFlow.first()
                Log.d("SettingsRepositoryImpl", "初始化完成，配置状态已更新")
            } catch (e: Exception) {
                Log.e("SettingsRepositoryImpl", "初始化检查配置失败", e)
            }
        }
    }

    // WooCommerce 配置相关方法 - 使用统一的配置类
    override suspend fun getWooCommerceConfig(): WooCommerceConfig {
        Log.d("SettingsRepositoryImpl", "获取WooCommerce配置")
        // 从本地配置转换为远程配置
        return wooCommerceConfig.toRemoteConfig()
    }

    override suspend fun saveWooCommerceConfig(config: WooCommerceConfig) {
        Log.d("SettingsRepositoryImpl", "保存WooCommerce配置: $config")
        // 保存远程配置到本地配置
        wooCommerceConfig.saveRemoteConfig(config)
    }

    override suspend fun clearWooCommerceConfig() {
        Log.d("SettingsRepositoryImpl", "清除WooCommerce配置")
        // 使用空配置覆盖现有配置
        val emptyConfig = WooCommerceConfig("", "", "", 30, false)
        wooCommerceConfig.saveRemoteConfig(emptyConfig)
    }

    // 其他设置相关方法 - 使用 Room 数据库
    override fun getNotificationEnabledFlow(): Flow<Boolean> {
        return getBooleanSettingFlow(KEY_NOTIFICATION_ENABLED, false)
    }

    override suspend fun setNotificationEnabled(enabled: Boolean) {
        val entity = SettingMapper.createBooleanSettingEntity(
            KEY_NOTIFICATION_ENABLED,
            enabled,
            "启用订单通知提醒"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    override fun getApiUrlFlow(): Flow<String> {
        return getStringSettingFlow(KEY_API_URL, DEFAULT_API_URL)
    }

    override suspend fun setApiUrl(url: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_API_URL,
            url,
            "WooCommerce API URL"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    override fun getConsumerKeyFlow(): Flow<String> {
        return getStringSettingFlow(KEY_CONSUMER_KEY, "")
    }

    override suspend fun setConsumerKey(consumerKey: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_CONSUMER_KEY,
            consumerKey,
            "WooCommerce Consumer Key"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    override fun getConsumerSecretFlow(): Flow<String> {
        return getStringSettingFlow(KEY_CONSUMER_SECRET, "")
    }

    override suspend fun setConsumerSecret(consumerSecret: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_CONSUMER_SECRET,
            consumerSecret,
            "WooCommerce Consumer Secret"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    override fun getPrinterTypeFlow(): Flow<String> {
        return getStringSettingFlow(KEY_PRINTER_TYPE, "")
    }

    override suspend fun setPrinterType(type: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_PRINTER_TYPE,
            type,
            "打印机类型"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    override fun getPrinterConnectionFlow(): Flow<Boolean> {
        return getBooleanSettingFlow(KEY_PRINTER_CONNECTION, false)
    }

    override suspend fun setPrinterConnection(isConnected: Boolean) {
        val entity = SettingMapper.createBooleanSettingEntity(
            KEY_PRINTER_CONNECTION,
            isConnected,
            "打印机连接状态"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    override fun getLanguageFlow(): Flow<String> {
        return getStringSettingFlow(KEY_LANGUAGE, DEFAULT_LANGUAGE)
    }

    override suspend fun setLanguage(language: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_LANGUAGE,
            language,
            "应用语言"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    override fun getCurrencyFlow(): Flow<String> {
        return getStringSettingFlow(KEY_CURRENCY, DEFAULT_CURRENCY)
    }

    override suspend fun setCurrency(currency: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_CURRENCY,
            currency,
            "显示货币"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    // 打印机配置相关方法实现
    override suspend fun getAllPrinterConfigs(): List<PrinterConfig> {
        val allSettings = settingDao.getAllSettings().first()
        val printerConfigs = mutableListOf<PrinterConfig>()
        
        for (setting in allSettings) {
            if (setting.key.startsWith(KEY_PREFIX_PRINTER_CONFIGS)) {
                try {
                    val json = setting.value
                    val config = SettingMapper.jsonToPrinterConfig(json)
                    printerConfigs.add(config)
                } catch (e: Exception) {
                    Log.e("SettingsRepository", "解析打印机配置失败: ${e.message}")
                }
            }
        }
        
        return printerConfigs
    }
    
    override suspend fun getPrinterConfig(printerId: String): PrinterConfig? {
        val key = KEY_PREFIX_PRINTER_CONFIGS + printerId
        val setting = settingDao.getSettingByKey(key) ?: return null
        return try {
            SettingMapper.jsonToPrinterConfig(setting.value)
        } catch (e: Exception) {
            Log.e("SettingsRepository", "解析打印机配置失败: ${e.message}")
            null
        }
    }
    
    override suspend fun getDefaultPrinterConfig(): PrinterConfig? {
        val defaultPrinterId = settingDao.getSettingByKey(KEY_DEFAULT_PRINTER_ID)?.value
        if (defaultPrinterId.isNullOrBlank()) return null
        return getPrinterConfig(defaultPrinterId)
    }
    
    override suspend fun savePrinterConfig(config: PrinterConfig) {
        try {
            val key = KEY_PREFIX_PRINTER_CONFIGS + config.id
            val json = SettingMapper.printerConfigToJson(config)
            val entity = SettingMapper.createStringSettingEntity(
                key,
                json,
                "打印机配置: ${config.getDisplayName()}"
            )
            settingDao.insertOrUpdateSetting(entity)
            
            // 如果是默认打印机，更新默认打印机ID
            if (config.isDefault) {
                val defaultEntity = SettingMapper.createStringSettingEntity(
                    KEY_DEFAULT_PRINTER_ID,
                    config.id,
                    "默认打印机ID"
                )
                settingDao.insertOrUpdateSetting(defaultEntity)
            }
        } catch (e: Exception) {
            Log.e("SettingsRepository", "保存打印机配置失败: ${e.message}")
        }
    }
    
    override suspend fun deletePrinterConfig(printerId: String) {
        val key = KEY_PREFIX_PRINTER_CONFIGS + printerId
        val setting = settingDao.getSettingByKey(key)
        if (setting != null) {
            settingDao.deleteSetting(setting)
        }
        
        // 如果删除的是默认打印机，清除默认打印机ID
        val defaultPrinterId = settingDao.getSettingByKey(KEY_DEFAULT_PRINTER_ID)?.value
        if (defaultPrinterId == printerId) {
            val defaultSetting = settingDao.getSettingByKey(KEY_DEFAULT_PRINTER_ID)
            if (defaultSetting != null) {
                settingDao.deleteSetting(defaultSetting)
            }
        }
    }
    
    override fun getPrinterConfigFlow(printerId: String): Flow<PrinterConfig?> {
        val key = KEY_PREFIX_PRINTER_CONFIGS + printerId
        return flow {
            val setting = settingDao.getSettingByKey(key)
            if (setting == null) {
                emit(null)
            } else {
                try {
                    emit(SettingMapper.jsonToPrinterConfig(setting.value))
                } catch (e: Exception) {
                    Log.e("SettingsRepository", "解析打印机配置失败: ${e.message}")
                    emit(null)
                }
            }
        }
    }
    
    override fun getAllPrinterConfigsFlow(): Flow<List<PrinterConfig>> {
        return flow {
            val allSettings = settingDao.getAllSettings().first()
            val printerConfigs = mutableListOf<PrinterConfig>()
            
            for (setting in allSettings) {
                if (setting.key.startsWith(KEY_PREFIX_PRINTER_CONFIGS)) {
                    try {
                        val config = SettingMapper.jsonToPrinterConfig(setting.value)
                        printerConfigs.add(config)
                    } catch (e: Exception) {
                        Log.e("SettingsRepository", "解析打印机配置失败: ${e.message}")
                    }
                }
            }
            
            emit(printerConfigs)
        }
    }

    // 商店信息相关方法实现
    override suspend fun getStoreName(): String? {
        return settingDao.getSettingByKey(KEY_STORE_NAME)?.value ?: DEFAULT_STORE_NAME
    }
    
    override suspend fun setStoreName(name: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_STORE_NAME,
            name,
            "商店名称"
        )
        settingDao.insertOrUpdateSetting(entity)
    }
    
    override suspend fun getStoreAddress(): String? {
        return settingDao.getSettingByKey(KEY_STORE_ADDRESS)?.value
    }
    
    override suspend fun setStoreAddress(address: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_STORE_ADDRESS,
            address,
            "商店地址"
        )
        settingDao.insertOrUpdateSetting(entity)
    }
    
    override suspend fun getStorePhone(): String? {
        return settingDao.getSettingByKey(KEY_STORE_PHONE)?.value
    }
    
    override suspend fun setStorePhone(phone: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_STORE_PHONE,
            phone,
            "商店电话"
        )
        settingDao.insertOrUpdateSetting(entity)
    }
    
    override suspend fun getCurrencySymbol(): String? {
        return settingDao.getSettingByKey(KEY_CURRENCY_SYMBOL)?.value ?: DEFAULT_CURRENCY_SYMBOL
    }
    
    override suspend fun setCurrencySymbol(symbol: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_CURRENCY_SYMBOL,
            symbol,
            "货币符号"
        )
        settingDao.insertOrUpdateSetting(entity)
    }
    
    override fun getStoreNameFlow(): Flow<String> {
        return getStringSettingFlow(KEY_STORE_NAME, DEFAULT_STORE_NAME)
    }
    
    override fun getStoreAddressFlow(): Flow<String> {
        return getStringSettingFlow(KEY_STORE_ADDRESS, "")
    }
    
    override fun getStorePhoneFlow(): Flow<String> {
        return getStringSettingFlow(KEY_STORE_PHONE, "")
    }
    
    override fun getCurrencySymbolFlow(): Flow<String> {
        return getStringSettingFlow(KEY_CURRENCY_SYMBOL, DEFAULT_CURRENCY_SYMBOL)
    }

    // 辅助方法
    private fun getStringSettingFlow(key: String, defaultValue: String): Flow<String> {
        return flow {
            // 在flow构建器内部可以调用挂起函数
            val entity = settingDao.getSettingByKey(key)
            emit(entity?.value ?: defaultValue)
        }
    }

    private fun getBooleanSettingFlow(key: String, defaultValue: Boolean): Flow<Boolean> {
        return flow {
            val entity = settingDao.getSettingByKey(key)
            emit(entity?.value?.toBoolean() ?: defaultValue)
        }
    }

    /**
     * 获取默认模板类型
     * @return 默认模板类型，如果未设置返回null
     */
    override suspend fun getDefaultTemplateType(): TemplateType? {
        val templateTypeName = settingDao.getSettingByKey(KEY_DEFAULT_TEMPLATE_TYPE)?.value
            ?: return TemplateType.FULL_DETAILS
        
        return try {
            TemplateType.valueOf(templateTypeName)
        } catch (e: Exception) {
            Log.e("SettingsRepository", "解析模板类型失败: ${e.message}")
            TemplateType.FULL_DETAILS
        }
    }
    
    /**
     * 保存默认模板类型
     * @param templateType 模板类型
     */
    override suspend fun saveDefaultTemplateType(templateType: TemplateType) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_DEFAULT_TEMPLATE_TYPE,
            templateType.name,
            "默认打印模板类型"
        )
        settingDao.insertOrUpdateSetting(entity)
    }
    
    /**
     * 获取自动打印开关状态
     */
    override suspend fun getAutomaticPrintingEnabled(): Boolean? {
        Log.d("SettingsRepository", "获取自动打印设置")
        val setting = settingDao.getSettingByKey(KEY_AUTO_PRINT_ENABLED)
        return setting?.value?.toBoolean()
    }
    
    /**
     * 设置自动打印开关状态
     */
    override suspend fun setAutomaticPrintingEnabled(enabled: Boolean) {
        Log.d("SettingsRepository", "设置自动打印: $enabled")
        val entity = SettingMapper.createBooleanSettingEntity(
            KEY_AUTO_PRINT_ENABLED,
            enabled,
            "自动打印开关"
        )
        settingDao.insertOrUpdateSetting(entity)
    }
    
    /**
     * 获取自动接单开关状态
     */
    override suspend fun getAutomaticOrderProcessingEnabled(): Boolean? {
        Log.d("SettingsRepository", "获取自动接单设置")
        val setting = settingDao.getSettingByKey(KEY_AUTO_ORDER_PROCESSING_ENABLED)
        return setting?.value?.toBoolean()
    }
    
    /**
     * 设置自动接单开关状态
     */
    override suspend fun setAutomaticOrderProcessingEnabled(enabled: Boolean) {
        Log.d("SettingsRepository", "设置自动接单: $enabled")
        val entity = SettingMapper.createBooleanSettingEntity(
            KEY_AUTO_ORDER_PROCESSING_ENABLED,
            enabled,
            "自动接单开关"
        )
        settingDao.insertOrUpdateSetting(entity)
    }
    
    override suspend fun getSoundSettings(): SoundSettings {
        val volume = getNotificationVolume()
        val type = getSoundType()
        val enabled = getSoundEnabled()
        
        return SoundSettings(
            notificationVolume = volume,
            soundType = type,
            soundEnabled = enabled
        )
    }
    
    override suspend fun saveSoundSettings(settings: SoundSettings) {
        setNotificationVolume(settings.notificationVolume)
        setSoundType(settings.soundType)
        setSoundEnabled(settings.soundEnabled)
        
        Log.d("SettingsRepository", "保存声音设置: 音量=${settings.notificationVolume}, 音效=${settings.soundType}, 启用=${settings.soundEnabled}")
    }
    
    override suspend fun getNotificationVolume(): Int {
        val setting = settingDao.getSettingByKey(KEY_NOTIFICATION_VOLUME)
        return setting?.value?.toIntOrNull() ?: 70 // 默认音量70%
    }
    
    override suspend fun setNotificationVolume(volume: Int) {
        val safeVolume = when {
            volume < 0 -> 0
            volume > 100 -> 100
            else -> volume
        }
        
        val entity = SettingMapper.createIntSettingEntity(
            KEY_NOTIFICATION_VOLUME,
            safeVolume,
            "通知音量"
        )
        settingDao.insertOrUpdateSetting(entity)
    }
    
    override suspend fun getSoundType(): String {
        val setting = settingDao.getSettingByKey(KEY_SOUND_TYPE)
        return setting?.value ?: SoundSettings.SOUND_TYPE_DEFAULT
    }
    
    override suspend fun setSoundType(type: String) {
        val validType = if (SoundSettings.getAllSoundTypes().contains(type)) {
            type
        } else {
            SoundSettings.SOUND_TYPE_DEFAULT
        }
        
        val entity = SettingMapper.createStringSettingEntity(
            KEY_SOUND_TYPE,
            validType,
            "提示音类型"
        )
        settingDao.insertOrUpdateSetting(entity)
    }
    
    override suspend fun getSoundEnabled(): Boolean {
        val setting = settingDao.getSettingByKey(KEY_SOUND_ENABLED)
        return setting?.value?.toBoolean() ?: true // 默认启用声音
    }
    
    override suspend fun setSoundEnabled(enabled: Boolean) {
        val entity = SettingMapper.createBooleanSettingEntity(
            KEY_SOUND_ENABLED,
            enabled,
            "声音启用状态"
        )
        settingDao.insertOrUpdateSetting(entity)
    }
} 