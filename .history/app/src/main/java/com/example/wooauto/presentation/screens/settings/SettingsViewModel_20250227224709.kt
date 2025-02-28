package com.example.wooauto.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.data.local.WooCommerceConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 系统设置ViewModel
 * 用于管理设置界面的状态和操作
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val wooCommerceConfig: WooCommerceConfig
) : ViewModel() {

    // 网站连接设置状态
    private val _siteUrl = MutableStateFlow("")
    val siteUrl: StateFlow<String> = _siteUrl.asStateFlow()

    private val _consumerKey = MutableStateFlow("")
    val consumerKey: StateFlow<String> = _consumerKey.asStateFlow()

    private val _consumerSecret = MutableStateFlow("")
    val consumerSecret: StateFlow<String> = _consumerSecret.asStateFlow()

    private val _pollingInterval = MutableStateFlow(30)
    val pollingInterval: StateFlow<Int> = _pollingInterval.asStateFlow()

    private val _useWooCommerceFood = MutableStateFlow(false)
    val useWooCommerceFood: StateFlow<Boolean> = _useWooCommerceFood.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<ConnectionTestResult?>(null)
    val connectionTestResult: StateFlow<ConnectionTestResult?> = _connectionTestResult.asStateFlow()

    // 初始化
    init {
        loadConfig()
    }

    // 加载配置
    private fun loadConfig() {
        viewModelScope.launch {
            wooCommerceConfig.siteUrl.collectLatest { _siteUrl.value = it }
        }
        viewModelScope.launch {
            wooCommerceConfig.consumerKey.collectLatest { _consumerKey.value = it }
        }
        viewModelScope.launch {
            wooCommerceConfig.consumerSecret.collectLatest { _consumerSecret.value = it }
        }
        viewModelScope.launch {
            wooCommerceConfig.pollingInterval.collectLatest { _pollingInterval.value = it }
        }
        viewModelScope.launch {
            wooCommerceConfig.useWooCommerceFood.collectLatest { _useWooCommerceFood.value = it }
        }
    }

    // 更新站点URL
    fun updateSiteUrl(url: String) {
        viewModelScope.launch {
            wooCommerceConfig.updateSiteUrl(url)
            _siteUrl.value = url
        }
    }

    // 更新Consumer Key
    fun updateConsumerKey(key: String) {
        viewModelScope.launch {
            wooCommerceConfig.updateConsumerKey(key)
            _consumerKey.value = key
        }
    }

    // 更新Consumer Secret
    fun updateConsumerSecret(secret: String) {
        viewModelScope.launch {
            wooCommerceConfig.updateConsumerSecret(secret)
            _consumerSecret.value = secret
        }
    }

    // 更新轮询间隔
    fun updatePollingInterval(interval: Int) {
        viewModelScope.launch {
            wooCommerceConfig.updatePollingInterval(interval)
            _pollingInterval.value = interval
        }
    }

    // 更新是否使用WooCommerce Food插件
    fun updateUseWooCommerceFood(use: Boolean) {
        viewModelScope.launch {
            wooCommerceConfig.updateUseWooCommerceFood(use)
            _useWooCommerceFood.value = use
        }
    }

    // 测试连接
    fun testConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = null
            
            try {
                // 这里可以调用Repository来测试连接
                // 暂时模拟成功
                _connectionTestResult.value = ConnectionTestResult.Success
            } catch (e: Exception) {
                _connectionTestResult.value =
                    ConnectionTestResult.Error(e.message ?: "Unknown error")
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    // 清除连接测试结果
    fun clearConnectionTestResult() {
        _connectionTestResult.value = null
    }

    // 连接测试结果密封类
    sealed class ConnectionTestResult {
        object Success : ConnectionTestResult()
        data class Error(val message: String) : ConnectionTestResult()
    }
} 