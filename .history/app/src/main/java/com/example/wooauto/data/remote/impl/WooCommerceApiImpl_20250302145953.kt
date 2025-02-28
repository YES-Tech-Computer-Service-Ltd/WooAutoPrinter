package com.example.wooauto.data.remote.impl

import android.util.Log
import com.example.wooauto.data.remote.WooCommerceApi
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.data.remote.adapters.CategoryDtoTypeAdapter
import com.example.wooauto.data.remote.adapters.OrderDtoTypeAdapter
import com.example.wooauto.data.remote.adapters.ProductDtoTypeAdapter
import com.example.wooauto.data.remote.dto.CategoryDto
import com.example.wooauto.data.remote.dto.OrderDto
import com.example.wooauto.data.remote.dto.ProductDto
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class WooCommerceApiImpl(
    private val config: WooCommerceConfig
) : WooCommerceApi {
    
    private val gson = GsonBuilder()
        .registerTypeAdapter(OrderDto::class.java, OrderDtoTypeAdapter())
        .registerTypeAdapter(ProductDto::class.java, ProductDtoTypeAdapter())
        .registerTypeAdapter(CategoryDto::class.java, CategoryDtoTypeAdapter())
        .create()
    
    private val client: OkHttpClient
    
    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    private fun buildBaseUrl(): String {
        val baseUrl = config.siteUrl.trim()
        return if (baseUrl.endsWith("/")) {
            "${baseUrl}wp-json/wc/v3/"
        } else {
            "$baseUrl/wp-json/wc/v3/"
        }
    }
    
    private fun addAuthParams(urlBuilder: okhttp3.HttpUrl.Builder) {
        // 添加日志以检查认证信息
        Log.d("WooCommerceApiImpl", "添加认证参数，使用Key: ${config.consumerKey}，Secret长度: ${config.consumerSecret.length}")
        urlBuilder.addQueryParameter("consumer_key", config.consumerKey)
        urlBuilder.addQueryParameter("consumer_secret", config.consumerSecret)
    }
    
    private suspend inline fun <reified T> executeGetRequest(endpoint: String, queryParams: Map<String, String> = emptyMap()): T {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = buildBaseUrl()
                val urlBuilder = ("$baseUrl$endpoint").toHttpUrl().newBuilder()
                
                // 添加认证参数
                addAuthParams(urlBuilder)
                
                // 添加其他查询参数
                queryParams.forEach { (key, value) ->
                    urlBuilder.addQueryParameter(key, value)
                }
                
                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .get()
                    .build()
                
                Log.d("API请求", "GET ${urlBuilder.build()}")
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw Exception("响应体为空")
                
                if (!response.isSuccessful) {
                    throw Exception("API错误: ${response.code} - $responseBody")
                }
                
                // 使用泛型信息更加精确地解析
                val result = when (T::class.java) {
                    OrderDto::class.java -> gson.fromJson(responseBody, OrderDto::class.java) as T
                    ProductDto::class.java -> gson.fromJson(responseBody, ProductDto::class.java) as T
                    CategoryDto::class.java -> gson.fromJson(responseBody, CategoryDto::class.java) as T
                    List::class.java -> {
                        if (endpoint.contains("orders")) {
                            gson.fromJson<List<OrderDto>>(responseBody, object : com.google.gson.reflect.TypeToken<List<OrderDto>>() {}.type) as T
                        } else if (endpoint.contains("products/categories")) {
                            gson.fromJson<List<CategoryDto>>(responseBody, object : com.google.gson.reflect.TypeToken<List<CategoryDto>>() {}.type) as T
                        } else if (endpoint.contains("products")) {
                            gson.fromJson<List<ProductDto>>(responseBody, object : com.google.gson.reflect.TypeToken<List<ProductDto>>() {}.type) as T
                        } else {
                            gson.fromJson(responseBody, T::class.java)
                        }
                    }
                    else -> gson.fromJson(responseBody, T::class.java)
                }
                
                result
            } catch (e: Exception) {
                Log.e("API错误", "GET请求失败: ${e.message}", e)
                throw e
            }
        }
    }
    
    private suspend inline fun <reified T> executePostRequest(endpoint: String, body: Map<String, Any>): T {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = buildBaseUrl()
                val urlBuilder = ("$baseUrl$endpoint").toHttpUrl().newBuilder()
                
                // 添加认证参数
                addAuthParams(urlBuilder)
                
                val jsonBody = gson.toJson(body)
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .post(requestBody)
                    .build()
                
                Log.d("API请求", "POST ${urlBuilder.build()} - $jsonBody")
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw Exception("响应体为空")
                
                if (!response.isSuccessful) {
                    throw Exception("API错误: ${response.code} - $responseBody")
                }
                
                // 使用泛型信息更加精确地解析
                val result = when (T::class.java) {
                    OrderDto::class.java -> gson.fromJson(responseBody, OrderDto::class.java) as T
                    ProductDto::class.java -> gson.fromJson(responseBody, ProductDto::class.java) as T
                    CategoryDto::class.java -> gson.fromJson(responseBody, CategoryDto::class.java) as T
                    else -> gson.fromJson(responseBody, T::class.java)
                }
                
                result
            } catch (e: Exception) {
                Log.e("API错误", "POST请求失败: ${e.message}", e)
                throw e
            }
        }
    }
    
    override suspend fun getProducts(page: Int, perPage: Int): List<ProductDto> {
        val queryParams = mapOf(
            "page" to page.toString(),
            "per_page" to perPage.toString()
        )
        
        return executeGetRequest("products", queryParams)
    }
    
    override suspend fun getProduct(id: Long): ProductDto {
        return executeGetRequest("products/$id")
    }
    
    override suspend fun updateProduct(id: Long, data: Map<String, Any>): ProductDto {
        return executePostRequest("products/$id", data)
    }
    
    override suspend fun getCategories(page: Int, perPage: Int): List<CategoryDto> {
        val queryParams = mapOf(
            "page" to page.toString(),
            "per_page" to perPage.toString()
        )
        
        return executeGetRequest("products/categories", queryParams)
    }
    
    override suspend fun getCategory(id: Long): CategoryDto {
        return executeGetRequest("products/categories/$id")
    }
    
    override suspend fun getOrders(page: Int, perPage: Int, status: String?): List<OrderDto> {
        val queryParams = mutableMapOf(
            "page" to page.toString(),
            "per_page" to perPage.toString()
        )
        
        if (status != null) {
            queryParams["status"] = status
        }
        
        return executeGetRequest("orders", queryParams)
    }
    
    override suspend fun getOrder(id: Long): OrderDto {
        return executeGetRequest("orders/$id")
    }
    
    override suspend fun updateOrder(id: Long, data: Map<String, Any>): OrderDto {
        return executePostRequest("orders/$id", data)
    }
} 