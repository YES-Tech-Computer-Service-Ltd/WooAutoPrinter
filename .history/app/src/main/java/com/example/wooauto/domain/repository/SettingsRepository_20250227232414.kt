package com.example.wooauto.domain.repository

import com.example.wooauto.data.remote.WooCommerceConfig

interface SettingsRepository {
    suspend fun getWooCommerceConfig(): WooCommerceConfig
    suspend fun saveWooCommerceConfig(config: WooCommerceConfig)
    suspend fun clearWooCommerceConfig()
} 