package com.wooauto.domain.usecases.settings

import com.wooauto.domain.repositories.DomainSettingRepository
import kotlinx.coroutines.flow.Flow

/**
 * 通知设置用例
 *
 * 该用例负责处理与通知相关的设置，包括：
 * - 获取通知开启状态
 * - 设置通知开启状态
 */
class NotificationSettingsUseCase(
    private val settingRepository: DomainSettingRepository
) {
    /**
     * 获取通知开启状态的Flow
     * @return 通知开启状态的Flow
     */
    fun getNotificationEnabled(): Flow<Boolean> {
        return settingRepository.getNotificationEnabledFlow()
    }

    /**
     * 设置通知开启状态
     * @param enabled 是否开启通知
     */
    suspend fun setNotificationEnabled(enabled: Boolean) {
        settingRepository.setNotificationEnabled(enabled)
    }
} 