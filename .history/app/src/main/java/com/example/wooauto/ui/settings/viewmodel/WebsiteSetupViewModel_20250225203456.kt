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

    val pollingInterval: StateFlow<Int> = preferencesManager.pollingInterval
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
     * Validate API credentials
     */
    private fun isValidApiCredentials(key: String, secret: String): Boolean {
        // API key should start with 'ck_' and secret with 'cs_'
        return key.startsWith("ck_") && secret.startsWith("cs_")
    }

    /**
     * Validate polling interval
     */
    private fun isValidPollingInterval(seconds: Int): Boolean {
        return seconds >= 10 // 最小轮询间隔为10秒
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
            if (key.isEmpty() || isValidApiCredentials(key, apiSecret.value)) {
                preferencesManager.setApiKey(key)
                _apiTestState.value = ApiTestState.Idle
            } else {
                _apiTestState.value = ApiTestState.Error("无效的API密钥格式")
            }
        }
    }

    /**
     * Update API secret
     */
    fun updateApiSecret(secret: String) {
        viewModelScope.launch {
            if (secret.isEmpty() || isValidApiCredentials(apiKey.value, secret)) {
                preferencesManager.setApiSecret(secret)
                _apiTestState.value = ApiTestState.Idle
            } else {
                _apiTestState.value = ApiTestState.Error("无效的API密钥格式")
            }
        }
    }

    /**
     * Update polling interval
     */
    fun updatePollingInterval(seconds: Int) {
        viewModelScope.launch {
            // Ensure polling interval is at least 10 seconds
            val validSeconds = if (seconds < 10) 10 else seconds
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
                    _apiTestState.value = ApiTestState.Error("All fields are required")
                    return@launch
                }

                // Ensure URL ends with "/"
                val validUrl = if (url.endsWith("/")) url else "$url/"

                // Create API service
                val apiService = RetrofitClient.getWooCommerceApiService(validUrl)

                // Test connection by retrieving a small number of products
                val response = apiService.getProducts(
                    consumerKey = key,
                    consumerSecret = secret,
                    perPage = 1
                )

                // Check if response is successful
                if (response.isSuccessful) {
                    _apiTestState.value = ApiTestState.Success
                } else {
                    _apiTestState.value =
                        ApiTestState.Error("API Error: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("WebsiteSetupViewModel", "Error testing API connection", e)
                _apiTestState.value = ApiTestState.Error(e.message ?: "Unknown error")
            }
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