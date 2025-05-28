package com.example.wooauto.data.remote

import android.util.Log
import com.example.wooauto.data.remote.impl.WooCommerceApiImpl
import javax.inject.Inject
import javax.inject.Singleton

interface WooCommerceApiFactory {
    fun createApi(config: WooCommerceConfig): WooCommerceApi
}

@Singleton
class WooCommerceApiFactoryImpl @Inject constructor() : WooCommerceApiFactory {
    
    private var cachedApi: WooCommerceApi? = null
    private var cachedConfig: WooCommerceConfig? = null
    
    override fun createApi(config: WooCommerceConfig): WooCommerceApi {
        // 检查是否可以复用现有实例
        if (cachedApi != null && cachedConfig != null && isSameConfig(cachedConfig!!, config)) {
            Log.d("WooCommerceApiFactory", "复用现有WooCommerceApi实例，站点URL: ${config.siteUrl}")
            return cachedApi!!
        }
        
        Log.d("WooCommerceApiFactory", "创建新的WooCommerceApi实例，站点URL: ${config.siteUrl}")
        val newApi = WooCommerceApiImpl(config)
        
        // 缓存新实例和配置
        cachedApi = newApi
        cachedConfig = config
        
        return newApi
    }
    
    private fun isSameConfig(config1: WooCommerceConfig, config2: WooCommerceConfig): Boolean {
        return config1.siteUrl == config2.siteUrl &&
               config1.consumerKey == config2.consumerKey &&
               config1.consumerSecret == config2.consumerSecret
    }
} 