package com.example.wooauto.licensing

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 证书验证管理器
 * 
 * 此类保留原有API以兼容现有代码，内部使用LicenseManager实现
 * 新代码应直接使用LicenseManager
 */
@Singleton
class LicenseVerificationManager @Inject constructor(
    private val licenseManager: LicenseManager
) {
    /**
     * 在应用启动时验证证书
     */
    fun verifyLicenseOnStart(
        context: Context,
        coroutineScope: CoroutineScope,
        onInvalid: suspend () -> Unit,
        onSuccess: suspend () -> Unit = {}
    ) {
        Log.d("LicenseVerificationManager", "verifyLicenseOnStart - 使用LicenseManager")
        
        licenseManager.verifyLicense(context, coroutineScope) { isValid ->
            coroutineScope.launch(Dispatchers.IO) {
                if (isValid) {
                    onSuccess()
                } else {
                    onInvalid()
                }
            }
        }
    }

    /**
     * 强制服务器验证
     */
    fun forceServerValidation(
        context: Context,
        coroutineScope: CoroutineScope,
        onInvalid: suspend () -> Unit,
        onSuccess: suspend () -> Unit = {}
    ) {
        Log.d("LicenseVerificationManager", "forceServerValidation - 使用LicenseManager")
        
        licenseManager.verifyLicense(context, coroutineScope, force = true) { isValid ->
            coroutineScope.launch(Dispatchers.IO) {
                if (isValid) {
                    onSuccess()
                } else {
                    onInvalid()
                }
            }
        }
    }
    
    /**
     * 检查证书是否有效
     */
    fun isLicenseValid(): Boolean {
        return licenseManager.isLicenseValid
    }
    
    /**
     * 获取当前证书信息
     */
    fun getLicenseInfo(): LicenseInfo {
        return licenseManager.licenseInfo.value ?: LicenseInfo()
    }
    
    companion object {
        // 为兼容旧代码提供的静态方法
        @Volatile
        private var INSTANCE: LicenseVerificationManager? = null
        
        @JvmStatic
        fun getInstance(): LicenseVerificationManager {
            return INSTANCE ?: throw IllegalStateException("LicenseVerificationManager未初始化")
        }
        
        @JvmStatic
        internal fun initialize(instance: LicenseVerificationManager) {
            INSTANCE = instance
        }
    }
}