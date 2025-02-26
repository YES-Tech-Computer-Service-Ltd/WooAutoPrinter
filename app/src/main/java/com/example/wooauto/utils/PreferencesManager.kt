package com.example.wooauto.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property for Context to create a single instance of DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wooauto_preferences")

class PreferencesManager(private val context: Context) {

    // Keys for preferences
    companion object {
        // First launch
        private val FIRST_LAUNCH = booleanPreferencesKey("first_launch")

        // API Settings
        private val WEBSITE_URL = stringPreferencesKey("website_url")
        private val API_KEY = stringPreferencesKey("api_key")
        private val API_SECRET = stringPreferencesKey("api_secret")
        private val POLLING_INTERVAL = intPreferencesKey("polling_interval")
        private val ORDER_PLUGIN = stringPreferencesKey("order_plugin")

        // Sound Settings
        private val SOUND_TYPE = stringPreferencesKey("sound_type")
        private val CUSTOM_SOUND_PATH = stringPreferencesKey("custom_sound_path")
        private val CUSTOM_TEXT = stringPreferencesKey("custom_text")
        private val VOICE_TYPE = stringPreferencesKey("voice_type")
        private val SOUND_VOLUME = intPreferencesKey("sound_volume")
        private val PLAY_COUNT = intPreferencesKey("play_count")

        // Auto-close Settings
        private val AUTO_CLOSE_NOTIFICATION_SECONDS = intPreferencesKey("auto_close_notification_seconds")

        // App Settings
        private val LANGUAGE = stringPreferencesKey("language")

        // Migration version
        private val PREFERENCES_VERSION = intPreferencesKey("preferences_version")
        private const val CURRENT_VERSION = 1

        // Default Values
        const val DEFAULT_POLLING_INTERVAL = 60 // seconds
        const val DEFAULT_SOUND_VOLUME = 80 // percent
        const val DEFAULT_PLAY_COUNT = 3
        const val DEFAULT_AUTO_CLOSE_SECONDS = 15
        const val DEFAULT_LANGUAGE = "en" // English

        // 获取系统默认语言的静态方法
        @JvmStatic
        fun getSystemDefaultLanguage(): String {
            val locale = java.util.Locale.getDefault()
            return when (locale.language) {
                "zh" -> "zh"
                else -> "en"
            }
        }
    }

    // 获取默认语言的同步方法
    fun getDefaultLanguage(): String {
        return getSystemDefaultLanguage()
    }

    // First Launch
    val isFirstLaunch: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FIRST_LAUNCH] ?: true
    }

    suspend fun setFirstLaunch(isFirst: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FIRST_LAUNCH] = isFirst
        }
    }

    // API Settings
    val websiteUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[WEBSITE_URL] ?: ""
    }

    suspend fun setWebsiteUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[WEBSITE_URL] = url
        }
    }

    val apiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_KEY] ?: ""
    }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key
        }
    }

    val apiSecret: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_SECRET] ?: ""
    }

    suspend fun setApiSecret(secret: String) {
        context.dataStore.edit { preferences ->
            preferences[API_SECRET] = secret
        }
    }

    val pollingInterval: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[POLLING_INTERVAL] ?: DEFAULT_POLLING_INTERVAL
    }

    suspend fun setPollingInterval(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[POLLING_INTERVAL] = seconds
        }
    }

    val orderPlugin: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[ORDER_PLUGIN] ?: "woocommerce_food"
    }

    suspend fun setOrderPlugin(plugin: String) {
        context.dataStore.edit { preferences ->
            preferences[ORDER_PLUGIN] = plugin
        }
    }

    // Sound Settings
    val soundType: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SOUND_TYPE] ?: "system_sound"
    }

    suspend fun setSoundType(type: String) {
        context.dataStore.edit { preferences ->
            preferences[SOUND_TYPE] = type
        }
    }

    val customSoundPath: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_SOUND_PATH] ?: ""
    }

    suspend fun setCustomSoundPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_SOUND_PATH] = path
        }
    }

    val customText: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_TEXT] ?: ""
    }

    suspend fun setCustomText(text: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_TEXT] = text
        }
    }

    val voiceType: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[VOICE_TYPE] ?: "female"
    }

    suspend fun setVoiceType(type: String) {
        context.dataStore.edit { preferences ->
            preferences[VOICE_TYPE] = type
        }
    }

    val soundVolume: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SOUND_VOLUME] ?: DEFAULT_SOUND_VOLUME
    }

    suspend fun setSoundVolume(volume: Int) {
        context.dataStore.edit { preferences ->
            preferences[SOUND_VOLUME] = volume
        }
    }

    val playCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PLAY_COUNT] ?: DEFAULT_PLAY_COUNT
    }

    suspend fun setPlayCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[PLAY_COUNT] = count
        }
    }

    // Auto-close Settings
    val autoCloseNotificationSeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[AUTO_CLOSE_NOTIFICATION_SECONDS] ?: DEFAULT_AUTO_CLOSE_SECONDS
    }

    suspend fun setAutoCloseNotificationSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CLOSE_NOTIFICATION_SECONDS] = seconds
        }
    }

    // App Settings
    val language: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE] ?: DEFAULT_LANGUAGE
    }

    suspend fun setLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE] = languageCode
        }
    }

    /**
     * 执行配置迁移
     */
    suspend fun migrateIfNeeded() {
        context.dataStore.edit { preferences ->
            val currentVersion = preferences[PREFERENCES_VERSION] ?: 0
            
            if (currentVersion < CURRENT_VERSION) {
                // 从 SharedPreferences 迁移数据
                val sharedPrefs = context.getSharedPreferences("WooAutoPrefs", Context.MODE_PRIVATE)
                
                // 迁移 API 设置
                sharedPrefs.getString("website_url", null)?.let { preferences[WEBSITE_URL] = it }
                sharedPrefs.getString("api_key", null)?.let { preferences[API_KEY] = it }
                sharedPrefs.getString("api_secret", null)?.let { preferences[API_SECRET] = it }
                
                // 迁移其他设置
                preferences[POLLING_INTERVAL] = sharedPrefs.getInt("polling_interval", DEFAULT_POLLING_INTERVAL)
                preferences[LANGUAGE] = sharedPrefs.getString("language", DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
                
                // 更新版本号
                preferences[PREFERENCES_VERSION] = CURRENT_VERSION
                
                // 清除旧的 SharedPreferences
                sharedPrefs.edit().clear().apply()
            }
        }
    }
}