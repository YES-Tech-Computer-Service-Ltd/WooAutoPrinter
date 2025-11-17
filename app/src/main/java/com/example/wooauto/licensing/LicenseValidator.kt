package com.example.wooauto.licensing

import android.util.Log
import com.example.wooauto.BuildConfig
import com.example.wooauto.utils.UiLog
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
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
        val licensedTo: String = "MockCustomer",
        val email: String = "user@example.com"
    ) : LicenseDetailsResult()
    data class Error(val message: String) : LicenseDetailsResult()
}

object LicenseValidator {
    private const val API_URL = "https://yestech.ca/"
    private val LICENSE_HOST: String by lazy { URL(API_URL).host }
    private val revocationDisabledSslSocketFactory: SSLSocketFactory by lazy {
        UiLog.d("LicenseValidator", "Initializing custom SSLSocketFactory (revocation disabled) for $LICENSE_HOST")
        buildRevocationDisabledSocketFactory()
    }

    suspend fun validateLicense(licenseKey: String, deviceId: String): LicenseValidationResult = withContext(Dispatchers.IO) {
        try {
            val parameters = mapOf(
                "fslm_v2_api_request" to "verify",
                "fslm_api_key" to BuildConfig.LICENSE_API_KEY,
                "license_key" to licenseKey,
                "device_id" to deviceId
            )
            UiLog.d("LicenseValidator", "Sending validate request: licenseKey=${licenseKey.take(4)}..., deviceId=$deviceId")
            UiLog.d("LicenseValidator", "API Key: ${BuildConfig.LICENSE_API_KEY.take(4)}...")
            UiLog.d("LicenseValidator", "API URL: $API_URL")
            
            val startTime = System.currentTimeMillis()
            val response = performPostRequest(parameters)
            val duration = System.currentTimeMillis() - startTime
            UiLog.d("LicenseValidator", "Validate response: $response")

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

            UiLog.d("LicenseValidator", "Validation result: success=$success, message=$message")
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
            UiLog.d("LicenseValidator", "Sending activate request: licenseKey=${licenseKey.take(4)}..., deviceId=$deviceId")
            UiLog.d("LicenseValidator", "API Key: ${BuildConfig.LICENSE_API_KEY.take(4)}...")
            UiLog.d("LicenseValidator", "API URL: $API_URL")
            val response = performPostRequest(parameters)
            UiLog.d("LicenseValidator", "Activate response: $response")

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
            UiLog.d("LicenseValidator", "Sending details request: licenseKey=${licenseKey.take(4)}...")
            val response = performPostRequest(parameters)
            UiLog.d("LicenseValidator", "Details response: $response")

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
            UiLog.d("LicenseValidator", "License status: $status")
            
            if (status.equals("sold", ignoreCase = true) || status.equals("active", ignoreCase = true)) {
                val activationDate = json.optString("activation_date", "")
                val creationDate = json.optString("creation_date", "")
                val expirationDate = json.optString("expiration_date", "")
                
                val validity = try {
                    // 首先尝试直接使用API返回的valid字段
                    val validDays = json.optString("valid", "")
                    if (validDays.isNotEmpty() && validDays != "0") {
                        val days = validDays.toIntOrNull()
                        if (days != null && days > 0) {
                            UiLog.d("LicenseValidator", "使用API返回的valid字段: ${days}天")
                            days
                        } else {
                            // 如果valid字段无效，检查是否为永久许可证
                            if (expirationDate == "0000-00-00" || expirationDate.isEmpty()) {
                                UiLog.d("LicenseValidator", "检测到永久许可证(expiration_date=0000-00-00)")
                                3650 // 永久许可证设为10年
                            } else {
                                365 // 默认1年
                            }
                        }
                    } else if (expirationDate == "0000-00-00" || expirationDate.isEmpty()) {
                        UiLog.d("LicenseValidator", "检测到永久许可证(无valid字段)")
                        3650 // 永久许可证设为10年
                    } else if (expirationDate.isNotEmpty() && creationDate.isNotEmpty()) {
                        // 作为备选方案，计算expiration_date - creation_date
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        val endDate = sdf.parse(expirationDate)
                        val startDate = sdf.parse(creationDate)
                        if (endDate != null && startDate != null) {
                            val diffInMillis = endDate.time - startDate.time
                            val diffInDays = (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
                            UiLog.d("LicenseValidator", "计算得到的有效期: ${diffInDays}天")
                            if (diffInDays > 0) diffInDays else 365
                        } else 365
                    } else {
                        UiLog.d("LicenseValidator", "使用默认有效期: 365天")
                        365 // 默认一年有效期
                    }
                } catch (e: Exception) {
                    Log.w("LicenseValidator", "Failed to calculate validity period: ${e.message}")
                    365
                }
                
                val firstName = json.optString("owner_first_name", "")
                val lastName = json.optString("owner_last_name", "")
                val ownerEmail = json.optString("owner_email_address", "user@example.com")
                
                val licensedTo = when {
                    firstName.isNotEmpty() && lastName.isNotEmpty() -> "$firstName $lastName"
                    firstName.isNotEmpty() -> firstName
                    lastName.isNotEmpty() -> lastName
                    else -> "Licensed User"
                }
                
                val edition = "Pro"
                val capabilities = "Full Features"
                
                UiLog.d("LicenseValidator", "Parsed license details: licensedTo=$licensedTo, email=$ownerEmail, validity=$validity days")
                
                LicenseDetailsResult.Success(
                    activationDate = activationDate.ifEmpty { creationDate },
                    validity = validity,
                    edition = edition,
                    capabilities = capabilities,
                    licensedTo = licensedTo,
                    email = ownerEmail
                )
            } else {
                val message = "License status: $status (Expected: sold or active)"
                Log.w("LicenseValidator", message)
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
        if (conn is HttpsURLConnection && url.host.equals(LICENSE_HOST, ignoreCase = true)) {
            try {
                conn.sslSocketFactory = revocationDisabledSslSocketFactory
                val defaultVerifier: HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
                conn.hostnameVerifier = HostnameVerifier { hostname, session ->
                    val result = defaultVerifier.verify(hostname, session)
                    UiLog.d("LicenseValidator", "Hostname verify: host=$hostname, peerHost=${session?.peerHost}, result=$result")
                    result
                }
                UiLog.d("LicenseValidator", "Applied custom SSL (revocation disabled) to $LICENSE_HOST")
            } catch (e: Exception) {
                Log.w("LicenseValidator", "Failed to apply custom SSL, fallback to default: ${e.message}", e)
            }
        }
        conn.requestMethod = "POST"
        conn.setRequestProperty("User-Agent", System.getProperty("http.agent") ?: "Android")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        val postData = parameters.map { (key, value) ->
            "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
        }.joinToString("&")
        UiLog.d("LicenseValidator", "Post data: $postData")

        conn.outputStream.use { os: OutputStream ->
            os.write(postData.toByteArray(StandardCharsets.UTF_8))
        }

        val responseCode = conn.responseCode
        UiLog.d("LicenseValidator", "Response code: $responseCode")
        UiLog.d("LicenseValidator", "Response message: ${conn.responseMessage}")

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

    private fun buildRevocationDisabledSocketFactory(): SSLSocketFactory {
        val trustManagers = arrayOf<TrustManager>(RevocationDisabledTrustManager())
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustManagers, SecureRandom())
        return sslContext.socketFactory
    }

    private class RevocationDisabledTrustManager : X509TrustManager {
        private val systemTrustAnchors: Set<TrustAnchor> by lazy { loadSystemTrustAnchors() }
        private val acceptedIssuersArray: Array<X509Certificate> by lazy { loadAcceptedIssuers() }

        override fun getAcceptedIssuers(): Array<X509Certificate> = acceptedIssuersArray

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            // 不用于客户端证书校验
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            UiLog.d("LicenseValidator", "TLS checkServerTrusted: authType=$authType, chainSize=${chain.size}")
            require(chain.isNotEmpty()) { "Empty server certificate chain" }

            try {
                chain.forEachIndexed { idx, cert ->
                    cert.checkValidity()
                    UiLog.d("LicenseValidator", "Cert[$idx] Subject=${cert.subjectX500Principal.name}")
                }

                val cf = CertificateFactory.getInstance("X.509")
                val certPath = cf.generateCertPath(chain.toList())

                if (systemTrustAnchors.isEmpty()) {
                    throw IllegalStateException("System trust anchors are empty")
                }

                val params = PKIXParameters(systemTrustAnchors).apply {
                    isRevocationEnabled = false
                }

                val validator = CertPathValidator.getInstance("PKIX")
                val result = validator.validate(certPath, params)
                UiLog.d("LicenseValidator", "PKIX validation passed (revocation disabled): $result")
            } catch (e: Exception) {
                Log.e("LicenseValidator", "PKIX validation failed (revocation disabled): ${e.message}", e)
                throw e
            }
        }

        private fun loadSystemTrustAnchors(): Set<TrustAnchor> {
            return try {
                val ks = KeyStore.getInstance("AndroidCAStore")
                ks.load(null)
                val anchors = mutableSetOf<TrustAnchor>()
                val aliases = ks.aliases()
                while (aliases.hasMoreElements()) {
                    val alias = aliases.nextElement()
                    val cert = ks.getCertificate(alias)
                    if (cert is X509Certificate) {
                        anchors.add(TrustAnchor(cert, null))
                    }
                }
                UiLog.d("LicenseValidator", "Loaded system trust anchors: ${anchors.size}")
                anchors
            } catch (e: Exception) {
                Log.e("LicenseValidator", "Failed to load AndroidCAStore: ${e.message}", e)
                emptySet()
            }
        }

        private fun loadAcceptedIssuers(): Array<X509Certificate> {
            return try {
                val ks = KeyStore.getInstance("AndroidCAStore")
                ks.load(null)
                val list = mutableListOf<X509Certificate>()
                val aliases = ks.aliases()
                while (aliases.hasMoreElements()) {
                    val alias = aliases.nextElement()
                    val cert = ks.getCertificate(alias)
                    if (cert is X509Certificate) {
                        list.add(cert)
                    }
                }
                list.toTypedArray()
            } catch (e: Exception) {
                Log.w("LicenseValidator", "Failed to load accepted issuers from AndroidCAStore: ${e.message}")
                emptyArray()
            }
        }
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