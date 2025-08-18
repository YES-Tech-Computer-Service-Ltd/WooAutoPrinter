package com.example.wooauto.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.wooauto.data.local.dao.SettingDao
import com.example.wooauto.data.mappers.SettingMapper
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.data.local.WooCommerceConfig as LocalWooCommerceConfig
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.models.SoundSettings
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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.catch
import java.io.IOException

// 将扩展属性改为伴生对象中的工厂方法
private val Context.dataStore by preferencesDataStore(name = "settings")

@OptIn(DelicateCoroutinesApi::class)
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingDao: SettingDao,
    private val wooCommerceConfig: LocalWooCommerceConfig,
    private val gson: Gson
) : DomainSettingRepository {

    // 获取DataStore实例
    private val dataStore: DataStore<Preferences> = context.dataStore

    private object PreferencesKeys {
        val SITE_URL = stringPreferencesKey("site_url")
        val CONSUMER_KEY = stringPreferencesKey("consumer_key")
        val CONSUMER_SECRET = stringPreferencesKey("consumer_secret")
        val POLLING_INTERVAL = intPreferencesKey("polling_interval")
        val USE_WOOCOMMERCE_FOOD = booleanPreferencesKey("use_woocommerce_food")
        val AUTO_PRINT_ENABLED = booleanPreferencesKey(KEY_AUTO_PRINT_ENABLED)
        val DEFAULT_TEMPLATE_TYPE = stringPreferencesKey(KEY_DEFAULT_TEMPLATE_TYPE)
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        val LAST_UPDATE_CHECK_TIME = longPreferencesKey("last_update_check_time")
        
        // 声音设置相关键
        val NOTIFICATION_VOLUME = intPreferencesKey(KEY_NOTIFICATION_VOLUME)
        val SOUND_TYPE = stringPreferencesKey(KEY_SOUND_TYPE)
        val SOUND_ENABLED = booleanPreferencesKey(KEY_SOUND_ENABLED)
        val CUSTOM_SOUND_URI = stringPreferencesKey(KEY_CUSTOM_SOUND_URI)
        
        // 自定义模板相关键
        val CURRENT_CUSTOM_TEMPLATE_ID = stringPreferencesKey(KEY_CURRENT_CUSTOM_TEMPLATE_ID)
        val DEFAULT_AUTO_PRINT_TEMPLATE_ID = stringPreferencesKey(KEY_DEFAULT_AUTO_PRINT_TEMPLATE_ID)
        val TEMPORARY_MANUAL_PRINT_FLAG = booleanPreferencesKey(KEY_TEMPORARY_MANUAL_PRINT_FLAG)
        
        // 屏幕常亮设置
        val KEEP_SCREEN_ON = booleanPreferencesKey(KEY_KEEP_SCREEN_ON)
        
        // 模板打印份数设置
        val TEMPLATE_PRINT_COPIES = stringPreferencesKey(KEY_TEMPLATE_PRINT_COPIES)
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
        const val DEFAULT_STORE_NAME = ""
        const val DEFAULT_CURRENCY_SYMBOL = "C$"
        
        // 声音设置相关键名
        const val KEY_NOTIFICATION_VOLUME = "notification_volume"
        const val KEY_SOUND_TYPE = "sound_type"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_CUSTOM_SOUND_URI = "custom_sound_uri"
        
        // 自定义模板相关键名
        const val KEY_CURRENT_CUSTOM_TEMPLATE_ID = "current_custom_template_id"
        const val KEY_DEFAULT_AUTO_PRINT_TEMPLATE_ID = "default_auto_print_template_id"
        const val KEY_TEMPORARY_MANUAL_PRINT_FLAG = "temporary_manual_print_flag"
        
        // 屏幕常亮设置
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        
        // 模板打印份数设置
        const val KEY_TEMPLATE_PRINT_COPIES = "template_print_copies"
    }

    private val autoUpdateKey = "auto_update"
    private val lastUpdateCheckTimeKey = "last_update_check_time"

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
        return settingDao.observeSettingByKey(key).map { entity ->
            entity?.value ?: defaultValue
        }
    }

    private fun getBooleanSettingFlow(key: String, defaultValue: Boolean): Flow<Boolean> {
        return settingDao.observeSettingByKey(key).map { entity ->
            entity?.value?.toBoolean() ?: defaultValue
        }
    }

    /**
     * 获取自动打印开关状态
     */
    override suspend fun getAutoPrintEnabled(): Boolean {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Log.e("SettingsRepositoryImpl", "Error reading auto_print_enabled.", exception)
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PreferencesKeys.AUTO_PRINT_ENABLED] ?: false
            }.first()
    }

    /**
     * 设置自动打印开关状态
     */
    override suspend fun setAutoPrintEnabled(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[PreferencesKeys.AUTO_PRINT_ENABLED] = enabled
        }
    }

    /**
     * 获取自动更新状态
     */
    override suspend fun getAutoUpdate(): Boolean {
        return dataStore.data.first()[booleanPreferencesKey(autoUpdateKey)] ?: false
    }

    /**
     * 设置自动更新状态
     */
    override suspend fun setAutoUpdate(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[booleanPreferencesKey(autoUpdateKey)] = enabled
        }
    }

    /**
     * 获取自动更新状态Flow
     */
    override fun getAutoUpdateFlow(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[booleanPreferencesKey(autoUpdateKey)] ?: false
        }
    }

    /**
     * 获取上次检查更新时间
     */
    override suspend fun getLastUpdateCheckTime(): Long {
        return dataStore.data.first()[longPreferencesKey(lastUpdateCheckTimeKey)] ?: 0L
    }

    /**
     * 设置上次检查更新时间
     */
    override suspend fun setLastUpdateCheckTime(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[longPreferencesKey(lastUpdateCheckTimeKey)] = timestamp
        }
    }

    // 实现 DomainSettingRepository 中关于自动打印和模板的方法
    override suspend fun getAutomaticPrintingEnabled(): Boolean? {
        return getAutoPrintEnabled()
    }

    override suspend fun setAutomaticPrintingEnabled(enabled: Boolean) {
        setAutoPrintEnabled(enabled)
    }

    override suspend fun getAutomaticOrderProcessingEnabled(): Boolean? {
        val entity = settingDao.getSettingByKey(KEY_AUTO_ORDER_PROCESSING_ENABLED)
        return entity?.value?.toBoolean()
    }

    override suspend fun setAutomaticOrderProcessingEnabled(enabled: Boolean) {
        val entity = SettingMapper.createBooleanSettingEntity(
            KEY_AUTO_ORDER_PROCESSING_ENABLED,
            enabled,
            "自动接单处理"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    override suspend fun getSoundSettings(): SoundSettings {
        val volume = getNotificationVolume()
        val type = getSoundType()
        val enabled = getSoundEnabled()
        val uri = getCustomSoundUri()
        
        return SoundSettings(
            notificationVolume = volume,
            soundType = type,
            soundEnabled = enabled,
            customSoundUri = uri
        )
    }

    override suspend fun saveSoundSettings(settings: SoundSettings) {
        setNotificationVolume(settings.notificationVolume)
        setSoundType(settings.soundType)
        setSoundEnabled(settings.soundEnabled)
        setCustomSoundUri(settings.customSoundUri)
    }

    override suspend fun getNotificationVolume(): Int {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Log.e("SettingsRepositoryImpl", "Error reading notification_volume.", exception)
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PreferencesKeys.NOTIFICATION_VOLUME] ?: 70
            }.first()
    }

    override suspend fun setNotificationVolume(volume: Int) {
        dataStore.edit { settings ->
            settings[PreferencesKeys.NOTIFICATION_VOLUME] = volume
        }
    }

    override suspend fun getSoundType(): String {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Log.e("SettingsRepositoryImpl", "Error reading sound_type.", exception)
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PreferencesKeys.SOUND_TYPE] ?: SoundSettings.SOUND_TYPE_DEFAULT
            }.first()
    }

    override suspend fun setSoundType(type: String) {
        dataStore.edit { settings ->
            settings[PreferencesKeys.SOUND_TYPE] = type
        }
    }

    override suspend fun getSoundEnabled(): Boolean {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Log.e("SettingsRepositoryImpl", "Error reading sound_enabled.", exception)
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PreferencesKeys.SOUND_ENABLED] ?: true
            }.first()
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[PreferencesKeys.SOUND_ENABLED] = enabled
        }
    }

    override suspend fun getCustomSoundUri(): String {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Log.e("SettingsRepositoryImpl", "Error reading custom_sound_uri.", exception)
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PreferencesKeys.CUSTOM_SOUND_URI] ?: ""
            }.first()
    }

    override suspend fun setCustomSoundUri(uri: String) {
        dataStore.edit { settings ->
            settings[PreferencesKeys.CUSTOM_SOUND_URI] = uri
        }
    }

    override suspend fun getDefaultPrintTemplate(): TemplateType {
        val templateName = dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Log.e("SettingsRepositoryImpl", "Error reading default_template_type.", exception)
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PreferencesKeys.DEFAULT_TEMPLATE_TYPE] ?: TemplateType.FULL_DETAILS.name
            }.first()
        return try {
            TemplateType.valueOf(templateName)
        } catch (e: IllegalArgumentException) {
            Log.w("SettingsRepositoryImpl", "Invalid template name found: $templateName, defaulting to FULL_DETAILS.")
            TemplateType.FULL_DETAILS
        }
    }

    override suspend fun setDefaultPrintTemplate(templateType: TemplateType) {
        dataStore.edit { settings ->
            settings[PreferencesKeys.DEFAULT_TEMPLATE_TYPE] = templateType.name
        }
    }
    
    /**
     * 获取默认模板类型 (兼容旧代码)
     */
    override suspend fun getDefaultTemplateType(): TemplateType {
        return getDefaultPrintTemplate()
    }
    
    /**
     * 保存默认模板类型 (兼容旧代码)
     */
    override suspend fun saveDefaultTemplateType(templateType: TemplateType) {
        setDefaultPrintTemplate(templateType)
    }
    
    /**
     * 获取当前使用的自定义模板ID
     */
    override suspend fun getCurrentCustomTemplateId(): String? {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Log.e("SettingsRepositoryImpl", "Error reading current_custom_template_id.", exception)
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PreferencesKeys.CURRENT_CUSTOM_TEMPLATE_ID]
            }.first()
    }
    
    /**
     * 保存当前使用的自定义模板ID
     */
    override suspend fun saveCustomTemplateId(templateId: String) {
        dataStore.edit { settings ->
            settings[PreferencesKeys.CURRENT_CUSTOM_TEMPLATE_ID] = templateId
        }
    }
    
    /**
     * 获取默认自动打印模板ID
     */
    override suspend fun getDefaultAutoPrintTemplateId(): String? {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Log.e("SettingsRepositoryImpl", "Error reading default_auto_print_template_id.", exception)
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PreferencesKeys.DEFAULT_AUTO_PRINT_TEMPLATE_ID]
            }.first()
    }
    
    /**
     * 保存默认自动打印模板ID
     */
    override suspend fun saveDefaultAutoPrintTemplateId(templateId: String) {
        dataStore.edit { settings ->
            settings[PreferencesKeys.DEFAULT_AUTO_PRINT_TEMPLATE_ID] = templateId
        }
    }
    
    /**
     * 设置临时手动打印标志
     */
    override suspend fun setTemporaryManualPrintFlag(isManualPrint: Boolean) {
        dataStore.edit { settings ->
            settings[PreferencesKeys.TEMPORARY_MANUAL_PRINT_FLAG] = isManualPrint
        }
    }
    
    /**
     * 获取并清除临时手动打印标志
     */
    override suspend fun getAndClearTemporaryManualPrintFlag(): Boolean {
        val isManual = dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Log.e("SettingsRepositoryImpl", "Error reading temporary_manual_print_flag.", exception)
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PreferencesKeys.TEMPORARY_MANUAL_PRINT_FLAG] ?: false
            }.first()
        
        // 读取后立即清除标志
        dataStore.edit { settings ->
            settings.remove(PreferencesKeys.TEMPORARY_MANUAL_PRINT_FLAG)
        }
        
        return isManual
    }
    
    // License Key
    private val LICENSE_KEY = stringPreferencesKey("license_key")

    /**
     * 获取屏幕常亮设置
     */
    override fun getKeepScreenOn(): Flow<Boolean> {
        return wooCommerceConfig.keepScreenOn
    }
    
    /**
     * 设置屏幕常亮
     */
    override suspend fun setKeepScreenOn(keepOn: Boolean) {
        wooCommerceConfig.updateKeepScreenOn(keepOn)
    }
    
    /**
     * 获取模板打印份数设置
     * @return Map<模板ID, 打印份数>
     */
    override suspend fun getTemplatePrintCopies(): Map<String, Int> {
        val jsonString = dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Log.e("SettingsRepositoryImpl", "Error reading template_print_copies.", exception)
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PreferencesKeys.TEMPLATE_PRINT_COPIES]
            }.first()
        
        return if (jsonString.isNullOrEmpty()) {
            emptyMap()
        } else {
            try {
                val type = object : TypeToken<Map<String, Int>>() {}.type
                gson.fromJson(jsonString, type)
            } catch (e: Exception) {
                Log.e("SettingsRepositoryImpl", "Error parsing template_print_copies JSON", e)
                emptyMap()
            }
        }
    }

    /**
     * 保存模板打印份数设置
     * @param printCopies Map<模板ID, 打印份数>
     */
    override suspend fun saveTemplatePrintCopies(printCopies: Map<String, Int>) {
        val jsonString = gson.toJson(printCopies)
        dataStore.edit { settings ->
            settings[PreferencesKeys.TEMPLATE_PRINT_COPIES] = jsonString
        }
    }
} 