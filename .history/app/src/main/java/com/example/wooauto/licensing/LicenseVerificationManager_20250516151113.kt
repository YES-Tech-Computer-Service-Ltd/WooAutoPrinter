package com.example.wooauto.licensing

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * @deprecated 使用 {@link LicenseManager} 替代
 * 为兼容旧代码保留此类，内部转发到 LicenseManager
 */
@Deprecated("使用 LicenseManager 替代，此类将在未来版本中移除")
object LicenseVerificationManager {
    
    private var licenseManager: LicenseManager? = null
    
    /**
     * 在应用启动时初始化，传入LicenseManager实例
     */
    fun initialize(manager: LicenseManager) {
        licenseManager = manager
        Log.d("LicenseVerificationManager", "初始化完成，使用LicenseManager")
    }
    
    fun verifyLicenseOnStart(
        context: Context,
        coroutineScope: CoroutineScope,
        onInvalid: suspend () -> Unit,
        onSuccess: suspend () -> Unit = {}
    ) {
        Log.d("LicenseVerificationManager", "verifyLicenseOnStart - 已重定向到LicenseManager")
        
        // 直接使用LicenseManager进行验证
        licenseManager?.let { manager ->
            manager.verifyLicense(context, coroutineScope) { isValid ->
                coroutineScope.launch(Dispatchers.IO) {
                    if (isValid) {
                        onSuccess()
                    } else {
                        onInvalid()
                    }
                }
            }
        } ?: run {
            // 未初始化时，使用旧的验证方法
            legacyVerifyLicense(context, coroutineScope, onInvalid, onSuccess)
        }
    }

    fun forceServerValidation(
        context: Context,
        coroutineScope: CoroutineScope,
        onInvalid: suspend () -> Unit,
        onSuccess: suspend () -> Unit = {}
    ) {
        Log.d("LicenseVerificationManager", "forceServerValidation - 已重定向到LicenseManager")
        
        // 使用LicenseManager强制验证
        licenseManager?.let { manager ->
            manager.verifyLicense(context, coroutineScope, force = true) { isValid ->
                coroutineScope.launch(Dispatchers.IO) {
                    if (isValid) {
                        onSuccess()
                    } else {
                        onInvalid()
                    }
                }
            }
        } ?: run {
            // 未初始化时，使用旧的验证方法
            legacyVerifyLicense(context, coroutineScope, onInvalid, onSuccess, force = true)
        }
    }

    /**
     * 旧版本验证方法，仅在LicenseManager未初始化时使用
     * @deprecated 将在未来版本中移除
     */
    @Deprecated("内部使用，将在未来版本中移除")
    private suspend fun legacyVerifyLicense(
        context: Context,
        coroutineScope: CoroutineScope,
        onInvalid: suspend () -> Unit,
        onSuccess: suspend () -> Unit,
        force: Boolean = false
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            Log.d("LicenseVerificationManager", "使用旧版本验证方法")
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            val appId = context.packageName

            val isTrialValid = try {
                withTimeoutOrNull(5000) {
                    TrialTokenManager.isTrialValid(context, deviceId, appId)
                } ?: false
            } catch (e: Exception) {
                Log.e("LicenseVerificationManager", "Failed to check trial validity: ${e.message}")
                false
            }

            if (isTrialValid) {
                Log.d("LicenseVerificationManager", "处于试用阶段，跳过许可验证")
                return@launch
            }
            performServerValidation(context, onInvalid, onSuccess)
        }
    }

    private suspend fun performServerValidation(
        context: Context,
        onInvalid: suspend () -> Unit,
        onSuccess: suspend () -> Unit
    ) {
        try {
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            val licenseKey = LicenseDataStore.getLicenseKey(context).first()
            Log.d("LicenseVerificationManager", "Performing server validation: deviceId=$deviceId, licenseKey=$licenseKey")
            if (licenseKey.isEmpty()) {
                Log.e("LicenseVerificationManager", "No license key found")
                Log.d("LicenseVerificationManager", "License validation failed - placeholder for future restrictions (e.g., disable new orders)")
                onInvalid()
                return
            }

            val startTime = System.currentTimeMillis()
            val result = LicenseValidator.validateLicense(licenseKey, deviceId)
            Log.d("LicenseVerificationManager", "Server validation result: success=${result.success}, message=${result.message}")

            if (result == null) {
                Log.e("LicenseVerificationManager", "Server validation timed out after ${System.currentTimeMillis() - startTime}ms")
                Log.d("LicenseVerificationManager", "License validation timed out - placeholder for future restrictions (e.g., disable new orders)")
                onInvalid()
                return
            }

            Log.d("LicenseVerificationManager", "Server validation result after ${System.currentTimeMillis() - startTime}ms: success=${result.success}, message=${result.message}")
            if (result.success) {
                val details = withTimeoutOrNull(5000) {
                    LicenseValidator.getLicenseDetails(licenseKey)
                }
                if (details == null) {
                    Log.e("LicenseVerificationManager", "Failed to fetch license details: timed out")
                    Log.d("LicenseVerificationManager", "Failed to fetch license details - placeholder for future restrictions (e.g., disable new orders)")
                    onInvalid()
                    return
                }
                when (details) {
                    is LicenseDetailsResult.Success -> {
                        Log.d("LicenseVerificationManager", "License details: activationDate=${details.activationDate}, validity=${details.validity}")
                        onSuccess()
                    }
                    is LicenseDetailsResult.Error -> {
                        Log.e("LicenseVerificationManager", "Failed to fetch license details: ${details.message}")
                        Log.d("LicenseVerificationManager", "Failed to fetch license details - placeholder for future restrictions (e.g., disable new orders)")
                        onInvalid()
                    }
                }
            } else {
                Log.e("LicenseVerificationManager", "Server validation failed: ${result.message}")
                Log.d("LicenseVerificationManager", "Server validation failed - placeholder for future restrictions (e.g., disable new orders)")
                onInvalid()
            }
        } catch (e: Exception) {
            Log.e("LicenseVerificationManager", "Server validation exception: ${e.message}")
            Log.d("LicenseVerificationManager", "Server validation exception - placeholder for future restrictions (e.g., disable new orders)")
            onInvalid()
        }
    }
}