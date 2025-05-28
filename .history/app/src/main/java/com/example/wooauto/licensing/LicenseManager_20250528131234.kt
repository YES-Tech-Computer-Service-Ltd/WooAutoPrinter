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

    // 初始化为默认允许状态，给用户更好的体验
    private val _licenseInfo = MutableLiveData<LicenseInfo>(
        LicenseInfo(
            status = LicenseStatus.TRIAL, // 默认状态为试用期
            message = "默认试用期，正在后台验证..."
        )
    )
    
    // 公开的LiveData，只读，提供给外部观察证书状态
    val licenseInfo: LiveData<LicenseInfo> = _licenseInfo
    
    // 证书是否有效 - 修改逻辑，默认为true
    val isLicenseValid: Boolean
        get() = _licenseInfo.value?.status != LicenseStatus.INVALID
    
    // 统一的资格状态 - 初始化为允许状态
    private val _eligibilityInfo = MutableLiveData<EligibilityInfo>(
        EligibilityInfo(
            status = EligibilityStatus.ELIGIBLE,
            isTrialActive = true,
            trialDaysRemaining = 10,
            displayMessage = "默认试用期有效，正在后台验证...",
            source = EligibilitySource.TRIAL
        )
    )
    val eligibilityInfo: LiveData<EligibilityInfo> = _eligibilityInfo
    
    // 便捷的资格检查方法 - 修改逻辑，只有明确INELIGIBLE才拒绝
    val hasEligibility: Boolean
        get() = _eligibilityInfo.value?.status != EligibilityStatus.INELIGIBLE
    
    /**
     * 非阻塞式验证证书
     * 在后台验证，不影响用户正常使用
     * 只有在验证明确失败时才会锁定功能
     * 
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
                // 更新状态为验证中，但仍保持可用
                updateStatus(
                    LicenseStatus.VERIFYING,
                    message = "后台验证中，功能可正常使用"
                )
                updateEligibilityToChecking()
                
                val deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
                val appId = context.packageName
                
                // 首先检查是否有有效的许可证
                val licenseKey = LicenseDataStore.getLicenseKey(context).first()
                val isLicensedLocally = LicenseDataStore.isLicensed(context).first()
                
                if (isLicensedLocally && licenseKey.isNotEmpty()) {
                    // 有许可证，验证许可证
                    val validationResult = validateLicenseInBackground(licenseKey, deviceId)
                    if (validationResult) {
                        // 许可证验证成功
                        withContext(Dispatchers.Main) {
                            onValidationComplete?.invoke(true)
                        }
                        return@launch
                    } else {
                        Log.w("LicenseManager", "许可证验证失败，检查试用期")
                    }
                }
                
                // 检查试用期状态（许可证无效或不存在时）
                val trialValid = checkTrialStatusSafely(context, deviceId, appId)
                
                if (trialValid) {
                    updateStatus(
                        LicenseStatus.TRIAL,
                        message = "试用期有效"
                    )
                    syncTrialInfoToEligibility(context)
                    withContext(Dispatchers.Main) {
                        onValidationComplete?.invoke(true)
                    }
                } else {
                    // 只有在试用期也无效时才锁定
                    Log.w("LicenseManager", "许可证和试用期都无效，锁定功能")
                    updateStatus(
                        LicenseStatus.INVALID,
                        message = "许可证和试用期均已过期，请激活许可证"
                    )
                    updateEligibilityToIneligible()
                    withContext(Dispatchers.Main) {
                        onValidationComplete?.invoke(false)
                    }
                }
                
            } catch (e: Exception) {
                // 验证异常时，保持当前可用状态，不锁定用户
                updateStatus(
                    LicenseStatus.TRIAL,
                    message = "验证异常，默认允许使用: ${e.message}"
                )
                withContext(Dispatchers.Main) {
                    onValidationComplete?.invoke(true)
                }
            }
        }
    }
    
    /**
     * 安全地检查试用期状态，出错时不影响用户使用
     */
    private suspend fun checkTrialStatusSafely(context: Context, deviceId: String, appId: String): Boolean {
        return try {
            val result = withTimeoutOrNull(3000) {
                val trialResult = TrialTokenManager.isTrialValid(context, deviceId, appId)
                trialResult
            }
            
            if (result != null) {
                result
            } else {
                true // 超时时默认允许使用
            }
        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.e("LicenseManager", "❌ [${System.currentTimeMillis()}] 检查试用期状态失败，默认允许使用, 总耗时: ${totalDuration}ms - ${e.message}")
            true // 异常时默认允许使用
        }
    }
    
    /**
     * 在后台验证许可证，不阻塞用户使用
     */
    private suspend fun validateLicenseInBackground(licenseKey: String, deviceId: String): Boolean {
        return try {
            val result = withTimeoutOrNull(5000) {
                val validationResult = LicenseValidator.validateLicense(licenseKey, deviceId)
                
                if (validationResult.success) {
                    // 获取许可证详情
                    val details = LicenseValidator.getLicenseDetails(licenseKey)
                    
                    if (details is LicenseDetailsResult.Success) {
                        updateStatus(
                            LicenseStatus.VALID,
                            activationDate = details.activationDate,
                            validity = details.validity,
                            edition = details.edition,
                            capabilities = details.capabilities,
                            licensedTo = details.licensedTo,
                            message = "许可证有效"
                        )
                        
                        true
                    } else {
                        Log.w("LicenseManager", "⚠️ [${System.currentTimeMillis()}] 无法获取许可证详情")
                        false
                    }
                } else {
                    false
                }
            }
            
            if (result != null) {
                result
            } else {
                Log.w("LicenseManager", "⏰ [${System.currentTimeMillis()}] 许可证验证超时, 总耗时: ${totalDuration}ms")
                false // 超时返回false
            }
        } catch (e: Exception) {

            Log.e("LicenseManager", "❌ [${System.currentTimeMillis()}] 许可证验证异常, 总耗时: ${totalDuration}ms - ${e.message}")
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
        val newLicenseInfo = LicenseInfo(
            status = status,
            activationDate = activationDate,
            validity = validity,
            edition = edition,
            capabilities = capabilities,
            licensedTo = licensedTo,
            lastVerifiedTime = System.currentTimeMillis(),
            message = message
        )
        
        // 先更新LicenseInfo
        _licenseInfo.postValue(newLicenseInfo)
        
        // 基于新的LicenseInfo计算资格状态，确保状态同步
        val eligibility = calculateEligibilityStatus(newLicenseInfo)
        _eligibilityInfo.postValue(eligibility)
    }
    
    /**
     * 计算当前的资格状态
     * 新策略：默认允许使用，只有明确验证失败才拒绝
     */
    private fun calculateEligibilityStatus(licenseInfo: LicenseInfo?): EligibilityInfo {
        if (licenseInfo == null) {
            return EligibilityInfo(
                status = EligibilityStatus.ELIGIBLE,
                isTrialActive = true,
                trialDaysRemaining = 10,
                displayMessage = "默认试用期有效",
                source = EligibilitySource.TRIAL
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
                    displayMessage = "许可证有效 (到期: $endDate)",
                    source = EligibilitySource.LICENSE
                )
            }
            LicenseStatus.TRIAL -> {
                EligibilityInfo(
                    status = EligibilityStatus.ELIGIBLE,
                    isLicensed = false,
                    isTrialActive = true,
                    trialDaysRemaining = 10, // 将在syncTrialInfoToEligibility中更新实际天数
                    displayMessage = "试用期有效",
                    source = EligibilitySource.TRIAL
                )
            }
            LicenseStatus.VERIFYING -> {
                EligibilityInfo(
                    status = EligibilityStatus.CHECKING,
                    isLicensed = false,
                    isTrialActive = true,
                    trialDaysRemaining = 10,
                    displayMessage = "正在验证权限，功能可正常使用",
                    source = EligibilitySource.TRIAL
                )
            }
            // 只有明确的INVALID状态才设置为INELIGIBLE
            LicenseStatus.INVALID -> {
                EligibilityInfo(
                    status = EligibilityStatus.INELIGIBLE,
                    isLicensed = false,
                    isTrialActive = false,
                    trialDaysRemaining = 0,
                    displayMessage = "许可证和试用期均已过期",
                    source = EligibilitySource.UNKNOWN
                )
            }
            // 超时等其他状态默认允许使用
            else -> {
                EligibilityInfo(
                    status = EligibilityStatus.ELIGIBLE,
                    isLicensed = false,
                    isTrialActive = true,
                    trialDaysRemaining = 10,
                    displayMessage = "默认试用期有效 (${licenseInfo.status})",
                    source = EligibilitySource.TRIAL
                )
            }
        }
    }
    
    /**
     * 同步试用期信息到资格状态
     * 在验证试用期后调用，更新试用期剩余天数
     */
    private suspend fun syncTrialInfoToEligibility(context: Context) {
        try {
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            val appId = context.packageName
            val remainingDays = TrialTokenManager.getRemainingDays(context, deviceId, appId)
            
            // 强制更新为试用期状态，不依赖当前的source
            val updatedEligibility = EligibilityInfo(
                status = EligibilityStatus.ELIGIBLE,
                isLicensed = false,
                isTrialActive = true,
                trialDaysRemaining = remainingDays,
                displayMessage = "试用期有效 (剩余: ${remainingDays}天)",
                source = EligibilitySource.TRIAL
            )
            _eligibilityInfo.postValue(updatedEligibility)
            
        } catch (e: Exception) {
            Log.e("LicenseManager", "❌ [${System.currentTimeMillis()}] 同步试用期信息失败: ${e.message}")
            
            // 即使同步失败，也要设置一个合理的默认状态
            val defaultEligibility = EligibilityInfo(
                status = EligibilityStatus.ELIGIBLE,
                isLicensed = false,
                isTrialActive = true,
                trialDaysRemaining = 10,
                displayMessage = "试用期有效 (默认状态)",
                source = EligibilitySource.TRIAL
            )
            _eligibilityInfo.postValue(defaultEligibility)
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
     * 新策略：默认允许使用，后台验证，只有明确失败才锁定
     */
    suspend fun forceRevalidateAndSync(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 设置为检查状态，但保持可用
                updateStatus(LicenseStatus.VERIFYING, message = "强制验证中，功能可正常使用")
                updateEligibilityToChecking()
                
                val deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
                val appId = context.packageName
                
                // 1. 检查本地许可证状态
                val isLicensedLocally = LicenseDataStore.isLicensed(context).first()
                val licenseKey = LicenseDataStore.getLicenseKey(context).first()
                
                // 2. 如果有许可证，验证许可证
                if (isLicensedLocally && licenseKey.isNotEmpty()) {
                    val licenseValid = validateLicenseInBackground(licenseKey, deviceId)
                    
                    if (licenseValid) {
                        return@withContext true
                    }
                }
                
                // 3. 检查试用期状态
                val trialValid = checkTrialStatusSafely(context, deviceId, appId)
                
                val trialDays = if (trialValid) {
                    try {
                        val days = TrialTokenManager.getRemainingDays(context, deviceId, appId)
                        days
                    } catch (e: Exception) {
                        10 // 默认给10天
                    }
                } else 0
                
                // 4. 根据结果设置最终状态
                if (trialValid && trialDays > 0) {
                    // 试用期有效
                    updateStatus(LicenseStatus.TRIAL, message = "试用期有效")
                    syncTrialInfoToEligibility(context)
                    return@withContext true
                } else {
                    // 只有在试用期明确无效且天数为0时才锁定
                    if (!trialValid && trialDays <= 0) {
                        updateStatus(LicenseStatus.INVALID, message = "无有效许可证或试用期")
                        updateEligibilityToIneligible()

                        Log.w("LicenseManager", "🔒 [${System.currentTimeMillis()}] 许可证和试用期都明确无效，锁定功能，状态更新耗时: ${finalStatusDuration}ms，总耗时: ${totalDuration}ms")
                        return@withContext false
                    } else {
                        // 其他情况默认允许使用
                        updateStatus(LicenseStatus.TRIAL, message = "默认试用期有效")
                        return@withContext true
                    }
                }
                
            } catch (e: Exception) {

                Log.e("LicenseManager", "❌ [${System.currentTimeMillis()}] 强制重新验证失败，但默认允许使用，总耗时: ${totalDuration}ms - ${e.message}", e)
                updateStatus(LicenseStatus.TRIAL, message = "验证异常，默认允许使用: ${e.message}")
                return@withContext true
            }
        }
    }
    
    /**
     * 更新资格状态为检查中（但仍可用）
     */
    private fun updateEligibilityToChecking() {
        val current = _eligibilityInfo.value ?: EligibilityInfo()
        _eligibilityInfo.postValue(
            current.copy(
                status = EligibilityStatus.CHECKING,
                displayMessage = "正在后台验证权限，功能可正常使用"
            )
        )
    }
    
    /**
     * 更新资格状态为不可用（只有明确验证失败时调用）
     */
    private fun updateEligibilityToIneligible() {
        _eligibilityInfo.postValue(
            EligibilityInfo(
                status = EligibilityStatus.INELIGIBLE,
                isLicensed = false,
                isTrialActive = false,
                trialDaysRemaining = 0,
                displayMessage = "许可证和试用期均已过期，请激活许可证",
                source = EligibilitySource.UNKNOWN
            )
        )
    }

    /**
     * 检查是否处于试用期（保留原方法，但调整为安全模式）
     */
    private suspend fun checkTrialStatus(context: Context, deviceId: String, appId: String): Boolean {
        return checkTrialStatusSafely(context, deviceId, appId)
    }
} 