package com.example.wooauto.data.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * WooCommerce API配置
 */
data class WooCommerceConfig(
    val siteUrl: String,
    val consumerKey: String,
    val consumerSecret: String,
    val pollingInterval: Int = 30,
    val useWooCommerceFood: Boolean = false
) {
    /**
     * 检查配置是否有效
     */
    fun isValid(): Boolean = siteUrl.isNotBlank() && consumerKey.isNotBlank() && consumerSecret.isNotBlank()
    
    // 向后兼容，为了和新代码兼容
    val apiUrl: String
        get() = siteUrl
    
    companion object {
        private val _isConfigured = MutableStateFlow(false)
        val isConfigured = _isConfigured.asStateFlow()
        
        fun updateConfigurationStatus(isConfigured: Boolean) {
            _isConfigured.value = isConfigured
        }
    }
    
    override fun toString(): String {
        return "WooCommerceConfig(siteUrl='$siteUrl', consumerKey='${consumerKey.take(5)}***', consumerSecret='${consumerSecret.take(5)}***', pollingInterval=$pollingInterval, useWooCommerceFood=$useWooCommerceFood)"
    }
} 