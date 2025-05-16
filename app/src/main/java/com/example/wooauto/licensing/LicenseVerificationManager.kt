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
@Deprecated("建议在新代码中使用LicenseManager替代此类，此类将在未来版本中移除", ReplaceWith("licenseManager", "com.example.wooauto.licensing.LicenseManager"))
@Singleton
class LicenseVerificationManager @Inject constructor(
    private val licenseManager: LicenseManager
) {
    /**
     * 在应用启动时验证证书
     */
    @Deprecated("建议使用LicenseManager.verifyLicense()替代", ReplaceWith("licenseManager.verifyLicense(context, coroutineScope, false, onValidationComplete)", "com.example.wooauto.licensing.LicenseManager"))
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
    @Deprecated("建议使用LicenseManager.verifyLicense()替代", ReplaceWith("licenseManager.verifyLicense(context, coroutineScope, true, onValidationComplete)", "com.example.wooauto.licensing.LicenseManager"))
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
    @Deprecated("建议使用LicenseManager.isLicenseValid属性替代", ReplaceWith("licenseManager.isLicenseValid", "com.example.wooauto.licensing.LicenseManager"))
    fun isLicenseValid(): Boolean {
        return licenseManager.isLicenseValid
    }
    
    /**
     * 获取当前证书信息
     */
    @Deprecated("建议使用LicenseManager.licenseInfo属性替代", ReplaceWith("licenseManager.licenseInfo.value", "com.example.wooauto.licensing.LicenseManager"))
    fun getLicenseInfo(): LicenseInfo {
        return licenseManager.licenseInfo.value ?: LicenseInfo()
    }
    
    companion object {
        // 为兼容旧代码提供的静态方法
        @Volatile
        private var INSTANCE: LicenseVerificationManager? = null
        
        @JvmStatic
        @Deprecated("建议通过依赖注入获取LicenseManager实例替代", level = DeprecationLevel.WARNING)
        fun getInstance(): LicenseVerificationManager {
            return INSTANCE ?: throw IllegalStateException("LicenseVerificationManager未初始化")
        }
        
        @JvmStatic
        internal fun initialize(instance: LicenseVerificationManager) {
            INSTANCE = instance
        }
        
        /**
         * 静态验证方法 - 使用INSTANCE代理
         * @deprecated 建议通过依赖注入获取LicenseManager实例替代
         */
        @JvmStatic
        @Deprecated("建议使用依赖注入获取的LicenseManager.verifyLicense()替代", level = DeprecationLevel.WARNING)
        fun staticVerifyLicenseOnStart(
            context: Context,
            coroutineScope: CoroutineScope,
            onInvalid: suspend () -> Unit,
            onSuccess: suspend () -> Unit = {}
        ) {
            getInstance().verifyLicenseOnStart(context, coroutineScope, onInvalid, onSuccess)
        }
        
        /**
         * 静态强制验证方法 - 使用INSTANCE代理
         * @deprecated 建议通过依赖注入获取LicenseManager实例替代
         */
        @JvmStatic
        @Deprecated("建议使用依赖注入获取的LicenseManager.verifyLicense()替代", level = DeprecationLevel.WARNING)
        fun staticForceServerValidation(
            context: Context,
            coroutineScope: CoroutineScope,
            onInvalid: suspend () -> Unit,
            onSuccess: suspend () -> Unit = {}
        ) {
            getInstance().forceServerValidation(context, coroutineScope, onInvalid, onSuccess)
        }
        
        /**
         * 静态检查证书是否有效 - 使用INSTANCE代理
         * @deprecated 建议通过依赖注入获取LicenseManager实例替代
         */
        @JvmStatic
        @Deprecated("建议使用依赖注入获取的LicenseManager.isLicenseValid替代", level = DeprecationLevel.WARNING)
        fun staticIsLicenseValid(): Boolean {
            return getInstance().isLicenseValid()
        }
    }
}