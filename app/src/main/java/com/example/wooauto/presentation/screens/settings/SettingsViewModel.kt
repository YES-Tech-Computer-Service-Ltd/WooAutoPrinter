package com.example.wooauto.presentation.screens.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.repositories.DomainProductRepository
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.utils.LocaleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * 系统设置ViewModel
 * 用于管理设置界面的状态和操作
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: DomainSettingRepository,
    private val productRepository: DomainProductRepository,
    private val orderRepository: DomainOrderRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // API 配置相关状态
    private val _siteUrl = MutableStateFlow("")
    val siteUrl: StateFlow<String> = _siteUrl.asStateFlow()

    private val _consumerKey = MutableStateFlow("")
    val consumerKey: StateFlow<String> = _consumerKey.asStateFlow()

    private val _consumerSecret = MutableStateFlow("")
    val consumerSecret: StateFlow<String> = _consumerSecret.asStateFlow()

    private val _pollingInterval = MutableStateFlow(30) // 默认30秒
    val pollingInterval: StateFlow<Int> = _pollingInterval.asStateFlow()

    private val _useWooCommerceFood = MutableStateFlow(false)
    val useWooCommerceFood: StateFlow<Boolean> = _useWooCommerceFood.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<ConnectionTestResult?>(null)
    val connectionTestResult: StateFlow<ConnectionTestResult?> = _connectionTestResult.asStateFlow()

    // 语言设置相关状态
    private val _currentLocale = MutableStateFlow(LocaleHelper.getSelectedLocale(context))
    val currentLocale: StateFlow<Locale> = _currentLocale.asStateFlow()

    init {
        Log.d("SettingsViewModel", "初始化ViewModel")
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val config = settingsRepository.getWooCommerceConfig()
                _siteUrl.value = config.siteUrl
                _consumerKey.value = config.consumerKey
                _consumerSecret.value = config.consumerSecret
                _pollingInterval.value = config.pollingInterval
                _useWooCommerceFood.value = config.useWooCommerceFood
                
                Log.d("SettingsViewModel", "成功加载设置: $config")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "加载设置失败", e)
            }
        }
    }

    fun updateSiteUrl(url: String) {
        _siteUrl.value = url
        saveSettings()
    }

    fun updateConsumerKey(key: String) {
        _consumerKey.value = key
        saveSettings()
    }

    fun updateConsumerSecret(secret: String) {
        _consumerSecret.value = secret
        saveSettings()
    }

    fun updatePollingInterval(interval: Int) {
        _pollingInterval.value = interval
        saveSettings()
    }

    fun updateUseWooCommerceFood(use: Boolean) {
        _useWooCommerceFood.value = use
        saveSettings()
    }

    private fun saveSettings() {
        viewModelScope.launch {
            try {
                val config = WooCommerceConfig(
                    siteUrl = _siteUrl.value,
                    consumerKey = _consumerKey.value,
                    consumerSecret = _consumerSecret.value,
                    pollingInterval = _pollingInterval.value,
                    useWooCommerceFood = _useWooCommerceFood.value
                )
                settingsRepository.saveWooCommerceConfig(config)
                Log.d("SettingsViewModel", "成功保存设置: $config")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "保存设置失败", e)
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            try {
                _isTestingConnection.value = true
                
                // 创建临时配置
                val config = WooCommerceConfig(
                    siteUrl = _siteUrl.value,
                    consumerKey = _consumerKey.value,
                    consumerSecret = _consumerSecret.value,
                    pollingInterval = _pollingInterval.value,
                    useWooCommerceFood = _useWooCommerceFood.value
                )
                
                // 尝试获取产品数据以测试连接
                val testResult = productRepository.testConnection(config)
                
                if (testResult) {
                    _connectionTestResult.value = ConnectionTestResult.Success
                    Log.d("SettingsViewModel", "API连接测试成功")
                } else {
                    _connectionTestResult.value = ConnectionTestResult.Error("未知错误")
                    Log.e("SettingsViewModel", "API连接测试失败: 未知错误")
                }
            } catch (e: Exception) {
                _connectionTestResult.value = ConnectionTestResult.Error(e.message ?: "未知错误")
                Log.e("SettingsViewModel", "API连接测试失败", e)
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    fun clearConnectionTestResult() {
        _connectionTestResult.value = null
    }

    fun setAppLanguage(locale: Locale) {
        LocaleHelper.setLocale(context, locale)
        _currentLocale.value = locale
        Log.d("SettingsViewModel", "应用语言已更改为: ${locale.language}")
    }

    // 连接测试结果密封类
    sealed class ConnectionTestResult {
        object Success : ConnectionTestResult()
        data class Error(val message: String) : ConnectionTestResult()
    }
} 