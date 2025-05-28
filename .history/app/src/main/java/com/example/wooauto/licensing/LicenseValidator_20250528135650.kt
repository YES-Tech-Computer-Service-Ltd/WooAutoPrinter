package com.example.wooauto.licensing

import android.util.Log
import com.example.wooauto.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class LicenseValidationResult(
    val success: Boolean,
    val message: String
)

sealed class LicenseDetailsResult {
    data class Success(
        val activationDate: String,
        val validity: Int,
        val edition: String = "Spire",
        val capabilities: String = "cap1, cap2",
        val licensedTo: String = "MockCustomer"
    ) : LicenseDetailsResult()
    data class Error(val message: String) : LicenseDetailsResult()
}

object LicenseValidator {
    private const val API_URL = "https://yestech.ca/wp-json/license/v1/"
    
    private const val DEBUG_SKIP_LICENSE = BuildConfig.DEBUG

    /**
     * 验证许可证
     */
    suspend fun validateLicense(licenseKey: String, deviceId: String): LicenseValidationResult = withContext(Dispatchers.IO) {
        if (DEBUG_SKIP_LICENSE) {
            Log.d("LicenseValidator", "调试模式：跳过License验证")
            return@withContext LicenseValidationResult(true, "调试模式：验证已跳过")
        }
        
        try {
            val response = makeApiRequest(
                endpoint = "validate",
                data = mapOf(
                    "license_key" to licenseKey,
                    "device_id" to deviceId,
                    "app_id" to "wooauto_app"
                )
            )

            val jsonResponse = JSONObject(response)
            val success = jsonResponse.getBoolean("success")
            val message = jsonResponse.optString("message", "未知错误")

            LicenseValidationResult(success, message)
        } catch (e: Exception) {
            Log.e("LicenseValidator", "验证许可证时发生错误: ${e.message}", e)
            LicenseValidationResult(false, "网络错误: ${e.message}")
        }
    }

    /**
     * 激活许可证
     */
    suspend fun activateLicense(licenseKey: String, deviceId: String): LicenseValidationResult = withContext(Dispatchers.IO) {
        try {
            val response = makeApiRequest(
                endpoint = "activate",
                data = mapOf(
                    "license_key" to licenseKey,
                    "device_id" to deviceId,
                    "app_id" to "wooauto_app"
                )
            )

            val jsonResponse = JSONObject(response)
            val success = jsonResponse.getBoolean("success")
            val message = jsonResponse.optString("message", "激活失败")

            LicenseValidationResult(success, message)
        } catch (e: Exception) {
            Log.e("LicenseValidator", "激活许可证时发生错误: ${e.message}", e)
            LicenseValidationResult(false, "网络错误: ${e.message}")
        }
    }

    /**
     * 获取许可证详情
     */
    suspend fun getLicenseDetails(licenseKey: String): LicenseDetailsResult = withContext(Dispatchers.IO) {
        try {
            val response = makeApiRequest(
                endpoint = "details",
                data = mapOf(
                    "license_key" to licenseKey,
                    "app_id" to "wooauto_app"
                )
            )

            val jsonResponse = JSONObject(response)
            
            if (jsonResponse.getBoolean("success")) {
                val details = jsonResponse.getJSONObject("details")
                LicenseDetailsResult.Success(
                    activationDate = details.getString("activation_date"),
                    validity = details.getInt("validity"),
                    edition = details.optString("edition", "Standard"),
                    capabilities = details.optString("capabilities", ""),
                    licensedTo = details.optString("licensed_to", "未知用户")
                )
            } else {
                val message = jsonResponse.optString("message", "获取详情失败")
                LicenseDetailsResult.Error(message)
            }
        } catch (e: Exception) {
            Log.e("LicenseValidator", "获取许可证详情时发生错误: ${e.message}", e)
            LicenseDetailsResult.Error("网络错误: ${e.message}")
        }
    }

    private suspend fun makeApiRequest(endpoint: String, data: Map<String, String>): String = withContext(Dispatchers.IO) {
        val url = URL(API_URL + endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("User-Agent", System.getProperty("http.agent") ?: "Android")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("Accept", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        val postData = data.map { (key, value) ->
            "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
        }.joinToString("&")
        Log.d("LicenseValidator", "请求URL: ${url}")
        Log.d("LicenseValidator", "Post data: $postData")

        conn.outputStream.use { os: OutputStream ->
            os.write(postData.toByteArray(StandardCharsets.UTF_8))
        }

        val responseCode = conn.responseCode
        val contentType = conn.getHeaderField("Content-Type") ?: ""
        Log.d("LicenseValidator", "Response code: $responseCode")
        Log.d("LicenseValidator", "Content-Type: $contentType")
        Log.d("LicenseValidator", "Response message: ${conn.responseMessage}")

        val response = if (responseCode == HttpURLConnection.HTTP_OK) {
            conn.inputStream.use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    reader.readText()
                }
            }
        } else {
            val errorResponse = conn.errorStream?.use { error: InputStream ->
                BufferedReader(InputStreamReader(error)).use { reader: BufferedReader ->
                    reader.readText()
                }
            } ?: "HTTP error: $responseCode"
            Log.e("LicenseValidator", "Error response: $errorResponse")
            throw Exception("HTTP error $responseCode: $errorResponse")
        }

        if (response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html")) {
            Log.e("LicenseValidator", "API返回了HTML页面而不是JSON，可能是端点URL错误")
            Log.e("LicenseValidator", "Response preview: ${response.take(200)}...")
            throw Exception("API端点返回了HTML页面，请检查API_URL配置")
        }
        
        if (!contentType.contains("application/json", ignoreCase = true) && response.isNotBlank()) {
            Log.w("LicenseValidator", "警告：响应Content-Type不是JSON: $contentType")
        }

        conn.disconnect()
        Log.d("LicenseValidator", "Response preview: ${response.take(200)}...")
        response
    }
}