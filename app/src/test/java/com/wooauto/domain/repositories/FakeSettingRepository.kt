package com.wooauto.domain.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSettingRepository : DomainSettingRepository {
    // API设置
    private val _apiUrl = MutableStateFlow("https://default.api.com")
    private val _consumerKey = MutableStateFlow("")
    private val _consumerSecret = MutableStateFlow("")

    // 通知设置
    private val _notificationEnabled = MutableStateFlow(false)

    // 打印机设置
    private val _printerType = MutableStateFlow("")
    private val _printerConnection = MutableStateFlow(false)

    // 语言和货币设置
    private val _language = MutableStateFlow("en")
    private val _currency = MutableStateFlow("USD")

    // API设置实现
    override fun getApiUrlFlow(): Flow<String> = _apiUrl.asStateFlow()
    override suspend fun setApiUrl(url: String) {
        _apiUrl.value = url
    }

    override fun getConsumerKeyFlow(): Flow<String> = _consumerKey.asStateFlow()
    override suspend fun setConsumerKey(key: String) {
        _consumerKey.value = key
    }

    override fun getConsumerSecretFlow(): Flow<String> = _consumerSecret.asStateFlow()
    override suspend fun setConsumerSecret(secret: String) {
        _consumerSecret.value = secret
    }

    // 通知设置实现
    override fun getNotificationEnabledFlow(): Flow<Boolean> = _notificationEnabled.asStateFlow()
    override suspend fun setNotificationEnabled(enabled: Boolean) {
        _notificationEnabled.value = enabled
    }

    // 打印机设置实现
    override fun getPrinterTypeFlow(): Flow<String> = _printerType.asStateFlow()
    override suspend fun setPrinterType(type: String) {
        _printerType.value = type
    }

    override fun getPrinterConnectionFlow(): Flow<Boolean> = _printerConnection.asStateFlow()
    override suspend fun setPrinterConnection(isConnected: Boolean) {
        _printerConnection.value = isConnected
    }

    // 语言设置实现
    override fun getLanguageFlow(): Flow<String> = _language.asStateFlow()
    override suspend fun setLanguage(lang: String) {
        _language.value = lang
    }

    // 货币设置实现
    override fun getCurrencyFlow(): Flow<String> = _currency.asStateFlow()
    override suspend fun setCurrency(curr: String) {
        _currency.value = curr
    }
} 