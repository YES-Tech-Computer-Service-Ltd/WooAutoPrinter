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
    
    // 统一的资格状态 - 新增
    private val _eligibilityInfo = MutableLiveData<EligibilityInfo>(EligibilityInfo())
    val eligibilityInfo: LiveData<EligibilityInfo> = _eligibilityInfo
    
    // 便捷的资格检查方法 - 新增
    val hasEligibility: Boolean
        get() = _eligibilityInfo.value?.status == EligibilityStatus.ELIGIBLE
    
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
                    // 同步试用期信息到资格状态 - 新增
                    syncTrialInfoToEligibility(context)
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
                // 首先检查本地试用期状态
                val localTrialValid = TrialTokenManager.isTrialValid(context, deviceId, appId)
                
                // 如果本地显示试用期有效，进一步验证服务器状态
                if (localTrialValid) {
                    // 服务器验证，如果服务器认为试用期已过期，将强制结束本地试用期
                    val serverVerified = TrialTokenManager.verifyTrialWithServer(context, deviceId, appId)
                    if (!serverVerified) {
                        Log.d("LicenseManager", "服务器指示试用期已过期，强制结束本地试用期")
                        TrialTokenManager.forceExpireTrial(context)
                        return@withTimeoutOrNull false
                    }
                }
                
                localTrialValid
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
        
        // 更新资格状态 - 新增
        updateEligibilityStatus()
    }
    
    /**
     * 更新统一的资格状态
     * 根据证书状态和试用期状态计算资格
     */
    private fun updateEligibilityStatus() {
        val currentLicense = _licenseInfo.value
        val eligibility = calculateEligibilityStatus(currentLicense)
        _eligibilityInfo.postValue(eligibility)
    }
    
    /**
     * 计算当前的资格状态
     */
    private fun calculateEligibilityStatus(licenseInfo: LicenseInfo?): EligibilityInfo {
        if (licenseInfo == null) {
            return EligibilityInfo(
                status = EligibilityStatus.UNKNOWN,
                displayMessage = "正在检查权限状态..."
            )
        }
        
        return when (licenseInfo.status) {
            LicenseStatus.VALID -> {
                val endDate = LicenseDataStore.calculateEndDate(licenseInfo.activationDate, licenseInfo.validity)
                EligibilityInfo(
                    status = EligibilityStatus.ELIGIBLE,
                    isLicensed = true,
                    isTrialActive = false,
                    licenseEndDate = endDate,
                    displayMessage = "证书有效 (到期: $endDate)",
                    source = EligibilitySource.LICENSE
                )
            }
            LicenseStatus.TRIAL -> {
                // 需要获取试用期剩余天数 - 这里先用默认值，后面会同步
                EligibilityInfo(
                    status = EligibilityStatus.ELIGIBLE,
                    isLicensed = false,
                    isTrialActive = true,
                    trialDaysRemaining = 0, // 将在verifyLicense中同步更新
                    displayMessage = "试用期有效",
                    source = EligibilitySource.TRIAL
                )
            }
            LicenseStatus.VERIFYING -> {
                EligibilityInfo(
                    status = EligibilityStatus.CHECKING,
                    displayMessage = "正在验证权限..."
                )
            }
            LicenseStatus.INVALID, LicenseStatus.TIMEOUT -> {
                EligibilityInfo(
                    status = EligibilityStatus.INELIGIBLE,
                    displayMessage = "权限已过期或无效"
                )
            }
            else -> {
                EligibilityInfo(
                    status = EligibilityStatus.UNKNOWN,
                    displayMessage = "权限状态未知"
                )
            }
        }
    }
    
    /**
     * 同步试用期信息到资格状态
     * 在验证试用期后调用，更新试用期剩余天数
     */
    private suspend fun syncTrialInfoToEligibility(context: Context) {
        val currentEligibility = _eligibilityInfo.value
        if (currentEligibility?.source == EligibilitySource.TRIAL) {
            try {
                val deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
                val appId = context.packageName
                val remainingDays = TrialTokenManager.getRemainingDays(context, deviceId, appId)
                
                val updatedEligibility = currentEligibility.copy(
                    trialDaysRemaining = remainingDays,
                    displayMessage = "试用期有效 (剩余: ${remainingDays}天)"
                )
                _eligibilityInfo.postValue(updatedEligibility)
            } catch (e: Exception) {
                Log.e("LicenseManager", "同步试用期信息失败: ${e.message}")
            }
        }
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
    
    /**
     * 强制重新验证并同步所有许可证状态
     * 解决多数据源状态不一致问题
     */
    suspend fun forceRevalidateAndSync(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("LicenseManager", "开始强制重新验证和同步所有状态")
                
                // 重置当前状态
                updateStatus(LicenseStatus.VERIFYING)
                
                val deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
                val appId = context.packageName
                
                // 1. 检查试用期状态
                val trialValid = TrialTokenManager.isTrialValid(context, deviceId, appId)
                val trialDays = if (trialValid) TrialTokenManager.getRemainingDays(context, deviceId, appId) else 0
                
                Log.d("LicenseManager", "试用期状态: valid=$trialValid, days=$trialDays")
                
                // 2. 检查本地许可证状态
                val isLicensedLocally = LicenseDataStore.isLicensed(context).first()
                val licenseKey = LicenseDataStore.getLicenseKey(context).first()
                
                Log.d("LicenseManager", "本地许可证状态: licensed=$isLicensedLocally, key=${licenseKey.take(8)}...")
                
                // 3. 确定最终状态
                val finalStatus = when {
                    // 如果有有效的许可证，执行服务器验证
                    isLicensedLocally && licenseKey.isNotEmpty() -> {
                        try {
                            val serverResult = LicenseValidator.validateLicense(licenseKey, deviceId)
                            if (serverResult.success) {
                                // 许可证服务器验证成功
                                val licenseDetails = LicenseValidator.getLicenseDetails(licenseKey)
                                if (licenseDetails is LicenseDetailsResult.Success) {
                                    updateStatus(
                                        LicenseStatus.VALID,
                                        activationDate = licenseDetails.activationDate,
                                        validity = licenseDetails.validity,
                                        edition = licenseDetails.edition,
                                        capabilities = licenseDetails.capabilities,
                                        licensedTo = licenseDetails.licensedTo,
                                        message = "许可证有效"
                                    )
                                    true
                                } else {
                                    updateStatus(LicenseStatus.INVALID, message = "无法获取许可证详情")
                                    false
                                }
                            } else {
                                // 许可证验证失败，检查试用期
                                if (trialValid) {
                                    updateStatus(LicenseStatus.TRIAL, message = "许可证验证失败，使用试用期")
                                    syncTrialInfoToEligibility(context)
                                    true
                                } else {
                                    updateStatus(LicenseStatus.INVALID, message = "许可证和试用期均无效")
                                    false
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("LicenseManager", "服务器验证异常: ${e.message}")
                            // 服务器验证异常，检查试用期
                            if (trialValid) {
                                updateStatus(LicenseStatus.TRIAL, message = "服务器验证异常，使用试用期")
                                syncTrialInfoToEligibility(context)
                                true
                            } else {
                                updateStatus(LicenseStatus.TIMEOUT, message = "验证超时且试用期无效")
                                false
                            }
                        }
                    }
                    // 如果没有许可证但有试用期
                    trialValid -> {
                        updateStatus(LicenseStatus.TRIAL, message = "试用期有效")
                        syncTrialInfoToEligibility(context)
                        true
                    }
                    // 都没有
                    else -> {
                        updateStatus(LicenseStatus.INVALID, message = "无有效许可证或试用期")
                        false
                    }
                }
                
                Log.d("LicenseManager", "强制重新验证完成: 最终状态=$finalStatus")
                return@withContext finalStatus
                
            } catch (e: Exception) {
                Log.e("LicenseManager", "强制重新验证失败: ${e.message}", e)
                updateStatus(LicenseStatus.INVALID, message = "验证过程异常: ${e.message}")
                return@withContext false
            }
        }
    }
} 