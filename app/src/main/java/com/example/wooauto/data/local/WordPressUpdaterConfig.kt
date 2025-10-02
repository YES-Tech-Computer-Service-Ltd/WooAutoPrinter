package com.example.wooauto.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.wooauto.di.WordPressUpdaterDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WordPress更新器配置管理类
 * 用于存储和管理WordPress API的配置信息
 */
@Singleton
class WordPressUpdaterConfig @Inject constructor(
    @WordPressUpdaterDataStore private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val API_BASE_URL = stringPreferencesKey("api_base_url")
        private val API_TOKEN = stringPreferencesKey("api_token")
        
        // 默认配置
        const val DEFAULT_BASE_URL = "https://yestech.ca/wp-json/app-updater/v1"
        const val DEFAULT_TOKEN = "hFIqLutLwmDqEXpJo2AwR2atLeYw1JWU"
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

}