package com.wooauto.domain.usecases.settings

import com.example.wooauto.domain.repositories.DomainSettingRepository
import kotlinx.coroutines.flow.Flow

/**
 * API设置相关用例
 */
class ApiSettingsUseCase(private val repository: DomainSettingRepository) {
    
    /**
     * 获取API URL
     */
    fun getApiUrl(): Flow<String> {
        return repository.getApiUrlFlow()
    }
    
    /**
     * 设置API URL
     */
    suspend fun setApiUrl(url: String) {
        repository.setApiUrl(url)
    }
    
    /**
     * 获取Consumer Key
     */
    fun getConsumerKey(): Flow<String> {
        return repository.getConsumerKeyFlow()
    }
    
    /**
     * 设置Consumer Key
     */
    suspend fun setConsumerKey(key: String) {
        repository.setConsumerKey(key)
    }
    
    /**
     * 获取Consumer Secret
     */
    fun getConsumerSecret(): Flow<String> {
        return repository.getConsumerSecretFlow()
    }
    
    /**
     * 设置Consumer Secret
     */
    suspend fun setConsumerSecret(secret: String) {
        repository.setConsumerSecret(secret)
    }
    
    /**
     * 清除API设置
     */
    suspend fun clearApiSettings() {
        repository.clearWooCommerceConfig()
    }
} 