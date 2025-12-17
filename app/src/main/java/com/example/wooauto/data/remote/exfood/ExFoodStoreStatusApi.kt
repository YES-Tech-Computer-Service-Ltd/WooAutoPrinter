package com.example.wooauto.data.remote.exfood

import com.example.wooauto.data.remote.ApiError
import com.example.wooauto.utils.UrlNormalizer
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ExFood (WooCommerce Food) API - Store status (open/close)
 *
 * Endpoints (per api-documentation.md):
 * - GET  /wp-json/exfood-api/v1/store-status
 * - POST /wp-json/exfood-api/v1/store-status   body: {"status":"closed|enable|disable"}
 *
 * Note:
 * - This app's OkHttpClient adds WooCommerce auth params via interceptor (consumer_key/consumer_secret).
 * - We keep this wrapper thin and UI-driven (no polling).
 */
object ExFoodStoreStatusApi {

    const val STATUS_ENABLE = "enable"   // follow open hours
    const val STATUS_DISABLE = "disable" // always open (ignore open hours)
    const val STATUS_CLOSED = "closed"   // always closed

    private const val STORE_STATUS_PATH = "wp-json/exfood-api/v1/store-status"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun buildStoreStatusUrl(siteUrl: String): String {
        val base = UrlNormalizer.sanitizeSiteUrl(siteUrl)
        return if (base.isBlank()) "" else "$base/$STORE_STATUS_PATH"
    }

    suspend fun getStoreStatus(
        client: OkHttpClient,
        gson: Gson,
        siteUrl: String
    ): StoreStatusResponse = withContext(Dispatchers.IO) {
        val url = buildStoreStatusUrl(siteUrl)
        require(url.isNotBlank()) { "siteUrl is blank" }

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiError.fromHttpCode(response.code, body)
            }
            gson.fromJson(body, StoreStatusResponse::class.java)
        }
    }

    suspend fun updateStoreStatus(
        client: OkHttpClient,
        gson: Gson,
        siteUrl: String,
        status: String
    ): UpdateStoreStatusResponse = withContext(Dispatchers.IO) {
        val url = buildStoreStatusUrl(siteUrl)
        require(url.isNotBlank()) { "siteUrl is blank" }

        val jsonBody = gson.toJson(UpdateStoreStatusRequest(status = status))
        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiError.fromHttpCode(response.code, body)
            }
            val parsed = gson.fromJson(body, UpdateStoreStatusResponse::class.java)
            if (!parsed.success) {
                throw ApiError.UnknownError(0, parsed.message ?: "Store status update failed")
            }
            parsed
        }
    }
}

data class StoreStatusResponse(
    val status: String?,
    @SerializedName("raw_value")
    val rawValue: String?
)

data class UpdateStoreStatusRequest(
    val status: String
)

data class UpdateStoreStatusResponse(
    val success: Boolean,
    val message: String?,
    @SerializedName("new_status")
    val newStatus: String?,
    @SerializedName("internal_value")
    val internalValue: String?
)


