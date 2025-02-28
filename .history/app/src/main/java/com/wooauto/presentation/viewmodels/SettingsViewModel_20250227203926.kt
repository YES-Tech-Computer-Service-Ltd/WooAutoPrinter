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
                settingRepository.getApiUrlFlow().combine(
                    settingRepository.getConsumerKeyFlow()
                ) { apiUrl, consumerKey -> Pair(apiUrl, consumerKey) }
                .combine(settingRepository.getConsumerSecretFlow()) { (apiUrl, consumerKey), consumerSecret ->
                    Triple(apiUrl, consumerKey, consumerSecret)
                }
                .combine(settingRepository.getPrinterTypeFlow()) { (apiUrl, consumerKey, consumerSecret), printerType ->
                    arrayOf(apiUrl, consumerKey, consumerSecret, printerType)
                }
                .combine(settingRepository.getPrinterConnectionFlow()) { arr, isPrinterConnected ->
                    arr + isPrinterConnected
                }
                .combine(settingRepository.getNotificationEnabledFlow()) { arr, isNotificationEnabled ->
                    arr + isNotificationEnabled
                }
                .combine(settingRepository.getLanguageFlow()) { arr, language ->
                    arr + language
                }
                .combine(settingRepository.getCurrencyFlow()) { arr, currency ->
                    SettingsUiState(
                        apiUrl = arr[0] as String,
                        consumerKey = arr[1] as String,
                        consumerSecret = arr[2] as String,
                        printerType = arr[3] as String,
                        isPrinterConnected = arr[4] as Boolean,
                        isNotificationEnabled = arr[5] as Boolean,
                        language = arr[6] as String,
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