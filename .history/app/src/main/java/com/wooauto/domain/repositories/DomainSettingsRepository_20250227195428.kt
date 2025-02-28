package com.wooauto.domain.repositories

import kotlinx.coroutines.flow.Flow

interface DomainSettingsRepository {
    fun getNotificationEnabledFlow(): Flow<Boolean>
    suspend fun setNotificationEnabled(enabled: Boolean)
    
    fun getApiUrlFlow(): Flow<String>
    suspend fun setApiUrl(url: String)
    
    fun getConsumerKeyFlow(): Flow<String>
    suspend fun setConsumerKey(key: String)
    
    fun getConsumerSecretFlow(): Flow<String>
    suspend fun setConsumerSecret(secret: String)
    
    fun getPrinterTypeFlow(): Flow<String>
    suspend fun setPrinterType(type: String)
    
    fun getPrinterConnectionFlow(): Flow<Boolean>
    suspend fun setPrinterConnection(isConnected: Boolean)
    
    fun getLanguageFlow(): Flow<String>
    suspend fun setLanguage(language: String)
    
    fun getCurrencyFlow(): Flow<String>
    suspend fun setCurrency(currency: String)
} 