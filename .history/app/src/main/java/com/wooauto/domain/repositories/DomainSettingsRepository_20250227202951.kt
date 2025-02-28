package com.wooauto.domain.repositories

interface DomainSettingsRepository {
    suspend fun getApiUrl(): String
    suspend fun setApiUrl(url: String)
    
    suspend fun getConsumerKey(): String
    suspend fun setConsumerKey(key: String)
    
    suspend fun getConsumerSecret(): String
    suspend fun setConsumerSecret(secret: String)
    
    suspend fun getPrinterType(): String
    suspend fun setPrinterType(type: String)
    
    suspend fun getIsPrinterConnected(): Boolean
    suspend fun setIsPrinterConnected(connected: Boolean)
    
    suspend fun getNotificationsEnabled(): Boolean
    suspend fun setNotificationsEnabled(enabled: Boolean)
    
    suspend fun getLanguage(): String
    suspend fun setLanguage(language: String)
    
    suspend fun getCurrency(): String
    suspend fun setCurrency(currency: String)
} 