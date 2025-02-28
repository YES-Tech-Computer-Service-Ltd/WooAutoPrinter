package com.wooauto.domain.usecases.settings

import com.example.wooauto.domain.repositories.DomainSettingRepository
import kotlinx.coroutines.flow.Flow

/**
 * 通用设置相关用例
 */
class GeneralSettingsUseCase(private val repository: DomainSettingRepository) {
    
    /**
     * 获取语言设置
     */
    fun getLanguage(): Flow<String> {
        return repository.getLanguageFlow()
    }
    
    /**
     * 设置语言
     */
    suspend fun setLanguage(language: String) {
        repository.setLanguage(language)
    }
    
    /**
     * 获取货币设置
     */
    fun getCurrency(): Flow<String> {
        return repository.getCurrencyFlow()
    }
    
    /**
     * 设置货币
     */
    suspend fun setCurrency(currency: String) {
        repository.setCurrency(currency)
    }
    
    /**
     * 获取商店名称
     */
    fun getStoreName(): Flow<String> {
        return repository.getStoreNameFlow()
    }
    
    /**
     * 设置商店名称
     */
    suspend fun setStoreName(name: String) {
        repository.setStoreName(name)
    }
    
    /**
     * 获取商店地址
     */
    fun getStoreAddress(): Flow<String> {
        return repository.getStoreAddressFlow()
    }
    
    /**
     * 设置商店地址
     */
    suspend fun setStoreAddress(address: String) {
        repository.setStoreAddress(address)
    }
    
    /**
     * 获取商店电话
     */
    fun getStorePhone(): Flow<String> {
        return repository.getStorePhoneFlow()
    }
    
    /**
     * 设置商店电话
     */
    suspend fun setStorePhone(phone: String) {
        repository.setStorePhone(phone)
    }
    
    /**
     * 获取货币符号
     */
    fun getCurrencySymbol(): Flow<String> {
        return repository.getCurrencySymbolFlow()
    }
    
    /**
     * 设置货币符号
     */
    suspend fun setCurrencySymbol(symbol: String) {
        repository.setCurrencySymbol(symbol)
    }
} 