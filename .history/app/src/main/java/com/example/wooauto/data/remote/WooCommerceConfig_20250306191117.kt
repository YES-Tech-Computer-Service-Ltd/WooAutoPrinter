package com.example.wooauto.data.remote

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * WooCommerce API配置 - 远程版本
 * 
 * 注意：此类是简化的配置数据类，建议优先使用本地配置类
 * （com.example.wooauto.data.local.WooCommerceConfig）
 * 此类将继续支持，但未来可能会被弃用
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
    fun isValid(): Boolean {
        val isValid = siteUrl.isNotBlank() && consumerKey.isNotBlank() && consumerSecret.isNotBlank()
        
        // 同步到全局配置状态
        updateConfigurationStatus(isValid)
        
        Log.d(TAG, "远程配置有效性检查: $isValid (siteUrl=${siteUrl.isNotBlank()}, consumerKey=${consumerKey.isNotBlank()}, consumerSecret=${consumerSecret.isNotBlank()})")
        
        return isValid
    }
    
    // 向后兼容，为了和新代码兼容
    val apiUrl: String
        get() = siteUrl
    
    companion object {
        private const val TAG = "RemoteWooConfig"
        private val _isConfigured = MutableStateFlow(false)
        val isConfigured = _isConfigured.asStateFlow()
        
        fun updateConfigurationStatus(isConfigured: Boolean) {
            Log.d(TAG, "更新配置状态: isConfigured=$isConfigured")
            _isConfigured.value = isConfigured
        }
    }
    
    override fun toString(): String {
        return "RemoteWooCommerceConfig(siteUrl='$siteUrl', consumerKey='${consumerKey.take(5)}***', consumerSecret='${consumerSecret.take(5)}***', pollingInterval=$pollingInterval, useWooCommerceFood=$useWooCommerceFood)"
    }
} 