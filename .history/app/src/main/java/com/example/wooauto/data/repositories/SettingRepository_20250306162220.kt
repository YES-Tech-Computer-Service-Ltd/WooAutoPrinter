package com.example.wooauto.data.repositories

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.domain.templates.TemplateType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson
) : DomainSettingRepository {

    private val TAG = "SettingRepository"
    private val PREF_PRINTER_CONFIGS = "printer_configs"
    private val PREF_DEFAULT_PRINTER_ID = "default_printer_id"
    private val PREF_WOOFOOD_ENABLED = "woofood_enabled"
    private val PREF_WOOCOMMERCE_ENABLED = "woocommerce_enabled"
    private val PREF_DEFAULT_TEMPLATE_TYPE = "default_template_type"

    // 实现从DomainSettingRepository继承的方法
    override suspend fun getWooCommerceConfig(): WooCommerceConfig {
        // 临时实现，返回空的WooCommerceConfig
        return WooCommerceConfig("", "", "")
    }
    
    override suspend fun saveWooCommerceConfig(config: WooCommerceConfig) {
        // 临时实现
        Log.d(TAG, "保存WooCommerce配置")
    }
    
    override suspend fun clearWooCommerceConfig() {
        // 临时实现
        Log.d(TAG, "清除WooCommerce配置")
    }

    override fun getNotificationEnabledFlow(): Flow<Boolean> = flow { emit(true) }
    override suspend fun setNotificationEnabled(enabled: Boolean) {}
    override fun getApiUrlFlow(): Flow<String> = flow { emit("") }
    override suspend fun setApiUrl(url: String) {}
    override fun getConsumerKeyFlow(): Flow<String> = flow { emit("") }
    override suspend fun setConsumerKey(consumerKey: String) {}
    override fun getConsumerSecretFlow(): Flow<String> = flow { emit("") }
    override suspend fun setConsumerSecret(consumerSecret: String) {}
    override fun getPrinterTypeFlow(): Flow<String> = flow { emit("") }
    override suspend fun setPrinterType(type: String) {}
    override fun getPrinterConnectionFlow(): Flow<Boolean> = flow { emit(false) }
    override suspend fun setPrinterConnection(isConnected: Boolean) {}
    override fun getLanguageFlow(): Flow<String> = flow { emit("en") }
    override suspend fun setLanguage(language: String) {}
    override fun getCurrencyFlow(): Flow<String> = flow { emit("USD") }
    override suspend fun setCurrency(currency: String) {}
    override suspend fun getAllPrinterConfigs(): List<PrinterConfig> = emptyList()
    override suspend fun getPrinterConfig(printerId: String): PrinterConfig? = null
    override suspend fun getDefaultPrinterConfig(): PrinterConfig? = null
    override suspend fun savePrinterConfig(config: PrinterConfig) {}
    override suspend fun deletePrinterConfig(printerId: String) {}
    override fun getPrinterConfigFlow(printerId: String): Flow<PrinterConfig?> = flow { emit(null) }

    override fun getAllPrinterConfigsFlow(): Flow<List<PrinterConfig>> = dataStore.data
        .map { preferences ->
            val configsJson = preferences[stringPreferencesKey(PREF_PRINTER_CONFIGS)] ?: "[]"
            val type = object : TypeToken<List<PrinterConfig>>() {}.type
            gson.fromJson(configsJson, type)
        }

    override suspend fun getDefaultTemplateType(): TemplateType? = withContext(Dispatchers.IO) {
        try {
            val preferences = dataStore.data.first()
            val templateTypeValue = preferences[stringPreferencesKey(PREF_DEFAULT_TEMPLATE_TYPE)]
            if (templateTypeValue != null) {
                return@withContext TemplateType.valueOf(templateTypeValue)
            }
            return@withContext TemplateType.FULL_DETAILS  // 默认返回完整详情模板
        } catch (e: Exception) {
            Log.e(TAG, "获取默认模板类型失败", e)
            return@withContext TemplateType.FULL_DETAILS  // 出错时返回默认模板
        }
    }
    
    override suspend fun saveDefaultTemplateType(templateType: TemplateType) {
        withContext(Dispatchers.IO) {
            try {
                dataStore.edit { preferences ->
                    preferences[stringPreferencesKey(PREF_DEFAULT_TEMPLATE_TYPE)] = templateType.name
                }
                Log.d(TAG, "保存默认模板类型: $templateType")
            } catch (e: Exception) {
                Log.e(TAG, "保存默认模板类型失败", e)
            }
        }
    }
    
    // 实现其他缺失的方法
    override suspend fun getStoreName(): String? = ""
    override suspend fun setStoreName(name: String) {}
    override suspend fun getStoreAddress(): String? = ""
    override suspend fun setStoreAddress(address: String) {}
    override suspend fun getStorePhone(): String? = ""
    override suspend fun setStorePhone(phone: String) {}
    override suspend fun getCurrencySymbol(): String? = "$"
    override suspend fun setCurrencySymbol(symbol: String) {}
    override fun getStoreNameFlow(): Flow<String> = flow { emit("WooAuto Store") }
    override fun getStoreAddressFlow(): Flow<String> = flow { emit("123 Main St") }
    override fun getStorePhoneFlow(): Flow<String> = flow { emit("123-456-7890") }
    override fun getCurrencySymbolFlow(): Flow<String> = flow { emit("$") }
} 