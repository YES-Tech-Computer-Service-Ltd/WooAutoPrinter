package com.example.wooauto.ui.settings.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.data.api.RetrofitClient
import com.example.wooauto.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WebsiteSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)

    // Website settings
    val websiteUrl: StateFlow<String> = preferencesManager.websiteUrl
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val apiKey: StateFlow<String> = preferencesManager.apiKey
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val apiSecret: StateFlow<String> = preferencesManager.apiSecret
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val pollingInterval: StateFlow<Long> = preferencesManager.pollingInterval
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PreferencesManager.DEFAULT_POLLING_INTERVAL
        )

    private val _apiTestState = MutableStateFlow<ApiTestState>(ApiTestState.Idle)
    val apiTestState: StateFlow<ApiTestState> = _apiTestState

    /**
     * Validate website URL format
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val validUrl = if (url.endsWith("/")) url else "$url/"
            android.util.Patterns.WEB_URL.matcher(validUrl).matches()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Update website URL
     */
    fun updateWebsiteUrl(url: String) {
        viewModelScope.launch {
            if (isValidUrl(url)) {
                preferencesManager.setWebsiteUrl(url)
                _apiTestState.value = ApiTestState.Idle
            } else {
                _apiTestState.value = ApiTestState.Error("无效的网站URL格式")
            }
        }
    }

    /**
     * Update API key
     */
    fun updateApiKey(key: String) {
        viewModelScope.launch {
            // 移除严格的验证，直接保存用户输入
            preferencesManager.setApiKey(key)
            _apiTestState.value = ApiTestState.Idle
        }
    }

    /**
     * Update API secret
     */
    fun updateApiSecret(secret: String) {
        viewModelScope.launch {
            // 移除严格的验证，直接保存用户输入
            preferencesManager.setApiSecret(secret)
            _apiTestState.value = ApiTestState.Idle
        }
    }

    /**
     * Update polling interval
     */
    fun updatePollingInterval(seconds: Long) {
        viewModelScope.launch {
            // Ensure polling interval is at least 10 seconds
            val validSeconds = if (seconds < 10) 10L else seconds
            preferencesManager.setPollingInterval(validSeconds)
        }
    }

    /**
     * Test API connection
     */
    fun testApiConnection() {
        viewModelScope.launch {
            try {
                _apiTestState.value = ApiTestState.Testing

                // Get URL, API key and secret
                val url = websiteUrl.value
                val key = apiKey.value
                val secret = apiSecret.value

                // Validate input
                if (url.isBlank() || key.isBlank() || secret.isBlank()) {
                    _apiTestState.value = ApiTestState.Error("所有字段都必须填写")
                    return@launch
                }

                // Validate API key format
                if (!key.startsWith("ck_")) {
                    _apiTestState.value = ApiTestState.Error("API密钥必须以 'ck_' 开头")
                    return@launch
                }

                // Validate API secret format
                if (!secret.startsWith("cs_")) {
                    _apiTestState.value = ApiTestState.Error("API密钥必须以 'cs_' 开头")
                    return@launch
                }

                // Ensure URL ends with "/"
                val validUrl = if (url.endsWith("/")) url else "$url/"

                // Create API service
                val apiService = RetrofitClient.getWooCommerceApiService(validUrl)

                // Test products API
                val productsResponse = apiService.getProducts(
                    consumerKey = key,
                    consumerSecret = secret,
                    perPage = 1
                )

                // Test orders API
                val ordersResponse = apiService.getOrders(
                    consumerKey = key,
                    consumerSecret = secret,
                    perPage = 1
                )

                when {
                    productsResponse.isSuccessful && ordersResponse.isSuccessful -> {
                        _apiTestState.value = ApiTestState.Success
                    }
                    !productsResponse.isSuccessful && !ordersResponse.isSuccessful -> {
                        _apiTestState.value = ApiTestState.Error("API认证失败：请检查API密钥和密钥是否正确")
                    }
                    !ordersResponse.isSuccessful -> {
                        val errorMessage = when (ordersResponse.code()) {
                            401 -> "订单API访问被拒绝：请检查API密钥权限，确保包含'读取订单'权限"
                            403 -> "没有访问订单的权限：请在WooCommerce后台为该API密钥授予'读取订单'权限"
                            404 -> "无法访问订单API：请确保WooCommerce REST API已启用"
                            else -> "订单API错误：${ordersResponse.code()} - ${ordersResponse.message()}"
                        }
                        _apiTestState.value = ApiTestState.Error(errorMessage)
                    }
                    !productsResponse.isSuccessful -> {
                        val errorMessage = when (productsResponse.code()) {
                            401 -> "产品API访问被拒绝：请检查API密钥权限，确保包含'读取产品'权限"
                            403 -> "没有访问产品的权限：请在WooCommerce后台为该API密钥授予'读取产品'权限"
                            404 -> "无法访问产品API：请确保WooCommerce REST API已启用"
                            else -> "产品API错误：${productsResponse.code()} - ${productsResponse.message()}"
                        }
                        _apiTestState.value = ApiTestState.Error(errorMessage)
                    }
                }
            } catch (e: Exception) {
                Log.e("WebsiteSetupViewModel", "Error testing API connection", e)
                _apiTestState.value = ApiTestState.Error("连接错误：${e.message ?: "未知错误"}")
            }
        }
    }

    /**
     * 设置首次启动完成
     */
    fun setFirstLaunchComplete() {
        viewModelScope.launch {
            preferencesManager.setFirstLaunch(false)
        }
    }
}

// API Test State
sealed class ApiTestState {
    data object Idle : ApiTestState()
    data object Testing : ApiTestState()
    data object Success : ApiTestState()
    data class Error(val message: String) : ApiTestState()
}