package com.example.wooauto.utils

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "WooAutoPrefs"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_SECRET = "api_secret"
        private const val KEY_POLLING_INTERVAL = "polling_interval"
        private const val KEY_WEBSITE_URL = "website_url"
        private const val DEFAULT_POLLING_INTERVAL = 60 // 默认60秒
        private const val DEFAULT_LANGUAGE = "en" // 默认英语
    }

    fun isFirstLaunch(): Boolean = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

    fun setFirstLaunch(isFirst: Boolean) {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, isFirst).apply()
    }

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE

    fun setLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun getApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""

    fun setApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getApiSecret(): String = prefs.getString(KEY_API_SECRET, "") ?: ""

    fun setApiSecret(apiSecret: String) {
        prefs.edit().putString(KEY_API_SECRET, apiSecret).apply()
    }

    fun getWebsiteUrl(): String = prefs.getString(KEY_WEBSITE_URL, "") ?: ""

    fun setWebsiteUrl(url: String) {
        prefs.edit().putString(KEY_WEBSITE_URL, url).apply()
    }

    fun getPollingInterval(): Int = prefs.getInt(KEY_POLLING_INTERVAL, DEFAULT_POLLING_INTERVAL)

    fun setPollingInterval(interval: Int) {
        prefs.edit().putInt(KEY_POLLING_INTERVAL, interval).apply()
    }
}