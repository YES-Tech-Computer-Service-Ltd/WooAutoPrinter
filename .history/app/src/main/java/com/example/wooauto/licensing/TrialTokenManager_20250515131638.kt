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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Date

private val Context.trialDataStore by preferencesDataStore(name = "trial_data")

/**
 * 用于管理应用试用期的工具类
 */
object TrialTokenManager {
    private const val TAG = "TrialTokenManager"
    
    // 存储的键
    private val TRIAL_START_KEY = longPreferencesKey("trial_start")
    private val TRIAL_DEVICE_ID_KEY = stringPreferencesKey("trial_device_id")
    private val TRIAL_APP_ID_KEY = stringPreferencesKey("trial_app_id")
    private val TRIAL_EXPIRED_KEY = booleanPreferencesKey("trial_expired")

    // 试用期天数
    private const val TRIAL_DAYS = 14 // 14天试用期
    
    private const val PREF_NAME = "secure_trial_data"
    private const val KEY_TOKEN = "trial_token"
    private const val KEY_SIGNATURE = "signature"
    private const val KEY_EXPIRES = "expires_in_days"
    private const val KEY_FIRST_LAUNCH = "first_launch_time"
    private const val KEY_SERVER_FIRST_LAUNCH = "server_first_launch_time"
    private const val DEFAULT_TRIAL_DAYS = 10
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
            val preferences = context.trialDataStore.data.first()
            val storedDeviceId = preferences[TRIAL_DEVICE_ID_KEY]
            val storedAppId = preferences[TRIAL_APP_ID_KEY]
            val trialStart = preferences[TRIAL_START_KEY]
            val isExpired = preferences[TRIAL_EXPIRED_KEY] ?: false

            // 如果已标记为过期，直接返回无效
            if (isExpired) {
                Log.d(TAG, "Trial已标记为过期")
                return false
            }

            // 如果设备ID或应用ID不匹配，这是首次使用，初始化试用期
            if (storedDeviceId != deviceId || storedAppId != appId || trialStart == null) {
                Log.d(TAG, "首次使用，初始化试用期")
                initializeTrial(context, deviceId, appId)
                return true
            }

            // 检查试用期是否过期
            val now = System.currentTimeMillis()
            val daysLeft = calculateDaysLeft(trialStart, now)
            
            if (daysLeft <= 0) {
                Log.d(TAG, "试用期已结束")
                // 标记为已过期
                markTrialExpired(context)
                return false
            }
            
            Log.d(TAG, "试用期有效，剩余天数: $daysLeft")
            return true
            
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
            val preferences = context.trialDataStore.data.first()
            val trialStart = preferences[TRIAL_START_KEY] ?: return null
            
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = trialStart
            calendar.add(Calendar.DAY_OF_MONTH, TRIAL_DAYS)
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
        context.trialDataStore.edit { preferences ->
            preferences.clear()
        }
        Log.d(TAG, "试用期已重置")
    }

    /**
     * 获取剩余试用天数
     */
    suspend fun getTrialDaysLeft(context: Context): Int {
        try {
            val preferences = context.trialDataStore.data.first()
            val trialStart = preferences[TRIAL_START_KEY] ?: return 0
            val isExpired = preferences[TRIAL_EXPIRED_KEY] ?: false
            
            if (isExpired) return 0
            
            val now = System.currentTimeMillis()
            return calculateDaysLeft(trialStart, now)
        } catch (e: Exception) {
            Log.e(TAG, "获取剩余试用天数出错", e)
            return 0
        }
    }
}