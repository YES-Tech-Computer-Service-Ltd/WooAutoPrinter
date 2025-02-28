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
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.domain.repositories.DomainSettingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DomainSettingRepository {

    private object PreferencesKeys {
        val SITE_URL = stringPreferencesKey("site_url")
        val CONSUMER_KEY = stringPreferencesKey("consumer_key")
        val CONSUMER_SECRET = stringPreferencesKey("consumer_secret")
        val POLLING_INTERVAL = intPreferencesKey("polling_interval")
        val USE_WOOCOMMERCE_FOOD = booleanPreferencesKey("use_woocommerce_food")
    }

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
} 