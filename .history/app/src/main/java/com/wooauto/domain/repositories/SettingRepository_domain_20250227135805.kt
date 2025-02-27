package com.wooauto.domain.repositories

import kotlinx.coroutines.flow.Flow

interface SettingRepository_domain {
    // 通知设置
    fun getNotificationEnabledFlow(): Flow<Boolean>
    suspend fun setNotificationEnabled(enabled: Boolean)

    // 网络 (API) 设置
    fun getApiUrlFlow(): Flow<String>
    suspend fun setApiUrl(url: String)

    fun getApiKeyFlow(): Flow<String>
    suspend fun setApiKey(apiKey: String)

    // 打印机设置
    fun getPrinterTypeFlow(): Flow<String>
    suspend fun setPrinterType(type: String)

    fun getPrinterConnectionFlow(): Flow<Boolean>
    suspend fun setPrinterConnection(isConnected: Boolean)

    // 语言设置
    fun getLanguageFlow(): Flow<String>
    suspend fun setLanguage(language: String)

    // 产品信息设置
    fun getCurrencyFlow(): Flow<String>
    suspend fun setCurrency(currency: String)

    fun getTaxRateFlow(): Flow<Float>
    suspend fun setTaxRate(taxRate: Float)
}
