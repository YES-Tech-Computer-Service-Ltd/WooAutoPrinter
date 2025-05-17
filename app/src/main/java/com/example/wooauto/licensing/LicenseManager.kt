package com.example.wooauto.licensing

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 证书验证状态
 */
enum class LicenseStatus {
    /** 正在验证中 */
    VERIFYING,
    /** 验证成功，证书有效 */
    VALID,
    /** 验证失败，证书无效 */
    INVALID,
    /** 验证超时 */
    TIMEOUT,
    /** 试用有效 */
    TRIAL,
    /** 未验证 */
    UNVERIFIED
}

/**
 * 证书详细信息
 */
data class LicenseInfo(
    val status: LicenseStatus = LicenseStatus.UNVERIFIED,
    val activationDate: String = "",
    val validity: Int = 0,
    val edition: String = "",
    val capabilities: String = "",
    val licensedTo: String = "",
    val lastVerifiedTime: Long = 0,
    val message: String = ""
)

/**
 * 证书管理器单例类
 * 负责管理证书状态，提供全局访问接口
 */
@Singleton
class LicenseManager @Inject constructor() {

    private val _licenseInfo = MutableLiveData<LicenseInfo>(LicenseInfo())
    
    // 公开的LiveData，只读，提供给外部观察证书状态
    val licenseInfo: LiveData<LicenseInfo> = _licenseInfo
    
    // 证书是否有效
    val isLicenseValid: Boolean
        get() = _licenseInfo.value?.status == LicenseStatus.VALID || 
                _licenseInfo.value?.status == LicenseStatus.TRIAL
    
    /**
     * 验证证书
     * @param context 上下文
     * @param force 是否强制验证
     * @param onValidationComplete 验证完成后的回调
     */
    fun verifyLicense(
        context: Context,
        coroutineScope: CoroutineScope,
        force: Boolean = false,
        onValidationComplete: ((Boolean) -> Unit)? = null
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // 更新状态为验证中
                updateStatus(LicenseStatus.VERIFYING)
                
                val deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
                val appId = context.packageName
                
                // 检查试用状态
                val isTrialValid = checkTrialStatus(context, deviceId, appId)
                
                if (isTrialValid) {
                    Log.d("LicenseManager", "处于试用阶段，跳过许可验证")
                    updateStatus(
                        LicenseStatus.TRIAL,
                        message = "试用期有效"
                    )
                    withContext(Dispatchers.Main) {
                        onValidationComplete?.invoke(true)
                    }
                    return@launch
                }
                
                // 执行服务器验证
                val licenseKey = LicenseDataStore.getLicenseKey(context).first()
                if (licenseKey.isEmpty()) {
                    Log.e("LicenseManager", "未找到许可证密钥")
                    updateStatus(
                        LicenseStatus.INVALID,
                        message = "未找到许可证密钥"
                    )
                    withContext(Dispatchers.Main) {
                        onValidationComplete?.invoke(false)
                    }
                    return@launch
                }
                
                // 执行服务器验证
                val result = LicenseValidator.validateLicense(licenseKey, deviceId)
                
                if (result.success) {
                    // 获取证书详情
                    val details = withTimeoutOrNull(5000) {
                        LicenseValidator.getLicenseDetails(licenseKey)
                    }
                    
                    when (details) {
                        is LicenseDetailsResult.Success -> {
                            Log.d("LicenseManager", "证书详情: activationDate=${details.activationDate}, validity=${details.validity}")
                            updateStatus(
                                LicenseStatus.VALID,
                                activationDate = details.activationDate,
                                validity = details.validity,
                                edition = details.edition,
                                capabilities = details.capabilities,
                                licensedTo = details.licensedTo,
                                message = "证书有效"
                            )
                            withContext(Dispatchers.Main) {
                                onValidationComplete?.invoke(true)
                            }
                        }
                        is LicenseDetailsResult.Error -> {
                            Log.e("LicenseManager", "获取证书详情失败: ${details.message}")
                            updateStatus(
                                LicenseStatus.INVALID,
                                message = "获取证书详情失败: ${details.message}"
                            )
                            withContext(Dispatchers.Main) {
                                onValidationComplete?.invoke(false)
                            }
                        }
                        null -> {
                            Log.e("LicenseManager", "获取证书详情超时")
                            updateStatus(
                                LicenseStatus.TIMEOUT,
                                message = "获取证书详情超时"
                            )
                            withContext(Dispatchers.Main) {
                                onValidationComplete?.invoke(false)
                            }
                        }
                    }
                } else {
                    Log.e("LicenseManager", "证书验证失败: ${result.message}")
                    updateStatus(
                        LicenseStatus.INVALID,
                        message = "证书验证失败: ${result.message}"
                    )
                    withContext(Dispatchers.Main) {
                        onValidationComplete?.invoke(false)
                    }
                }
            } catch (e: Exception) {
                Log.e("LicenseManager", "证书验证异常: ${e.message}")
                updateStatus(
                    LicenseStatus.INVALID,
                    message = "证书验证异常: ${e.message}"
                )
                withContext(Dispatchers.Main) {
                    onValidationComplete?.invoke(false)
                }
            }
        }
    }
    
    /**
     * 检查是否处于试用期
     */
    private suspend fun checkTrialStatus(context: Context, deviceId: String, appId: String): Boolean {
        return try {
            withTimeoutOrNull(5000) {
                TrialTokenManager.isTrialValid(context, deviceId, appId)
            } ?: false
        } catch (e: Exception) {
            Log.e("LicenseManager", "检查试用有效性失败: ${e.message}")
            false
        }
    }
    
    /**
     * 更新证书状态
     */
    private fun updateStatus(
        status: LicenseStatus,
        activationDate: String = _licenseInfo.value?.activationDate ?: "",
        validity: Int = _licenseInfo.value?.validity ?: 0,
        edition: String = _licenseInfo.value?.edition ?: "",
        capabilities: String = _licenseInfo.value?.capabilities ?: "",
        licensedTo: String = _licenseInfo.value?.licensedTo ?: "",
        message: String = ""
    ) {
        _licenseInfo.postValue(
            LicenseInfo(
                status = status,
                activationDate = activationDate,
                validity = validity,
                edition = edition,
                capabilities = capabilities,
                licensedTo = licensedTo,
                lastVerifiedTime = System.currentTimeMillis(),
                message = message
            )
        )
    }
    
    /**
     * 获取距离上次验证的时间（分钟）
     */
    fun getTimeSinceLastVerification(): Long {
        val lastVerified = _licenseInfo.value?.lastVerifiedTime ?: 0
        if (lastVerified == 0L) return Long.MAX_VALUE
        
        return (System.currentTimeMillis() - lastVerified) / (60 * 1000)
    }
    
    /**
     * 重置证书状态为未验证
     */
    fun resetLicenseStatus() {
        _licenseInfo.postValue(LicenseInfo())
    }
    
    /**
     * 检查证书是否需要重新验证
     * @param forceThresholdMinutes 强制验证的时间阈值（分钟）
     */
    fun shouldRevalidate(forceThresholdMinutes: Long = 24 * 60): Boolean {
        // 如果未验证或无效，总是需要重新验证
        if (_licenseInfo.value?.status == LicenseStatus.UNVERIFIED ||
            _licenseInfo.value?.status == LicenseStatus.INVALID) {
            return true
        }
        
        // 如果距离上次验证时间超过阈值，需要重新验证
        return getTimeSinceLastVerification() >= forceThresholdMinutes
    }
} 