package com.wooauto.domain.repositories.fake

import com.wooauto.domain.repositories.SettingRepository_domain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 用于测试的Fake设置仓库实现
 */
class FakeSettingRepository : SettingRepository_domain {
    // API设置
    private val apiUrl = MutableStateFlow("")
    private val consumerKey = MutableStateFlow("")
    private val consumerSecret = MutableStateFlow("")

    // 通知设置
    private val notificationEnabled = MutableStateFlow(false)

    // 打印机设置
    private val printerType = MutableStateFlow("")
    private val printerConnection = MutableStateFlow(false)

    // 语言和货币设置
    private val language = MutableStateFlow("")
    private val currency = MutableStateFlow("")

    // API设置实现
    override fun getApiUrlFlow(): Flow<String> = apiUrl
    override suspend fun setApiUrl(url: String) {
        apiUrl.value = url
    }

    override fun getConsumerKeyFlow(): Flow<String> = consumerKey
    override suspend fun setConsumerKey(key: String) {
        consumerKey.value = key
    }

    override fun getConsumerSecretFlow(): Flow<String> = consumerSecret
    override suspend fun setConsumerSecret(secret: String) {
        consumerSecret.value = secret
    }

    // 通知设置实现
    override fun getNotificationEnabledFlow(): Flow<Boolean> = notificationEnabled
    override suspend fun setNotificationEnabled(enabled: Boolean) {
        notificationEnabled.value = enabled
    }

    // 打印机设置实现
    override fun getPrinterTypeFlow(): Flow<String> = printerType
    override suspend fun setPrinterType(type: String) {
        printerType.value = type
    }

    override fun getPrinterConnectionFlow(): Flow<Boolean> = printerConnection
    override suspend fun setPrinterConnection(isConnected: Boolean) {
        printerConnection.value = isConnected
    }

    // 语言设置实现
    override fun getLanguageFlow(): Flow<String> = language
    override suspend fun setLanguage(lang: String) {
        language.value = lang
    }

    // 货币设置实现
    override fun getCurrencyFlow(): Flow<String> = currency
    override suspend fun setCurrency(curr: String) {
        currency.value = curr
    }
} 