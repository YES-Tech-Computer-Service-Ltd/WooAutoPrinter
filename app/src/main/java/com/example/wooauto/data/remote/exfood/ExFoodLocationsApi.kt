package com.example.wooauto.data.remote.exfood

import com.example.wooauto.data.remote.ApiError
import com.example.wooauto.utils.UrlNormalizer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.reflect.Type

/**
 * ExFood (WooCommerce Food) API - Locations (multi-store)
 *
 * Endpoint (per api-documentation.md):
 * - GET /wp-json/exfood-api/v1/locations
 *
 * Response:
 * [
 *   { "id": 123, "name": "...", "slug": "...", "address": "..." }
 * ]
 */
object ExFoodLocationsApi {

    private const val LOCATIONS_PATH = "wp-json/exfood-api/v1/locations"

    fun buildLocationsUrl(siteUrl: String): String {
        val base = UrlNormalizer.sanitizeSiteUrl(siteUrl)
        return if (base.isBlank()) "" else "$base/$LOCATIONS_PATH"
    }

    suspend fun getLocations(
        client: OkHttpClient,
        gson: Gson,
        siteUrl: String
    ): List<ExFoodLocation> = withContext(Dispatchers.IO) {
        val url = buildLocationsUrl(siteUrl)
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
            val type: Type = object : TypeToken<List<ExFoodLocation>>() {}.type
            gson.fromJson(body, type)
        }
    }
}

data class ExFoodLocation(
    val id: Int,
    val name: String,
    val slug: String,
    val address: String? = null
)


