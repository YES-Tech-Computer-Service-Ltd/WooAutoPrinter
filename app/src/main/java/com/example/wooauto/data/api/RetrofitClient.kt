package com.example.wooauto.data.api

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val TIMEOUT = 30L
    private var retrofit: Retrofit? = null

    private const val TAG = "RetrofitClient_DEBUG"

    fun getWooCommerceApiService(baseUrl: String): WooCommerceApiService {
        Log.d(TAG, "创建 WooCommerce API 服务: $baseUrl")
        if (retrofit == null || retrofit?.baseUrl()?.toString() != baseUrl) {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d(TAG, "OkHttp: $message")
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build()

            val gson = GsonBuilder()
                .registerTypeAdapter(Date::class.java, DateDeserializer())
                .create()

            // 确保 baseUrl 格式正确
            val apiUrl = baseUrl.trim()
                .replace("/$", "") // 移除末尾的斜杠
                .replace("/wp-json/wc/v3/?$", "") // 移除已存在的API路径

            // 确保 URL 包含 scheme
            val fullUrl = when {
                apiUrl.startsWith("http://") || apiUrl.startsWith("https://") -> "$apiUrl/wp-json/wc/v3/"
                else -> "https://$apiUrl/wp-json/wc/v3/"
            }

            Log.d(TAG, "构建 Retrofit 实例, 最终URL: $fullUrl")
            retrofit = Retrofit.Builder()
                .baseUrl(fullUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
        }

        return retrofit!!.create(WooCommerceApiService::class.java)
    }

    private class DateDeserializer : JsonDeserializer<Date> {
        private val dateFormats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): Date? {
            val dateString = json.asString

            for (format in dateFormats) {
                try {
                    val dateFormat = SimpleDateFormat(format, Locale.getDefault())
                    return dateFormat.parse(dateString)
                } catch (e: Exception) {
                    // Try next format
                }
            }

            return null
        }
    }
}