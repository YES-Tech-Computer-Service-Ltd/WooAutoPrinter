package com.example.wooauto.data.local

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.example.wooauto.data.remote.WooCommerceConfig as RemoteConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WooCommerce配置管理类
 * 负责存储和获取WooCommerce API的配置信息
 * 
 * 这是项目中唯一的WooCommerce配置类，所有组件都应该使用这个类
 */
@Singleton
class WooCommerceConfig @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    // 定义存储键
    companion object {
        private val TAG = "WooCommerceConfig"
        
        val SITE_URL = stringPreferencesKey("site_url")
        val CONSUMER_KEY = stringPreferencesKey("consumer_key")
        val CONSUMER_SECRET = stringPreferencesKey("consumer_secret")
        val POLLING_INTERVAL = intPreferencesKey("polling_interval")
        val USE_WOOCOMMERCE_FOOD = booleanPreferencesKey("use_woocommerce_food")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        
        // 默认值
        const val DEFAULT_POLLING_INTERVAL = 30 // 默认轮询间隔30秒
        const val DEFAULT_SITE_URL = ""
        const val DEFAULT_CONSUMER_KEY = ""
        const val DEFAULT_CONSUMER_SECRET = ""
        const val DEFAULT_KEEP_SCREEN_ON = false
        
        // 配置状态管理
        private val _isConfigured = MutableStateFlow(false)
        val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()
        
        /**
         * 更新配置状态
         * @param configured 是否已配置
         */
        fun updateConfigurationStatus(configured: Boolean) {
            Log.d(TAG, "更新配置状态: $configured")
            _isConfigured.value = configured
        }
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
    
    // 是否保持屏幕常亮
    val keepScreenOn: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEEP_SCREEN_ON] ?: DEFAULT_KEEP_SCREEN_ON
    }
    
    // 是否已配置
    val isConfiguredFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        val hasKey = preferences[CONSUMER_KEY]?.isNotEmpty() ?: false
        val hasSecret = preferences[CONSUMER_SECRET]?.isNotEmpty() ?: false
        val hasUrl = preferences[SITE_URL]?.isNotEmpty() ?: false
        val isValid = hasKey && hasSecret && hasUrl
        
        // 同步到静态状态
        updateConfigurationStatus(isValid)
        
        isValid
    }
    
    // 更新站点URL
    suspend fun updateSiteUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[SITE_URL] = url
        }
        
        // 检查配置并更新状态
        checkAndUpdateConfigStatus()
    }
    
    // 更新Consumer Key
    suspend fun updateConsumerKey(key: String) {
        dataStore.edit { preferences ->
            preferences[CONSUMER_KEY] = key
        }
        
        // 检查配置并更新状态
        checkAndUpdateConfigStatus()
    }
    
    // 更新Consumer Secret
    suspend fun updateConsumerSecret(secret: String) {
        dataStore.edit { preferences ->
            preferences[CONSUMER_SECRET] = secret
        }
        
        // 检查配置并更新状态
        checkAndUpdateConfigStatus()
    }
    
    // 更新轮询间隔
    suspend fun updatePollingInterval(interval: Int) {
        Log.d(TAG, "更新轮询间隔: ${interval}秒")
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
    
    // 更新是否保持屏幕常亮
    suspend fun updateKeepScreenOn(keepOn: Boolean) {
        Log.d(TAG, "更新屏幕常亮设置: $keepOn")
        dataStore.edit { preferences ->
            preferences[KEEP_SCREEN_ON] = keepOn
        }
    }
    
    // 获取API基础URL
    fun getBaseUrl(url: String): String {
        val normalized = com.example.wooauto.utils.UrlNormalizer.sanitizeSiteUrl(url)
        return com.example.wooauto.utils.UrlNormalizer.buildApiBaseUrl(normalized)
    }
    
    /**
     * 检查配置有效性并更新状态
     */
    private suspend fun checkAndUpdateConfigStatus() {
        val preferences = dataStore.data.first()
        val hasKey = preferences[CONSUMER_KEY]?.isNotEmpty() ?: false
        val hasSecret = preferences[CONSUMER_SECRET]?.isNotEmpty() ?: false
        val hasUrl = preferences[SITE_URL]?.isNotEmpty() ?: false
        val isValid = hasKey && hasSecret && hasUrl
        
        updateConfigurationStatus(isValid)
    }
    
    /**
     * 将当前配置转换为远程配置对象
     * 用于兼容旧代码，长期应该改用本类
     */
    suspend fun toRemoteConfig(): RemoteConfig {
        val prefs = dataStore.data.first()
        return RemoteConfig(
            siteUrl = prefs[SITE_URL] ?: DEFAULT_SITE_URL,
            consumerKey = prefs[CONSUMER_KEY] ?: DEFAULT_CONSUMER_KEY,
            consumerSecret = prefs[CONSUMER_SECRET] ?: DEFAULT_CONSUMER_SECRET,
            pollingInterval = prefs[POLLING_INTERVAL] ?: DEFAULT_POLLING_INTERVAL,
            useWooCommerceFood = prefs[USE_WOOCOMMERCE_FOOD] ?: false
        )
    }
    
    /**
     * 保存远程配置对象到本地配置
     * 用于兼容旧代码，长期应该改用本类的方法
     */
    suspend fun saveRemoteConfig(remoteConfig: RemoteConfig) {
        dataStore.edit { preferences ->
            preferences[SITE_URL] = remoteConfig.siteUrl
            preferences[CONSUMER_KEY] = remoteConfig.consumerKey
            preferences[CONSUMER_SECRET] = remoteConfig.consumerSecret
            preferences[POLLING_INTERVAL] = remoteConfig.pollingInterval
            preferences[USE_WOOCOMMERCE_FOOD] = remoteConfig.useWooCommerceFood
        }
        
        // 检查配置并更新状态
        val isValid = remoteConfig.isValid()
        updateConfigurationStatus(isValid)
    }
} 