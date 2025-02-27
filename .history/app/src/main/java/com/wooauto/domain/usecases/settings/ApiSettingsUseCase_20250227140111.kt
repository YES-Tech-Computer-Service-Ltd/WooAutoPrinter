package com.wooauto.domain.usecases.settings

import com.wooauto.domain.repositories.SettingRepository_domain
import kotlinx.coroutines.flow.Flow

/**
 * API设置用例
 *
 * 该用例负责处理与API相关的设置，包括：
 * - 获取和设置API URL
 * - 获取和设置API密钥
 */
class ApiSettingsUseCase(
    private val settingRepository: SettingRepository_domain
) {
    /**
     * 获取API URL的Flow
     * @return API URL的Flow
     */
    fun getApiUrl(): Flow<String> {
        return settingRepository.getApiUrlFlow()
    }

    /**
     * 设置API URL
     * @param url 新的API URL
     */
    suspend fun setApiUrl(url: String) {
        settingRepository.setApiUrl(url)
    }

    /**
     * 获取API密钥的Flow
     * @return API密钥的Flow
     */
    fun getApiKey(): Flow<String> {
        return settingRepository.getApiKeyFlow()
    }

    /**
     * 设置API密钥
     * @param apiKey 新的API密钥
     */
    suspend fun setApiKey(apiKey: String) {
        settingRepository.setApiKey(apiKey)
    }
} 