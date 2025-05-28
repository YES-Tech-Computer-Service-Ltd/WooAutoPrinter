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

    suspend fun validateLicense(licenseKey: String, deviceId: String): LicenseValidationResult = withContext(Dispatchers.IO) {
        try {
            val parameters = mapOf(
                "fslm_v2_api_request" to "verify",
                "fslm_api_key" to BuildConfig.LICENSE_API_KEY,
                "license_key" to licenseKey,
                "device_id" to deviceId
            )
            Log.d("LicenseValidator", "Sending validate request: licenseKey=$licenseKey, deviceId=$deviceId")
            Log.d("LicenseValidator", "API Key: ${BuildConfig.LICENSE_API_KEY}")
            Log.d("LicenseValidator", "API URL: $API_URL")
            
            val startTime = System.currentTimeMillis()
            val response = performPostRequest(parameters)
            val duration = System.currentTimeMillis() - startTime
            Log.d("LicenseValidator", "Validate response: $response")

            if (response.isBlank()) {
                Log.e("LicenseValidator", "Empty response from server")
                return@withContext LicenseValidationResult(false, "Empty response from server")
            }

            if (!isJsonValid(response)) {
                Log.e("LicenseValidator", "Response is not valid JSON: $response")
                return@withContext LicenseValidationResult(false, "Invalid response: not a valid JSON")
            }

            val json = JSONObject(response)
            if (!json.has("result")) {
                Log.e("LicenseValidator", "Response missing 'result' field: $response")
                return@withContext LicenseValidationResult(false, "Invalid response: missing 'result' field")
            }

            val result = json.getString("result")
            val message = json.optString("message", "No message provided")
            val success = result == "success"

            Log.d("LicenseValidator", "Validation result: success=$success, message=$message")
            LicenseValidationResult(success, message)
        } catch (e: Exception) {
            LicenseValidationResult(false, "Network error: ${e.message}")
        }
    }

    suspend fun activateLicense(licenseKey: String, deviceId: String): LicenseValidationResult = withContext(Dispatchers.IO) {
        try {
            val parameters = mapOf(
                "fslm_v2_api_request" to "activate",
                "fslm_api_key" to BuildConfig.LICENSE_API_KEY,
                "license_key" to licenseKey,
                "device_id" to deviceId
            )
            Log.d("LicenseValidator", "Sending activate request: licenseKey=$licenseKey, deviceId=$deviceId")
            Log.d("LicenseValidator", "API Key: ${BuildConfig.LICENSE_API_KEY}")
            Log.d("LicenseValidator", "API URL: $API_URL")
            val response = performPostRequest(parameters)
            Log.d("LicenseValidator", "Activate response: $response")

            if (response.isBlank()) {
                Log.e("LicenseValidator", "Empty response from server")
                return@withContext LicenseValidationResult(false, "Empty response from server")
            }

            if (!isJsonValid(response)) {
                Log.e("LicenseValidator", "Response is not valid JSON: $response")
                return@withContext LicenseValidationResult(false, "Invalid response: not a valid JSON")
            }

            val json = JSONObject(response)
            if (!json.has("result")) {
                Log.e("LicenseValidator", "Response missing 'result' field: $response")
                return@withContext LicenseValidationResult(false, "Invalid response: missing 'result' field")
            }

            val result = json.getString("result")
            val message = json.optString("message", "No message provided")
            val success = result == "success"

            LicenseValidationResult(success, message)
        } catch (e: Exception) {
            Log.e("LicenseValidator", "Activation error: ${e.message}", e)
            LicenseValidationResult(false, "Network error: ${e.message}")
        }
    }

    suspend fun getLicenseDetails(licenseKey: String): LicenseDetailsResult = withContext(Dispatchers.IO) {
        try {
            val parameters = mapOf(
                "fslm_v2_api_request" to "details",
                "fslm_api_key" to BuildConfig.LICENSE_API_KEY,
                "license_key" to licenseKey
            )
            Log.d("LicenseValidator", "Sending details request: licenseKey=$licenseKey")
            val response = performPostRequest(parameters)
            Log.d("LicenseValidator", "Details response: $response")

            if (response.isBlank()) {
                Log.e("LicenseValidator", "Empty response from server")
                return@withContext LicenseDetailsResult.Error("Empty response from server")
            }

            if (!isJsonValid(response)) {
                Log.e("LicenseValidator", "Response is not valid JSON: $response")
                return@withContext LicenseDetailsResult.Error("Invalid response: not a valid JSON")
            }

            val json = JSONObject(response)
            if (!json.has("license_status")) {
                Log.e("LicenseValidator", "Response missing 'license_status' field: $response")
                return@withContext LicenseDetailsResult.Error("Invalid response: missing 'license_status' field")
            }

            val status = json.getString("license_status")
            if (status.equals("Active", ignoreCase = true)) {
                val activationDate = json.optString("activation_date", "")
                val validityStr = json.optString("valid", "3")
                val validity = validityStr.toIntOrNull() ?: 3
                val edition = json.optString("license_edition", "Spire")
                val capabilities = json.optString("capabilities", "cap1, cap2")
                val licensedTo = json.optString("licensed_to", "MockCustomer")
                LicenseDetailsResult.Success(activationDate, validity, edition, capabilities, licensedTo)
            } else {
                val message = "License status: $status"
                LicenseDetailsResult.Error(message)
            }
        } catch (e: Exception) {
            Log.e("LicenseValidator", "Details error: ${e.message}", e)
            LicenseDetailsResult.Error("Network error: ${e.message}")
        }
    }

    private suspend fun performPostRequest(parameters: Map<String, String>): String = withContext(Dispatchers.IO) {
        val url = URL(API_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("User-Agent", System.getProperty("http.agent") ?: "Android")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        val postData = parameters.map { (key, value) ->
            "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
        }.joinToString("&")
        Log.d("LicenseValidator", "Post data: $postData")

        conn.outputStream.use { os: OutputStream ->
            os.write(postData.toByteArray(StandardCharsets.UTF_8))
        }

        val responseCode = conn.responseCode
        Log.d("LicenseValidator", "Response code: $responseCode")
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

        conn.disconnect()
        response
    }

    private fun isJsonValid(jsonString: String): Boolean {
        return try {
            JSONObject(jsonString)
            true
        } catch (e: Exception) {
            false
        }
    }
}