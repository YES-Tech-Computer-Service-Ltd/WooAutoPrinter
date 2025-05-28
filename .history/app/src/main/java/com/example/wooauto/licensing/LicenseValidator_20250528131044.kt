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
    private const val API_URL = "https://yestech.ca/"

    suspend fun validateLicense(licenseKey: String, deviceId: String): LicenseValidationResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = makeApiCall(
                    action = "validate",
                    licenseKey = licenseKey,
                    deviceId = deviceId
                )
                
                if (response.isBlank()) {
                    return@withContext LicenseValidationResult(false, "Empty response from server")
                }

                val success: Boolean
                val message: String
                
                try {
                    val jsonResponse = JSONObject(response)
                    if (jsonResponse.has("result")) {
                        success = jsonResponse.getBoolean("result")
                        message = jsonResponse.optString("message", "No message provided")
                    } else {
                        return@withContext LicenseValidationResult(false, "Response missing 'result' field: $response")
                    }
                } catch (e: JSONException) {
                    return@withContext LicenseValidationResult(false, "Response is not valid JSON: $response")
                }

                LicenseValidationResult(success, message)
            } catch (e: Exception) {
                LicenseValidationResult(false, "Network error: ${e.message}")
            }
        }
    }

    suspend fun activateLicense(licenseKey: String, deviceId: String): LicenseValidationResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = makeApiCall(
                    action = "activate",
                    licenseKey = licenseKey,
                    deviceId = deviceId
                )
                
                if (response.isBlank()) {
                    return@withContext LicenseValidationResult(false, "Empty response from server")
                }

                val success: Boolean
                val message: String
                
                try {
                    val jsonResponse = JSONObject(response)
                    if (jsonResponse.has("result")) {
                        success = jsonResponse.getBoolean("result")
                        message = jsonResponse.optString("message", "No message provided")
                    } else {
                        return@withContext LicenseValidationResult(false, "Response missing 'result' field: $response")
                    }
                } catch (e: JSONException) {
                    return@withContext LicenseValidationResult(false, "Response is not valid JSON: $response")
                }

                LicenseValidationResult(success, message)
            } catch (e: Exception) {
                LicenseDetailsResult.Error("Network error: ${e.message}")
                LicenseValidationResult(false, "Network error: ${e.message}")
            }
        }
    }

    suspend fun getLicenseDetails(licenseKey: String): LicenseDetailsResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = makeApiCall(
                    action = "details", 
                    licenseKey = licenseKey
                )
                
                if (response.isBlank()) {
                    return@withContext LicenseDetailsResult.Error("Empty response from server")
                }

                try {
                    val jsonResponse = JSONObject(response)
                    if (jsonResponse.has("license_status") && jsonResponse.getBoolean("license_status")) {
                        return@withContext LicenseDetailsResult.Success(
                            activationDate = jsonResponse.optString("activation_date", "2024-01-01"),
                            validity = jsonResponse.optInt("validity", 365),
                            edition = jsonResponse.optString("edition", "Spire"),
                            capabilities = jsonResponse.optString("capabilities", "cap1, cap2"),
                            licensedTo = jsonResponse.optString("licensed_to", "MockCustomer")
                        )
                    } else {
                        return@withContext LicenseDetailsResult.Error("Response missing 'license_status' field: $response")
                    }
                } catch (e: JSONException) {
                    return@withContext LicenseDetailsResult.Error("Response is not valid JSON: $response")
                }
                
            } catch (e: Exception) {
                LicenseDetailsResult.Error("Network error: ${e.message}")
            }
        }
    }

    private suspend fun makeApiCall(
        action: String, 
        licenseKey: String, 
        deviceId: String? = null
    ): String {
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(API_URL)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true
                
                val postData = buildString {
                    append("license_key=").append(URLEncoder.encode(licenseKey, "UTF-8"))
                    append("&action=").append(URLEncoder.encode(action, "UTF-8"))
                    append("&api_key=").append(URLEncoder.encode(BuildConfig.LICENSE_API_KEY, "UTF-8"))
                    if (deviceId != null) {
                        append("&device_id=").append(URLEncoder.encode(deviceId, "UTF-8"))
                    }
                }
                
                conn.outputStream.use { it.write(postData.toByteArray()) }
                
                val responseCode = conn.responseCode
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val errorResponse = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    throw IOException("HTTP error: $responseCode - $errorResponse")
                }
            } finally {
                conn?.disconnect()
            }
        }
    }
}