package com.example.wooauto.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// DataStore扩展
private val Context.wordpressUpdaterDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wordpress_updater_config"
)

/**
 * WordPress更新器配置管理类
 * 用于存储和管理WordPress API的配置信息
 */
@Singleton
class WordPressUpdaterConfig @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val API_BASE_URL = stringPreferencesKey("api_base_url")
        private val API_TOKEN = stringPreferencesKey("api_token")
        
        // 默认配置
        const val DEFAULT_BASE_URL = "https://yestech.ca/wp-json/app-updater/v1"
        const val DEFAULT_TOKEN = "hFIqLutLwmDqEXpJo2AwR2atLeYw1JWU"
    }
    
    /**
     * API基础URL流
     */
    val apiBaseUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[API_BASE_URL] ?: DEFAULT_BASE_URL
    }
    
    /**
     * API访问token流
     */
    val apiToken: Flow<String> = dataStore.data.map { preferences ->
        preferences[API_TOKEN] ?: DEFAULT_TOKEN
    }
    
    /**
     * 检查配置是否有效
     */
    val isConfiguredFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        val hasUrl = preferences[API_BASE_URL]?.isNotEmpty() ?: true // 默认有URL
        val hasToken = preferences[API_TOKEN]?.isNotEmpty() ?: true  // 默认有token
        hasUrl && hasToken
    }
    
    /**
     * 更新API基础URL
     */
    suspend fun updateApiBaseUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[API_BASE_URL] = url.trim()
        }
    }
    
    /**
     * 更新API访问token
     */
    suspend fun updateApiToken(token: String) {
        dataStore.edit { preferences ->
            preferences[API_TOKEN] = token.trim()
        }
    }
    
    /**
     * 获取当前配置
     */
    suspend fun getCurrentConfig(): Pair<String, String> {
        val preferences = dataStore.data.first()
        val url = preferences[API_BASE_URL] ?: DEFAULT_BASE_URL
        val token = preferences[API_TOKEN] ?: DEFAULT_TOKEN
        return Pair(url, token)
    }
    
    /**
     * 重置为默认配置
     */
    suspend fun resetToDefault() {
        dataStore.edit { preferences ->
            preferences[API_BASE_URL] = DEFAULT_BASE_URL
            preferences[API_TOKEN] = DEFAULT_TOKEN
        }
    }
} 