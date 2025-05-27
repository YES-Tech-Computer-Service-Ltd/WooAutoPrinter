package com.example.wooauto.utils

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.example.wooauto.licensing.LicenseManager
import com.example.wooauto.licensing.TrialTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 调试辅助类，用于诊断证书状态问题
 */
object DebugHelper {
    private const val TAG = "DebugHelper"

    /**
     * 检查证书状态和试用期状态
     */
    fun checkLicenseStatus(context: Context, licenseManager: LicenseManager) {
        // 使用独立的协程作用域来避免composition问题
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== 开始证书状态检查 ===")

                // 获取设备信息
                val deviceId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                val appId = context.packageName

                Log.d(TAG, "设备ID: $deviceId")
                Log.d(TAG, "应用ID: $appId")

                // 检查当前证书状态
                val licenseInfo = licenseManager.licenseInfo.value
                Log.d(TAG, "当前证书状态: ${licenseInfo?.status}")
                Log.d(TAG, "isLicenseValid: ${licenseManager.isLicenseValid}")

                // 检查试用期状态
                withContext(Dispatchers.IO) {
                    try {
                        Log.d(TAG, "=== 检查试用期状态 ===")

                        // 1. 检查剩余天数
                        val remainingDays =
                            TrialTokenManager.getRemainingDays(context, deviceId, appId)
                        Log.d(TAG, "试用期剩余天数: $remainingDays")

                        // 2. 检查试用期是否有效
                        val isTrialValid = TrialTokenManager.isTrialValid(context, deviceId, appId)
                        Log.d(TAG, "试用期是否有效: $isTrialValid")

                        // 3. 检查试用期是否过期
                        val isTrialExpired = TrialTokenManager.isTrialExpired(context)
                        Log.d(TAG, "试用期是否过期: $isTrialExpired")

                        // 4. 检查服务器验证状态
                        try {
                            val serverValid =
                                TrialTokenManager.verifyTrialWithServer(context, deviceId, appId)
                            Log.d(TAG, "服务器验证试用期结果: $serverValid")
                        } catch (e: Exception) {
                            Log.w(TAG, "服务器验证失败: ${e.message}")
                        }

                        // 5. 分析不一致的原因
                        if (remainingDays > 0 && !isTrialValid) {
                            Log.w(TAG, "⚠️ 发现不一致：剩余天数>0但试用期无效，可能原因：")
                            Log.w(TAG, "  - 服务器验证失败导致强制过期")
                            Log.w(TAG, "  - 缓存状态不同步")
                            Log.w(TAG, "  - 多种存储方式冲突")
                        } else {
                            Log.d(TAG, "试用期状态一致")
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "检查试用期状态时出错: ${e.message}", e)
                    }
                }

                Log.d(TAG, "=== 证书状态检查完成 ===")

            } catch (e: Exception) {
                Log.e(TAG, "检查证书状态时出错: ${e.message}", e)
            }
        }
    }

    /**
     * 重置试用期状态以便重新检查
     */
    fun resetTrialStatus(context: Context, licenseManager: LicenseManager) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== 重置试用期状态 ===")

                // 清除试用期缓存
                TrialTokenManager.clearTrialData(context)

                // 重置许可证状态
                licenseManager.resetLicenseStatus()

                Log.d(TAG, "试用期状态已重置，将重新检查")

                // 等待一秒后重新检查
                delay(1000)

                val deviceId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                val appId = context.packageName

                // 重新请求试用期
                val requestResult = TrialTokenManager.requestTrialIfNeeded(context, deviceId, appId)
                Log.d(TAG, "重新请求试用期结果: $requestResult")

                // 检查重置后的状态
                checkLicenseStatus(context, licenseManager)

            } catch (e: Exception) {
                Log.e(TAG, "重置试用期状态时出错: ${e.message}", e)
            }
        }
    }
}