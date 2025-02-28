package com.wooauto.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wooauto.domain.repositories.DomainSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页面的状态
 */
data class SettingsUiState(
    // API 设置
    val apiUrl: String = "",
    val consumerKey: String = "",
    val consumerSecret: String = "",
    
    // 打印机设置
    val printerType: String = "USB",
    val isPrinterConnected: Boolean = false,
    
    // 通知设置
    val isNotificationEnabled: Boolean = true,
    
    // 语言设置
    val language: String = "zh",
    
    // 货币设置
    val currency: String = "CNY",
    
    // 状态
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

/**
 * 设置页面的 ViewModel
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: DomainSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    /**
     * 加载设置
     */
    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // 从仓库加载设置
                settingsRepository.getSettings().collect { settings ->
                    _uiState.update {
                        it.copy(
                            apiUrl = settings.apiUrl,
                            consumerKey = settings.consumerKey,
                            consumerSecret = settings.consumerSecret,
                            printerType = settings.printerType,
                            isPrinterConnected = settings.isPrinterConnected,
                            isNotificationEnabled = settings.isNotificationEnabled,
                            language = settings.language,
                            currency = settings.currency,
                            isLoading = false,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "加载设置失败"
                    )
                }
            }
        }
    }

    /**
     * 设置 API URL
     */
    fun setApiUrl(url: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setApiUrl(url)
                _uiState.update {
                    it.copy(
                        apiUrl = url,
                        successMessage = "API URL 已更新"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "更新 API URL 失败")
                }
            }
        }
    }

    /**
     * 设置 Consumer Key
     */
    fun setConsumerKey(key: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setConsumerKey(key)
                _uiState.update {
                    it.copy(
                        consumerKey = key,
                        successMessage = "Consumer Key 已更新"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "更新 Consumer Key 失败")
                }
            }
        }
    }

    /**
     * 设置 Consumer Secret
     */
    fun setConsumerSecret(secret: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setConsumerSecret(secret)
                _uiState.update {
                    it.copy(
                        consumerSecret = secret,
                        successMessage = "Consumer Secret 已更新"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "更新 Consumer Secret 失败")
                }
            }
        }
    }

    /**
     * 设置打印机类型
     */
    fun setPrinterType(type: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setPrinterType(type)
                _uiState.update {
                    it.copy(
                        printerType = type,
                        successMessage = "打印机类型已更新"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "更新打印机类型失败")
                }
            }
        }
    }

    /**
     * 设置通知开关
     */
    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setNotificationEnabled(enabled)
                _uiState.update {
                    it.copy(
                        isNotificationEnabled = enabled,
                        successMessage = if (enabled) "通知已开启" else "通知已关闭"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "更新通知设置失败")
                }
            }
        }
    }

    /**
     * 设置语言
     */
    fun setLanguage(language: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setLanguage(language)
                _uiState.update {
                    it.copy(
                        language = language,
                        successMessage = "语言已更新"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "更新语言失败")
                }
            }
        }
    }

    /**
     * 设置货币
     */
    fun setCurrency(currency: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setCurrency(currency)
                _uiState.update {
                    it.copy(
                        currency = currency,
                        successMessage = "货币已更新"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "更新货币失败")
                }
            }
        }
    }

    /**
     * 清除成功消息
     */
    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
} 