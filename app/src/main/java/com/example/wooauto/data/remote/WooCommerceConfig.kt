package com.example.wooauto.data.remote

data class WooCommerceConfig(
    val siteUrl: String,
    val consumerKey: String,
    val consumerSecret: String,
    val pollingInterval: Int,
    val useWooCommerceFood: Boolean
) {
    override fun toString(): String {
        return "WooCommerceConfig(siteUrl='$siteUrl', consumerKey='${consumerKey.take(5)}***', consumerSecret='${consumerSecret.take(5)}***', pollingInterval=$pollingInterval, useWooCommerceFood=$useWooCommerceFood)"
    }
    
    // 检查配置是否有效
    fun isValid(): Boolean {
        return siteUrl.isNotBlank() && consumerKey.isNotBlank() && consumerSecret.isNotBlank()
    }
} 