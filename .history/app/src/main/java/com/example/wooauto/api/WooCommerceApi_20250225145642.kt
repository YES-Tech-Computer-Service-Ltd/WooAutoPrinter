package com.example.wooauto.api

import android.content.Context
import com.example.wooauto.data.Order
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.*

class WooCommerceApi(
    context: Context,
    private val apiKey: String,
    private val apiSecret: String
) {
    private val baseUrl = "https://your-store-url.com/wp-json/wc/v3/"
    private val service: WooCommerceService

    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url.newBuilder()
                    .addQueryParameter("consumer_key", apiKey)
                    .addQueryParameter("consumer_secret", apiSecret)
                    .build()

                val request = original.newBuilder()
                    .url(url)
                    .build()

                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        service = retrofit.create(WooCommerceService::class.java)
    }

    suspend fun getNewOrders(after: Date?): List<Order> {
        val dateStr = after?.let { 
            android.text.format.DateFormat.format("yyyy-MM-dd'T'HH:mm:ss", it).toString()
        }
        return service.getOrders(dateStr)
    }

    interface WooCommerceService {
        @GET("orders")
        suspend fun getOrders(
            @Query("after") after: String? = null,
            @Query("status") status: String = "processing",
            @Query("per_page") perPage: Int = 100
        ): List<Order>
    }
} 