package com.example.wooauto.data.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * WooCommerce API配置
 */
data class WooCommerceConfig(
    val apiUrl: String,
    val consumerKey: String,
    val consumerSecret: String
) {
    /**
     * 检查配置是否有效
     */
    val isValid: Boolean
        get() = apiUrl.isNotEmpty() && consumerKey.isNotEmpty() && consumerSecret.isNotEmpty()
    
    companion object {
        private val _isConfigured = MutableStateFlow(false)
        val isConfigured = _isConfigured.asStateFlow()
        
        fun updateConfigurationStatus(isConfigured: Boolean) {
            _isConfigured.value = isConfigured
        }
    }
} 