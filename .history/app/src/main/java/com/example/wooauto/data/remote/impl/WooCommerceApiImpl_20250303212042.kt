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
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import com.example.wooauto.data.remote.ApiError

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
                Log.d("WooCommerceApiImpl", "【订单调试】执行GET请求 - endpoint: $endpoint")
                val baseUrl = buildBaseUrl()
                val urlBuilder = ("$baseUrl$endpoint").toHttpUrl().newBuilder()
                
                // 添加认证参数
                addAuthParams(urlBuilder)
                
                // 添加其他查询参数
                queryParams.forEach { (key, value) ->
                    urlBuilder.addQueryParameter(key, value)
                    Log.d("WooCommerceApiImpl", "【订单调试】添加查询参数: $key = $value")
                }
                
                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .get()
                    .build()
                
                val fullUrl = urlBuilder.build().toString()
                Log.d("WooCommerceApiImpl", "【订单调试】发送GET请求: $fullUrl")
                
                try {
                    val startTime = System.currentTimeMillis()
                    val response = client.newCall(request).execute()
                    val duration = System.currentTimeMillis() - startTime
                    
                    val responseBody = response.body?.string() ?: throw Exception("响应体为空")
                    
                    Log.d("WooCommerceApiImpl", "【订单调试】收到响应 - 状态码: ${response.code}, 耗时: ${duration}ms, 响应大小: ${responseBody.length} 字节")
                    
                    if (!response.isSuccessful) {
                        Log.e("WooCommerceApiImpl", "【订单调试】请求失败 - 状态码: ${response.code}, 响应: $responseBody")
                        throw ApiError.fromHttpCode(response.code, responseBody)
                    }
                    
                    // 记录响应的一部分，避免日志过大
                    val truncatedResponse = if (responseBody.length > 500) {
                        responseBody.substring(0, 500) + "... (截断, 总长度: ${responseBody.length})"
                    } else {
                        responseBody
                    }
                    Log.d("WooCommerceApiImpl", "【订单调试】响应内容: $truncatedResponse")
                    
                    try {
                        val result = gson.fromJson<T>(responseBody, object : TypeToken<T>() {}.type)
                        // 记录解析结果类型
                        Log.d("WooCommerceApiImpl", "【订单调试】成功解析响应为: ${T::class.java.simpleName ?: "未知类型"}")
                        return@withContext result
                    } catch (e: Exception) {
                        Log.e("WooCommerceApiImpl", "【订单调试】解析JSON失败: ${e.message}", e)
                        // 记录JSON解析失败的部分内容
                        Log.e("WooCommerceApiImpl", "【订单调试】无法解析的JSON: $truncatedResponse")
                        throw Exception("解析响应失败: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e("WooCommerceApiImpl", "【订单调试】执行请求时发生错误: ${e.message}", e)
                    throw e
                }
            } catch (e: Exception) {
                Log.e("WooCommerceApiImpl", "【订单调试】请求失败: ${e.message}", e)
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
                    Log.e("API错误", "HTTP错误: ${response.code}")
                    throw ApiError.fromHttpCode(response.code, "API错误: ${response.code}")
                }
                
                // 使用精确的类型解析方式
                val result = when {
                    T::class == OrderDto::class -> gson.fromJson(responseBody, OrderDto::class.java) as T
                    T::class == ProductDto::class -> gson.fromJson(responseBody, ProductDto::class.java) as T
                    T::class == CategoryDto::class -> gson.fromJson(responseBody, CategoryDto::class.java) as T
                    else -> gson.fromJson(responseBody, object : TypeToken<T>() {}.type)
                }
                
                result
            } catch (e: Exception) {
                Log.e("API错误", "POST请求失败: ${e.message}", e)
                
                if (e is ApiError) {
                    throw e
                } else {
                    throw ApiError.fromException(e)
                }
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
        Log.d("WooCommerceApiImpl", "【订单调试】开始获取订单列表 - 页码:$page, 每页数量:$perPage, 状态:${status ?: "全部"}")
        
        val queryParams = mutableMapOf(
            "page" to page.toString(),
            "per_page" to perPage.toString()
        )
        
        if (status != null) {
            queryParams["status"] = status
        }
        
        // 构建完整的API请求URL（仅用于日志）
        val baseUrl = buildBaseUrl()
        val endpoint = "orders"
        val fullUrl = buildRequestUrl(baseUrl, endpoint, queryParams)
        Log.d("WooCommerceApiImpl", "【订单调试】完整的API请求URL: $fullUrl")
        
        try {
            val result = executeGetRequest<List<OrderDto>>("orders", queryParams)
            Log.d("WooCommerceApiImpl", "【订单调试】API请求成功，返回 ${result.size} 个订单")
            return result
        } catch (e: Exception) {
            Log.e("WooCommerceApiImpl", "【订单调试】API请求失败: ${e.message}", e)
            // 打印更详细的错误信息
            if (e.message?.contains("response code") == true) {
                Log.e("WooCommerceApiImpl", "【订单调试】HTTP错误: ${e.message}")
            }
            throw e
        }
    }
    
    // 用于日志记录的辅助方法，构建完整URL
    private fun buildRequestUrl(baseUrl: String, endpoint: String, params: Map<String, String>): String {
        val url = StringBuilder(baseUrl)
        if (!url.endsWith("/")) url.append("/")
        url.append(endpoint)
        
        if (params.isNotEmpty()) {
            url.append("?")
            params.entries.forEachIndexed { index, entry ->
                if (index > 0) url.append("&")
                url.append("${entry.key}=${entry.value}")
            }
        }
        
        return url.toString()
    }
    
    override suspend fun getOrder(id: Long): OrderDto {
        return executeGetRequest("orders/$id")
    }
    
    override suspend fun updateOrder(id: Long, data: Map<String, Any>): OrderDto {
        return executePostRequest("orders/$id", data)
    }
} 