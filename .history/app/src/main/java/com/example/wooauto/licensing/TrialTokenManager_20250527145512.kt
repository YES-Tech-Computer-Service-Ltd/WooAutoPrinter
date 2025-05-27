package com.example.wooauto.licensing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import java.util.Calendar
import java.util.Date

/**
 * 用于管理应用试用期的工具类
 */
object TrialTokenManager {
    private const val TAG = "TrialTokenManager"
    
    // 试用期天数（现在统一使用SharedPreferences中的KEY_EXPIRES）
    private const val DEFAULT_TRIAL_DAYS = 10
    
    // SharedPreferences存储的键
    private const val PREF_NAME = "secure_trial_data"
    private const val KEY_TOKEN = "trial_token"
    private const val KEY_SIGNATURE = "signature"
    private const val KEY_EXPIRES = "expires_in_days"
    private const val KEY_FIRST_LAUNCH = "first_launch_time"
    private const val KEY_SERVER_FIRST_LAUNCH = "server_first_launch_time"
    private const val MAX_RETRIES = 2

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    private const val TRIAL_API = "https://yestech.ca/wp-json/trial/v1/start"
    private const val VERIFY_API = "https://yestech.ca/wp-json/trial/v1/verify"
    
    private val mutex = Mutex()
    private var isTrialInitialized = false
    private var cachedTrialValid: Boolean? = null
    
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getOrInitFirstLaunchTime(context: Context, serverFirstLaunch: Long? = null): Long {
        val prefs = getPrefs(context)
        val serverFirstLaunchTime = serverFirstLaunch ?: prefs.getLong(KEY_SERVER_FIRST_LAUNCH, 0L)
        if (serverFirstLaunchTime != 0L) {
            return serverFirstLaunchTime
        }

        val firstLaunchTime = prefs.getLong(KEY_FIRST_LAUNCH, 0L)
        if (firstLaunchTime == 0L) {
            val currentTime = System.currentTimeMillis()
            prefs.edit().putLong(KEY_FIRST_LAUNCH, currentTime).apply()
            Log.d("TrialDebug", "Initialized first launch time: $currentTime")
            return currentTime
        }
        return firstLaunchTime
    }

    private suspend fun fetchTrialFromServer(deviceId: String, appId: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject()
                json.put("device_id", deviceId)
                json.put("app_id", appId)

                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url(TRIAL_API).post(body).build()
                val res = client.newCall(req).execute()

                if (!res.isSuccessful) {
                    Log.w("TrialManager", "Server request failed with status: ${res.code}")
                    return@withContext null
                }

                val resJson = JSONObject(res.body?.string() ?: throw Exception("Empty response"))
                Log.d("TrialDebug", "START API response JSON = $resJson")
                resJson
            } catch (e: Exception) {
                Log.w("TrialManager", "Trial fetch error: ${e.message}")
                null
            }
        }
    }

    private suspend fun fetchTrialWithRetry(deviceId: String, appId: String): JSONObject? {
        var attempt = 0
        var resJson: JSONObject? = null
        while (attempt < MAX_RETRIES && resJson == null) {
            Log.d("TrialDebug", "Attempting trial fetch, attempt ${attempt + 1}/$MAX_RETRIES")
            resJson = withTimeoutOrNull(3000) { // 每个请求最多 3 秒
                fetchTrialFromServer(deviceId, appId)
            }
            if (resJson == null) {
                attempt++
                Log.d("TrialDebug", "Retrying trial fetch, attempt ${attempt + 1}/$MAX_RETRIES")
                if (attempt < MAX_RETRIES) delay(1000)
            }
        }
        return resJson
    }

    suspend fun requestTrialIfNeeded(context: Context, deviceId: String, appId: String): Boolean {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                Log.d("TrialDebug", "Starting requestTrialIfNeeded")
                val prefs = getPrefs(context)
                val cachedToken = prefs.getString(KEY_TOKEN, null)
                val cachedSig = prefs.getString(KEY_SIGNATURE, null)

                if (cachedToken != null && cachedSig != null) {
                    Log.d("TrialDebug", "Verifying cached token with server")
                    val isValid = verifyWithServer(deviceId, appId, cachedToken, cachedSig)
                    if (!isValid) {
                        Log.w("TrialManager", "Server verification failed, proceeding with local check")
                    }
                }

                val firstLaunchTime = getOrInitFirstLaunchTime(context)

                if (!isNetworkAvailable(context)) {
                    Log.w("TrialManager", "Network unavailable, setting default trial period of $DEFAULT_TRIAL_DAYS days")
                    prefs.edit()
                        .putString(KEY_TOKEN, "default_token")
                        .putString(KEY_SIGNATURE, "default_signature")
                        .putInt(KEY_EXPIRES, DEFAULT_TRIAL_DAYS)
                        .commit()
                    isTrialInitialized = false
                    cachedTrialValid = null
                    return@withLock true
                }

                val resJson = fetchTrialWithRetry(deviceId, appId)

                if (resJson == null) {
                    Log.w("TrialManager", "Server request failed after $MAX_RETRIES retries, setting default trial period of $DEFAULT_TRIAL_DAYS days")
                    prefs.edit()
                        .putString(KEY_TOKEN, "default_token")
                        .putString(KEY_SIGNATURE, "default_signature")
                        .putInt(KEY_EXPIRES, DEFAULT_TRIAL_DAYS)
                        .commit()
                    isTrialInitialized = false
                    cachedTrialValid = null
                    return@withLock true
                }

                resJson.let { json ->
                    try {
                        val token = json.getString("trial_token")
                        val sig = json.getString("signature")
                        val days = json.getInt("expires_in_days")
                        val serverFirstLaunch = json.optLong("first_launch_time", 0L)

                        prefs.edit()
                            .putString(KEY_TOKEN, token)
                            .putString(KEY_SIGNATURE, sig)
                            .putInt(KEY_EXPIRES, days)
                            .apply()
                        if (serverFirstLaunch != 0L) {
                            prefs.edit().putLong(KEY_SERVER_FIRST_LAUNCH, serverFirstLaunch).apply()
                        }
                        isTrialInitialized = false
                        cachedTrialValid = null
                        Log.d("TrialDebug", "requestTrialIfNeeded returned: true")
                        return@withLock true
                    } catch (e: Exception) {
                        Log.w("TrialManager", "Trial fetch error: ${e.message}, setting default trial period of $DEFAULT_TRIAL_DAYS days")
                        prefs.edit()
                            .putString(KEY_TOKEN, "default_token")
                            .putString(KEY_SIGNATURE, "default_signature")
                            .putInt(KEY_EXPIRES, DEFAULT_TRIAL_DAYS)
                            .commit()
                        isTrialInitialized = false
                        cachedTrialValid = null
                        return@withLock true
                    }
                }

                return@withLock false
            }
        }
    }

    suspend fun isTrialValid(context: Context, deviceId: String, appId: String): Boolean {
        try {
            // 使用SharedPreferences，与getRemainingDays()保持一致
            val prefs = getPrefs(context)
            
            // 检查是否有试用期数据，如果没有则初始化
            val trialDays = prefs.getInt(KEY_EXPIRES, -1)
            if (trialDays == -1) {
                Log.d(TAG, "没有试用期数据，初始化试用期")
                requestTrialIfNeeded(context, deviceId, appId)
                return true // 新初始化的试用期应该是有效的
            }
            
            // 获取首次启动时间
            val firstLaunchTime = getOrInitFirstLaunchTime(context)
            val currentTime = System.currentTimeMillis()
            
            // 计算试用期结束时间
            val trialEndTime = firstLaunchTime + TimeUnit.DAYS.toMillis(trialDays.toLong())
            val remainingMillis = trialEndTime - currentTime
            
            // 检查试用期是否有效
            val isValid = remainingMillis > 0
            
            Log.d(TAG, "试用期检查: firstLaunchTime=$firstLaunchTime, trialDays=$trialDays, currentTime=$currentTime, remainingMillis=$remainingMillis, isValid=$isValid")
            
            return isValid
            
        } catch (e: Exception) {
            Log.e(TAG, "检查试用期出错", e)
            return false
        }
    }

    private suspend fun verifyWithServer(
        deviceId: String,
        appId: String,
        token: String,
        signature: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject()
                json.put("device_id", deviceId)
                json.put("app_id", appId)
                json.put("trial_token", token)
                json.put("signature", signature)

                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url(VERIFY_API).post(body).build()
                val res = withTimeoutOrNull(3000) { client.newCall(req).execute() }
                    ?: throw Exception("Verify API timed out after 3 seconds")

                if (!res.isSuccessful) {
                    Log.w("TrialManager", "Verify API failed with status: ${res.code}, assuming valid")
                    return@withContext true
                }

                val result = JSONObject(res.body?.string() ?: return@withContext true)
                val isValid = result.optBoolean("valid", true)
                Log.d("TrialDebug", "Verify API result: $isValid, full response: $result")
                isValid
            } catch (e: Exception) {
                Log.w("TrialManager", "Verify failed: ${e.message}, assuming valid")
                return@withContext true
            }
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    suspend fun getRemainingDays(context: Context, deviceId: String, appId: String): Int {
        val prefs = getPrefs(context)
        val trialDays = prefs.getInt(KEY_EXPIRES, -1)

        if (trialDays == -1) {
            Log.d("TrialDebug", "No trial data found, initializing trial")
            requestTrialIfNeeded(context, deviceId, appId)
            return getRemainingDays(context, deviceId, appId)
        }

        val firstLaunchTime = getOrInitFirstLaunchTime(context)
        val currentTime = System.currentTimeMillis()

        if (currentTime < firstLaunchTime) {
            Log.w("TrialManager", "Device time is earlier than first launch time, assuming trial is valid")
            return DEFAULT_TRIAL_DAYS
        }

        val trialEndTime = firstLaunchTime + TimeUnit.DAYS.toMillis(trialDays.toLong())
        val remainingMillis = trialEndTime - currentTime

        val remainingDays = if (remainingMillis <= 0) {
            0
        } else {
            ceil(remainingMillis.toDouble() / TimeUnit.DAYS.toMillis(1)).toInt()
        }
        if (remainingDays <= 0) {
            Log.d("TrialDebug", "Trial expired - placeholder for future restrictions (e.g., disable new orders)")
        }
        Log.d("TrialDebug", "Remaining days: $remainingDays (firstLaunchTime=$firstLaunchTime, trialDays=$trialDays, currentTime=$currentTime, remainingMillis=$remainingMillis)")
        return remainingDays
    }
    
    fun simulateTrialExpired(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().putInt(KEY_EXPIRES, 0).apply()
        cachedTrialValid = false
        Log.d("TrialDebug", "Simulated trial expired")
    }

    fun clearTrialData(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().clear().apply()
        cachedTrialValid = null
        isTrialInitialized = false
        Log.d("TrialDebug", "Cleared trial data")
    }

    fun isTrialExpired(context: Context): Boolean {
        val prefs = getPrefs(context)
        val days = prefs.getInt(KEY_EXPIRES, -1)
        return days <= 0
    }

    /**
     * 初始化试用期
     */
    private suspend fun initializeTrial(context: Context, deviceId: String, appId: String) {
        val now = System.currentTimeMillis()
        context.trialDataStore.edit { preferences ->
            preferences[TRIAL_START_KEY] = now
            preferences[TRIAL_DEVICE_ID_KEY] = deviceId
            preferences[TRIAL_APP_ID_KEY] = appId
            preferences[TRIAL_EXPIRED_KEY] = false
        }
        Log.d(TAG, "Trial初始化完成，开始日期: ${Date(now)}")
    }

    /**
     * 标记试用期已过期
     */
    private suspend fun markTrialExpired(context: Context) {
        context.trialDataStore.edit { preferences ->
            preferences[TRIAL_EXPIRED_KEY] = true
        }
        Log.d(TAG, "已标记trial为过期")
    }

    /**
     * 计算剩余天数
     */
    private fun calculateDaysLeft(startTime: Long, currentTime: Long): Int {
        val elapsedDays = TimeUnit.MILLISECONDS.toDays(currentTime - startTime)
        return (TRIAL_DAYS - elapsedDays).toInt().coerceAtLeast(0)
    }
    
    /**
     * 获取试用期结束日期
     */
    suspend fun getTrialEndDate(context: Context): Date? {
        try {
            // 使用SharedPreferences，与其他方法保持一致
            val prefs = getPrefs(context)
            val trialDays = prefs.getInt(KEY_EXPIRES, -1)
            if (trialDays == -1) return null
            
            val firstLaunchTime = getOrInitFirstLaunchTime(context)
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = firstLaunchTime
            calendar.add(Calendar.DAY_OF_MONTH, trialDays)
            return calendar.time
        } catch (e: Exception) {
            Log.e(TAG, "获取试用期结束日期出错", e)
            return null
        }
    }
    
    /**
     * 重置试用期（仅用于开发测试）
     */
    suspend fun resetTrial(context: Context) {
        try {
            // 清除SharedPreferences中的所有试用期数据
            val prefs = getPrefs(context)
            prefs.edit().clear().apply()
            
            // 重置缓存状态
            cachedTrialValid = null
            isTrialInitialized = false
            
            Log.d(TAG, "试用期已重置")
        } catch (e: Exception) {
            Log.e(TAG, "重置试用期出错", e)
        }
    }

    /**
     * 获取剩余试用天数（使用SharedPreferences）
     */
    suspend fun getTrialDaysLeft(context: Context): Int {
        try {
            // 使用SharedPreferences，与getRemainingDays()保持一致
            val prefs = getPrefs(context)
            val trialDays = prefs.getInt(KEY_EXPIRES, -1)
            if (trialDays == -1) return 0
            
            val firstLaunchTime = getOrInitFirstLaunchTime(context)
            val currentTime = System.currentTimeMillis()
            val trialEndTime = firstLaunchTime + TimeUnit.DAYS.toMillis(trialDays.toLong())
            val remainingMillis = trialEndTime - currentTime
            
            return if (remainingMillis <= 0) {
                0
            } else {
                ceil(remainingMillis.toDouble() / TimeUnit.DAYS.toMillis(1)).toInt()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取剩余试用天数出错", e)
            return 0
        }
    }
    
    /**
     * 强制结束试用期
     * 使用SharedPreferences确保数据一致性
     */
    suspend fun forceExpireTrial(context: Context) {
        try {
            // 使用SharedPreferences设置过期标记
            val prefs = getPrefs(context)
            prefs.edit()
                .putInt(KEY_EXPIRES, 0)
                .putLong(KEY_FIRST_LAUNCH, 0L)
                .apply()
                
            // 重置缓存状态
            cachedTrialValid = false
            isTrialInitialized = false
            
            Log.d(TAG, "试用期已强制结束")
        } catch (e: Exception) {
            Log.e(TAG, "强制结束试用期出错", e)
        }
    }
    
    /**
     * 检查本地和服务器端的试用期状态
     * 如果服务器返回试用期已过期，则强制过期本地试用期
     */
    suspend fun verifyTrialWithServer(context: Context, deviceId: String, appId: String): Boolean {
        try {
            // 1. 获取本地存储的令牌
            val prefs = getPrefs(context)
            val cachedToken = prefs.getString(KEY_TOKEN, null) 
            val cachedSig = prefs.getString(KEY_SIGNATURE, null)
            
            if (cachedToken == null || cachedSig == null) {
                Log.d(TAG, "本地没有有效的试用期令牌")
                return false
            }
            
            // 2. 向服务器验证
            val json = JSONObject()
            json.put("device_id", deviceId)
            json.put("app_id", appId)
            json.put("trial_token", cachedToken)
            json.put("signature", cachedSig)
            json.put("verify_type", "full_check") // 告诉服务器执行完整检查
            
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(VERIFY_API).post(body).build()
            
            val res = withTimeoutOrNull(5000) { client.newCall(req).execute() }
                ?: throw Exception("服务器验证超时")
                
            if (!res.isSuccessful) {
                Log.w(TAG, "服务器验证失败: ${res.code}")
                return false
            }
            
            val result = JSONObject(res.body?.string() ?: return false)
            val isValid = result.optBoolean("valid", false)
            val serverMessage = result.optString("message", "")
            
            Log.d(TAG, "服务器验证结果: $isValid, 消息: $serverMessage")
            
            // 3. 如果服务器明确返回试用期无效，则强制过期本地试用期
            if (!isValid) {
                Log.d(TAG, "服务器指示试用期已过期，强制结束本地试用期")
                forceExpireTrial(context)
                return false
            }
            
            return isValid
        } catch (e: Exception) {
            Log.e(TAG, "服务器验证出错", e)
            return false
        }
    }
}