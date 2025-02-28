package com.wooauto.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wooauto.domain.repositories.DomainSettingRepository
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
    val printerType: String = "",
    val isPrinterConnected: Boolean = false,
    
    // 通知设置
    val isNotificationEnabled: Boolean = false,
    
    // 语言设置
    val language: String = "en",
    
    // 货币设置
    val currency: String = "CNY",
    
    // 状态
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * 设置页面的 ViewModel
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingRepository: DomainSettingRepository
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
            try {
                combine(
                    settingRepository.getApiUrlFlow(),
                    settingRepository.getConsumerKeyFlow(),
                    settingRepository.getConsumerSecretFlow(),
                    settingRepository.getPrinterTypeFlow(),
                    settingRepository.getPrinterConnectionFlow(),
                    settingRepository.getNotificationEnabledFlow(),
                    settingRepository.getLanguageFlow(),
                    settingRepository.getCurrencyFlow()
                ) { apiUrl: String, consumerKey: String, consumerSecret: String, 
                    printerType: String, isPrinterConnected: Boolean, 
                    isNotificationEnabled: Boolean, language: String, 
                    currency: String ->
                    SettingsUiState(
                        apiUrl = apiUrl,
                        consumerKey = consumerKey,
                        consumerSecret = consumerSecret,
                        printerType = printerType,
                        isPrinterConnected = isPrinterConnected,
                        isNotificationEnabled = isNotificationEnabled,
                        language = language,
                        currency = currency
                    )
                }.catch { e ->
                    _uiState.update { it.copy(error = e.message) }
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * 设置 API URL
     */
    fun updateApiUrl(url: String) {
        viewModelScope.launch {
            settingRepository.setApiUrl(url)
        }
    }

    /**
     * 设置 Consumer Key
     */
    fun updateConsumerKey(key: String) {
        viewModelScope.launch {
            settingRepository.setConsumerKey(key)
        }
    }

    /**
     * 设置 Consumer Secret
     */
    fun updateConsumerSecret(secret: String) {
        viewModelScope.launch {
            settingRepository.setConsumerSecret(secret)
        }
    }

    /**
     * 设置打印机类型
     */
    fun updatePrinterType(type: String) {
        viewModelScope.launch {
            settingRepository.setPrinterType(type)
        }
    }

    /**
     * 设置打印机连接状态
     */
    fun updatePrinterConnection(isConnected: Boolean) {
        viewModelScope.launch {
            settingRepository.setPrinterConnection(isConnected)
        }
    }

    /**
     * 设置通知开关
     */
    fun updateNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingRepository.setNotificationEnabled(enabled)
        }
    }

    /**
     * 设置语言
     */
    fun updateLanguage(language: String) {
        viewModelScope.launch {
            settingRepository.setLanguage(language)
        }
    }

    /**
     * 设置货币
     */
    fun updateCurrency(currency: String) {
        viewModelScope.launch {
            settingRepository.setCurrency(currency)
        }
    }
} 