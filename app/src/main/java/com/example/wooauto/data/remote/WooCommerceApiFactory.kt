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
    override fun createApi(config: WooCommerceConfig): WooCommerceApi {
        Log.d("WooCommerceApiFactory", "创建WooCommerceApi，站点URL: ${config.siteUrl}")
        return WooCommerceApiImpl(config)
    }
} 