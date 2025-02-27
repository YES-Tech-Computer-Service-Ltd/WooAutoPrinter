package com.wooauto.domain.usecases.settings

import com.wooauto.domain.repositories.SettingRepository_domain
import kotlinx.coroutines.flow.Flow

/**
 * 业务设置用例
 *
 * 该用例负责处理与业务相关的设置，包括：
 * - 货币设置（仅作为显示单位）
 * - 语言设置
 */
class GeneralSettingsUseCase(
    private val settingRepository: SettingRepository_domain
) {
    /**
     * 获取货币设置的Flow
     * @return 货币代码的Flow
     */
    fun getCurrency(): Flow<String> {
        return settingRepository.getCurrencyFlow()
    }

    /**
     * 设置货币
     * @param currency 货币代码
     */
    suspend fun setCurrency(currency: String) {
        settingRepository.setCurrency(currency)
    }

    /**
     * 获取语言设置的Flow
     * @return 语言代码的Flow
     */
    fun getLanguage(): Flow<String> {
        return settingRepository.getLanguageFlow()
    }

    /**
     * 设置语言
     * @param language 语言代码
     */
    suspend fun setLanguage(language: String) {
        settingRepository.setLanguage(language)
    }
} 