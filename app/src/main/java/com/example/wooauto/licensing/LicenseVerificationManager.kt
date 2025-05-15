package com.example.wooauto.licensing

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object LicenseVerificationManager {
    fun verifyLicenseOnStart(
        context: Context,
        coroutineScope: CoroutineScope,
        onInvalid: suspend () -> Unit,
        onSuccess: suspend () -> Unit = {}
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            Log.d("LicenseVerification", "verifyLicenseOnStart initiated")
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
                Log.e("LicenseVerification", "Failed to check trial validity: ${e.message}")
                false
            }

            if (isTrialValid) {
                Log.d("LicenseVerification", "处于试用阶段，跳过许可验证")
                return@launch
            }
            performServerValidation(context, onInvalid, onSuccess)
        }
    }

    fun forceServerValidation(
        context: Context,
        coroutineScope: CoroutineScope,
        onInvalid: suspend () -> Unit,
        onSuccess: suspend () -> Unit = {}
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            Log.d("LicenseVerification", "forceServerValidation initiated")
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
                Log.e("LicenseVerification", "Failed to check trial validity: ${e.message}")
                false
            }

            if (isTrialValid) {
                Log.d("LicenseVerification", "处于试用阶段，跳过强制验证")
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
            Log.d("LicenseVerification", "Performing server validation: deviceId=$deviceId, licenseKey=$licenseKey")
            if (licenseKey.isEmpty()) {
                Log.e("LicenseVerification", "No license key found")
                Log.d("LicenseVerification", "License validation failed - placeholder for future restrictions (e.g., disable new orders)")
                onInvalid()
                return
            }

            val startTime = System.currentTimeMillis()
            val result = LicenseValidator.validateLicense(licenseKey, deviceId)
            Log.d("LicenseVerification", "Server validation result: success=${result.success}, message=${result.message}")

            if (result == null) {
                Log.e("LicenseVerification", "Server validation timed out after ${System.currentTimeMillis() - startTime}ms")
                Log.d("LicenseVerification", "License validation timed out - placeholder for future restrictions (e.g., disable new orders)")
                onInvalid()
                return
            }

            Log.d("LicenseVerification", "Server validation result after ${System.currentTimeMillis() - startTime}ms: success=${result.success}, message=${result.message}")
            if (result.success) {
                val details = withTimeoutOrNull(5000) {
                    LicenseValidator.getLicenseDetails(licenseKey)
                }
                if (details == null) {
                    Log.e("LicenseVerification", "Failed to fetch license details: timed out")
                    Log.d("LicenseVerification", "Failed to fetch license details - placeholder for future restrictions (e.g., disable new orders)")
                    onInvalid()
                    return
                }
                when (details) {
                    is LicenseDetailsResult.Success -> {
                        Log.d("LicenseVerification", "License details: activationDate=${details.activationDate}, validity=${details.validity}")
                        onSuccess()
                    }
                    is LicenseDetailsResult.Error -> {
                        Log.e("LicenseVerification", "Failed to fetch license details: ${details.message}")
                        Log.d("LicenseVerification", "Failed to fetch license details - placeholder for future restrictions (e.g., disable new orders)")
                        onInvalid()
                    }
                }
            } else {
                Log.e("LicenseVerification", "Server validation failed: ${result.message}")
                Log.d("LicenseVerification", "Server validation failed - placeholder for future restrictions (e.g., disable new orders)")
                onInvalid()
            }
        } catch (e: Exception) {
            Log.e("LicenseVerification", "Server validation exception: ${e.message}")
            Log.d("LicenseVerification", "Server validation exception - placeholder for future restrictions (e.g., disable new orders)")
            onInvalid()
        }
    }
}