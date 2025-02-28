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
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.repositories.DomainSettingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingDao: SettingDao
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
        
        // 默认值
        const val DEFAULT_API_URL = "https://example.com/wp-json/wc/v3/"
        const val DEFAULT_LANGUAGE = "en"
        const val DEFAULT_CURRENCY = "USD"
        const val DEFAULT_STORE_NAME = "我的商店"
        const val DEFAULT_CURRENCY_SYMBOL = "¥"
    }

    // WooCommerce 配置相关方法 - 使用 DataStore
    override suspend fun getWooCommerceConfig(): WooCommerceConfig {
        Log.d("SettingsRepositoryImpl", "获取WooCommerce配置")
        val preferences = context.dataStore.data.first()
        
        return WooCommerceConfig(
            siteUrl = preferences[PreferencesKeys.SITE_URL] ?: "",
            consumerKey = preferences[PreferencesKeys.CONSUMER_KEY] ?: "",
            consumerSecret = preferences[PreferencesKeys.CONSUMER_SECRET] ?: "",
            pollingInterval = preferences[PreferencesKeys.POLLING_INTERVAL] ?: 30,
            useWooCommerceFood = preferences[PreferencesKeys.USE_WOOCOMMERCE_FOOD] ?: false
        )
    }

    override suspend fun saveWooCommerceConfig(config: WooCommerceConfig) {
        Log.d("SettingsRepositoryImpl", "保存WooCommerce配置: $config")
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SITE_URL] = config.siteUrl
            preferences[PreferencesKeys.CONSUMER_KEY] = config.consumerKey
            preferences[PreferencesKeys.CONSUMER_SECRET] = config.consumerSecret
            preferences[PreferencesKeys.POLLING_INTERVAL] = config.pollingInterval
            preferences[PreferencesKeys.USE_WOOCOMMERCE_FOOD] = config.useWooCommerceFood
        }
    }

    override suspend fun clearWooCommerceConfig() {
        Log.d("SettingsRepositoryImpl", "清除WooCommerce配置")
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.SITE_URL)
            preferences.remove(PreferencesKeys.CONSUMER_KEY)
            preferences.remove(PreferencesKeys.CONSUMER_SECRET)
            preferences.remove(PreferencesKeys.POLLING_INTERVAL)
            preferences.remove(PreferencesKeys.USE_WOOCOMMERCE_FOOD)
        }
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
        val allSettings = settingDao.getAllSettings()
        return allSettings
            .filter { it.key.startsWith(KEY_PREFIX_PRINTER_CONFIGS) }
            .mapNotNull { 
                try {
                    val json = it.value
                    SettingMapper.jsonToPrinterConfig(json)
                } catch (e: Exception) {
                    Log.e("SettingsRepository", "解析打印机配置失败: ${e.message}")
                    null
                }
            }
    }
    
    override suspend fun getPrinterConfig(printerId: String): PrinterConfig? {
        val key = KEY_PREFIX_PRINTER_CONFIGS + printerId
        val json = settingDao.getSetting(key)?.value ?: return null
        return try {
            SettingMapper.jsonToPrinterConfig(json)
        } catch (e: Exception) {
            Log.e("SettingsRepository", "解析打印机配置失败: ${e.message}")
            null
        }
    }
    
    override suspend fun getDefaultPrinterConfig(): PrinterConfig? {
        val defaultPrinterId = settingDao.getSetting(KEY_DEFAULT_PRINTER_ID)?.value
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
        settingDao.deleteSetting(key)
        
        // 如果删除的是默认打印机，清除默认打印机ID
        val defaultPrinterId = settingDao.getSetting(KEY_DEFAULT_PRINTER_ID)?.value
        if (defaultPrinterId == printerId) {
            settingDao.deleteSetting(KEY_DEFAULT_PRINTER_ID)
        }
    }
    
    override fun getPrinterConfigFlow(printerId: String): Flow<PrinterConfig?> {
        val key = KEY_PREFIX_PRINTER_CONFIGS + printerId
        return settingDao.getSettingFlow(key).flow {
            emit(if (it == null) null else {
                try {
                    SettingMapper.jsonToPrinterConfig(it.value)
                } catch (e: Exception) {
                    Log.e("SettingsRepository", "解析打印机配置失败: ${e.message}")
                    null
                }
            })
        }
    }
    
    override fun getAllPrinterConfigsFlow(): Flow<List<PrinterConfig>> {
        return settingDao.getAllSettingsFlow().flow {
            val printerConfigs = it
                .filter { setting -> setting.key.startsWith(KEY_PREFIX_PRINTER_CONFIGS) }
                .mapNotNull { setting -> 
                    try {
                        SettingMapper.jsonToPrinterConfig(setting.value)
                    } catch (e: Exception) {
                        Log.e("SettingsRepository", "解析打印机配置失败: ${e.message}")
                        null
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
} 