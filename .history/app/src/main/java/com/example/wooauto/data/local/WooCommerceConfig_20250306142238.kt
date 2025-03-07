package com.example.wooauto.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WooCommerce配置管理类
 * 负责存储和获取WooCommerce API的配置信息
 */
@Singleton
class WooCommerceConfig @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    // 定义存储键
    companion object {
        val SITE_URL = stringPreferencesKey("site_url")
        val CONSUMER_KEY = stringPreferencesKey("consumer_key")
        val CONSUMER_SECRET = stringPreferencesKey("consumer_secret")
        val POLLING_INTERVAL = intPreferencesKey("polling_interval")
        val USE_WOOCOMMERCE_FOOD = booleanPreferencesKey("use_woocommerce_food")
        
        // 默认值
        const val DEFAULT_POLLING_INTERVAL = 30 // 默认轮询间隔30秒
        const val DEFAULT_SITE_URL = "https://your-woocommerce-site.com"
        const val DEFAULT_CONSUMER_KEY = ""
        const val DEFAULT_CONSUMER_SECRET = ""
    }
    
    // 获取站点URL
    val siteUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[SITE_URL] ?: DEFAULT_SITE_URL
    }
    
    // 获取Consumer Key
    val consumerKey: Flow<String> = dataStore.data.map { preferences ->
        preferences[CONSUMER_KEY] ?: DEFAULT_CONSUMER_KEY
    }
    
    // 获取Consumer Secret
    val consumerSecret: Flow<String> = dataStore.data.map { preferences ->
        preferences[CONSUMER_SECRET] ?: DEFAULT_CONSUMER_SECRET
    }
    
    // 获取轮询间隔
    val pollingInterval: Flow<Int> = dataStore.data.map { preferences ->
        preferences[POLLING_INTERVAL] ?: DEFAULT_POLLING_INTERVAL
    }
    
    // 是否使用WooCommerce Food插件
    val useWooCommerceFood: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[USE_WOOCOMMERCE_FOOD] ?: false
    }
    
    // 是否已配置
    val isConfigured: Flow<Boolean> = dataStore.data.map { preferences ->
        val hasKey = preferences[CONSUMER_KEY]?.isNotEmpty() ?: false
        val hasSecret = preferences[CONSUMER_SECRET]?.isNotEmpty() ?: false
        val hasUrl = preferences[SITE_URL]?.isNotEmpty() ?: false
        hasKey && hasSecret && hasUrl
    }
    
    // 更新站点URL
    suspend fun updateSiteUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[SITE_URL] = url
        }
    }
    
    // 更新Consumer Key
    suspend fun updateConsumerKey(key: String) {
        dataStore.edit { preferences ->
            preferences[CONSUMER_KEY] = key
        }
    }
    
    // 更新Consumer Secret
    suspend fun updateConsumerSecret(secret: String) {
        dataStore.edit { preferences ->
            preferences[CONSUMER_SECRET] = secret
        }
    }
    
    // 更新轮询间隔
    suspend fun updatePollingInterval(interval: Int) {
        dataStore.edit { preferences ->
            preferences[POLLING_INTERVAL] = interval
        }
    }
    
    // 更新是否使用WooCommerce Food插件
    suspend fun updateUseWooCommerceFood(use: Boolean) {
        dataStore.edit { preferences ->
            preferences[USE_WOOCOMMERCE_FOOD] = use
        }
    }
    
    // 获取API基础URL
    fun getBaseUrl(url: String): String {
        val baseUrl = url.trimEnd('/')
        return if (!baseUrl.endsWith("/wp-json/wc/v3")) {
            "$baseUrl/wp-json/wc/v3/"
        } else {
            "$baseUrl/"
        }
    }
} 