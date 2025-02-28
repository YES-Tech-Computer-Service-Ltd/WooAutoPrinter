package com.example.wooauto.data.repository

import com.example.wooauto.data.mappers.SettingMapper
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.data.local.dao.SettingDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingRepositoryImpl @Inject constructor(
    private val settingDao: SettingDao
) : DomainSettingRepository {

    // 设置键名常量
    companion object {
        const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        const val KEY_API_URL = "api_url"
        const val KEY_CONSUMER_KEY = "consumer_key"
        const val KEY_CONSUMER_SECRET = "consumer_secret"
        const val KEY_PRINTER_TYPE = "printer_type"
        const val KEY_PRINTER_CONNECTION = "printer_connection"
        const val KEY_LANGUAGE = "language"
        const val KEY_CURRENCY = "currency"

        // 默认值
        const val DEFAULT_API_URL = "https://example.com/wp-json/wc/v3/"
        const val DEFAULT_LANGUAGE = "en"
        const val DEFAULT_CURRENCY = "USD"
    }

    override fun getNotificationEnabledFlow(): Flow<Boolean> {
        return getBooleanSettingFlow(KEY_NOTIFICATION_ENABLED, false)
    }

    override suspend fun setNotificationEnabled(enabled: Boolean) {
        val entity = SettingMapper.createBooleanSettingEntity(
            KEY_NOTIFICATION_ENABLED,
            enabled,
            "启用订单通知提醒"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    override fun getApiUrlFlow(): Flow<String> {
        return getStringSettingFlow(KEY_API_URL, DEFAULT_API_URL)
    }

    override suspend fun setApiUrl(url: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_API_URL,
            url,
            "WooCommerce API URL"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    override fun getConsumerKeyFlow(): Flow<String> {
        return getStringSettingFlow(KEY_CONSUMER_KEY, "")
    }

    override suspend fun setConsumerKey(consumerKey: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_CONSUMER_KEY,
            consumerKey,
            "WooCommerce Consumer Key"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    override fun getConsumerSecretFlow(): Flow<String> {
        return getStringSettingFlow(KEY_CONSUMER_SECRET, "")
    }

    override suspend fun setConsumerSecret(consumerSecret: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_CONSUMER_SECRET,
            consumerSecret,
            "WooCommerce Consumer Secret"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    override fun getPrinterTypeFlow(): Flow<String> {
        return getStringSettingFlow(KEY_PRINTER_TYPE, "")
    }

    override suspend fun setPrinterType(type: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_PRINTER_TYPE,
            type,
            "打印机类型"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    override fun getPrinterConnectionFlow(): Flow<Boolean> {
        return getBooleanSettingFlow(KEY_PRINTER_CONNECTION, false)
    }

    override suspend fun setPrinterConnection(isConnected: Boolean) {
        val entity = SettingMapper.createBooleanSettingEntity(
            KEY_PRINTER_CONNECTION,
            isConnected,
            "打印机连接状态"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    override fun getLanguageFlow(): Flow<String> {
        return getStringSettingFlow(KEY_LANGUAGE, DEFAULT_LANGUAGE)
    }

    override suspend fun setLanguage(language: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_LANGUAGE,
            language,
            "应用语言"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    override fun getCurrencyFlow(): Flow<String> {
        return getStringSettingFlow(KEY_CURRENCY, DEFAULT_CURRENCY)
    }

    override suspend fun setCurrency(currency: String) {
        val entity = SettingMapper.createStringSettingEntity(
            KEY_CURRENCY,
            currency,
            "显示货币"
        )
        settingDao.insertOrUpdateSetting(entity)
    }

    // 辅助方法

    private fun getStringSettingFlow(key: String, defaultValue: String): Flow<String> {
        return flow {
            // 在flow构建器内部可以调用挂起函数
            val entity = settingDao.getSettingByKey(key)
            emit(entity?.value ?: defaultValue)
        }
    }

    private fun getBooleanSettingFlow(key: String, defaultValue: Boolean): Flow<Boolean> {
        return flow {
            val entity = settingDao.getSettingByKey(key)
            emit(entity?.value?.toBoolean() ?: defaultValue)
        }
    }
}