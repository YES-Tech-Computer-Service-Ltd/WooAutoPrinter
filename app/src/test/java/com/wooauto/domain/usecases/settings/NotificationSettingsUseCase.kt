package com.wooauto.domain.usecases.settings

import com.example.wooauto.domain.repositories.DomainSettingRepository
import kotlinx.coroutines.flow.Flow

/**
 * 通知设置相关用例
 */
class NotificationSettingsUseCase(private val repository: DomainSettingRepository) {
    
    /**
     * 获取通知启用状态
     */
    fun getNotificationEnabled(): Flow<Boolean> {
        return repository.getNotificationEnabledFlow()
    }
    
    /**
     * 设置通知启用状态
     */
    suspend fun setNotificationEnabled(enabled: Boolean) {
        repository.setNotificationEnabled(enabled)
    }
    
    /**
     * 切换通知启用状态
     */
    suspend fun toggleNotificationEnabled() {
        val currentValue = repository.getNotificationEnabledFlow().toString().toBoolean()
        repository.setNotificationEnabled(!currentValue)
    }
} 