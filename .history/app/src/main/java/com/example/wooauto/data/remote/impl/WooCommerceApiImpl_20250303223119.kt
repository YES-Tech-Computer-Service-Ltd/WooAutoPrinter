package com.example.wooauto.data.remote.impl

import android.util.Log
import com.example.wooauto.data.remote.WooCommerceApi
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.data.remote.adapters.CategoryDtoTypeAdapter
import com.example.wooauto.data.remote.adapters.OrderDtoTypeAdapter
import com.example.wooauto.data.remote.adapters.OrderDtoListTypeAdapter
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
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class WooCommerceApiImpl(
    private val config: WooCommerceConfig
) : WooCommerceApi {
    
    // 创建一个List<OrderDto>的Type，用于注册类型适配器
    private val orderDtoListType = object : TypeToken<List<OrderDto>>() {}.type
    
    private val gson = GsonBuilder()
        .registerTypeAdapter(OrderDto::class.java, OrderDtoTypeAdapter())
        .registerTypeAdapter(orderDtoListType, OrderDtoListTypeAdapter())
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
                        // 确定要使用的类型和解析策略
                        val isOrdersList = T::class.java == List::class.java && endpoint.contains("orders")
                        if (isOrdersList) {
                            Log.d("WooCommerceApiImpl", "【订单调试】使用订单列表专用适配器")
                        }
                        
                        // 使用注册了自定义适配器的gson直接解析
                        val result = when {
                            isOrdersList -> {
                                // 订单列表使用我们注册的专用适配器
                                gson.fromJson<T>(responseBody, orderDtoListType)
                            }
                            else -> {
                                // 其他类型使用标准解析
                                gson.fromJson<T>(responseBody, object : TypeToken<T>() {}.type)
                            }
                        }
                        
                        // 记录解析结果信息
                        when (result) {
                            is List<*> -> {
                                Log.d("WooCommerceApiImpl", "【订单调试】成功解析列表数据，元素数量: ${result.size}")
                            }
                            else -> {
                                Log.d("WooCommerceApiImpl", "【订单调试】成功解析单个对象: ${result?.javaClass?.simpleName}")
                            }
                        }
                        
                        return@withContext result
                    } catch (e: Exception) {
                        // 记录详细的JSON解析错误
                        val errorMsg = when (e) {
                            is NumberFormatException -> {
                                // 针对数字格式异常进行特殊处理
                                Log.e("WooCommerceApiImpl", "【订单调试】数字格式错误: ${e.message}", e)
                                "数字格式错误: ${e.message}"
                            }
                            else -> {
                                Log.e("WooCommerceApiImpl", "【订单调试】JSON解析错误: ${e.javaClass.simpleName} - ${e.message}", e)
                                e.message
                            }
                        }
                        
                        // 记录JSON解析失败的部分内容
                        Log.e("WooCommerceApiImpl", "【订单调试】无法解析的JSON: $truncatedResponse")
                        
                        // 针对不同类型的数据解析失败，尝试进行恢复
                        if (endpoint.contains("orders") && T::class.java == List::class.java) {
                            Log.w("WooCommerceApiImpl", "【订单调试】尝试使用空列表作为备用返回值")
                            try {
                                // 对于订单列表，在解析失败时返回空列表而非抛出异常
                                @Suppress("UNCHECKED_CAST")
                                return@withContext emptyList<OrderDto>() as T
                            } catch (e: Exception) {
                                Log.e("WooCommerceApiImpl", "【订单调试】返回空列表失败: ${e.message}")
                            }
                        }
                        
                        throw Exception("解析响应失败: ${errorMsg}")
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
            val startTime = System.currentTimeMillis()
            val result = executeGetRequest<List<OrderDto>>("orders", queryParams)
            val duration = System.currentTimeMillis() - startTime
            Log.d("WooCommerceApiImpl", "【订单调试】API请求成功，耗时 ${duration}ms，返回 ${result.size} 个订单")
            
            // 记录订单ID，帮助调试
            if (result.isNotEmpty()) {
                val orderIds = result.take(5).map { it.id }.joinToString(", ")
                Log.d("WooCommerceApiImpl", "【订单调试】订单ID示例: $orderIds${if (result.size > 5) "..." else ""}")
            }
            
            return result
        } catch (e: Exception) {
            Log.e("WooCommerceApiImpl", "【订单调试】API请求失败: ${e.message}", e)
            
            // 分析错误类型并记录特定的错误信息
            val errorDetails = when {
                e.message?.contains("response code") == true -> "HTTP错误: ${e.message}"
                e.message?.contains("NumberFormatException") == true -> "数字格式错误: ${e.message}"
                e.message?.contains("解析响应失败") == true -> "JSON解析错误: ${e.message}"
                else -> "未知错误: ${e.message}"
            }
            
            Log.e("WooCommerceApiImpl", "【订单调试】错误详情: $errorDetails")
            
            // 如果是空列表的情况下的解析错误，尝试返回空列表
            if (e.message?.contains("解析响应失败") == true) {
                Log.w("WooCommerceApiImpl", "【订单调试】JSON解析失败，返回空列表")
                return emptyList()
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
    
    // 添加一个备用方法，在标准解析失败时尝试手动解析JSON提取订单数据
    private fun extractOrdersManually(jsonString: String): List<OrderDto> {
        Log.d("WooCommerceApiImpl", "【订单调试】尝试手动解析订单数据")
        val orders = mutableListOf<OrderDto>()
        
        try {
            // 使用Gson解析为最基本的JsonArray，避免强类型转换
            val jsonArray = gson.fromJson(jsonString, com.google.gson.JsonArray::class.java)
            
            var successCount = 0
            var failCount = 0
            
            // 遍历每个JsonElement，尝试解析为OrderDto
            for (element in jsonArray) {
                try {
                    if (element.isJsonObject) {
                        val jsonObject = element.asJsonObject
                        
                        // 提取基本字段
                        val id = try {
                            val idElement = jsonObject.get("id")
                            when {
                                idElement.isJsonNull -> 0L
                                idElement.isJsonPrimitive -> {
                                    val primitive = idElement.asJsonPrimitive
                                    if (primitive.isNumber) primitive.asLong
                                    else if (primitive.isString) {
                                        val str = primitive.asString
                                        if (str.isBlank()) 0L else str.toLongOrNull() ?: 0L
                                    } else 0L
                                }
                                else -> 0L
                            }
                        } catch (e: Exception) {
                            Log.e("WooCommerceApiImpl", "【订单调试】提取ID失败: ${e.message}")
                            0L
                        }
                        
                        // 提取其他基本字段
                        val number = try {
                            jsonObject.get("number")?.asString ?: ""
                        } catch (e: Exception) {
                            ""
                        }
                        
                        val status = try {
                            jsonObject.get("status")?.asString ?: ""
                        } catch (e: Exception) {
                            ""
                        }
                        
                        val dateCreated = try {
                            jsonObject.get("date_created")?.asString ?: ""
                        } catch (e: Exception) {
                            ""
                        }
                        
                        val dateModified = try {
                            jsonObject.get("date_modified")?.asString ?: ""
                        } catch (e: Exception) {
                            ""
                        }
                        
                        val total = try {
                            jsonObject.get("total")?.asString ?: "0.00"
                        } catch (e: Exception) {
                            "0.00"
                        }
                        
                        // 创建最小化的OrderDto对象
                        val orderDto = OrderDto(
                            id = id,
                            parentId = 0L,
                            number = number,
                            status = status,
                            dateCreated = dateCreated,
                            dateModified = dateModified,
                            total = total,
                            customer = null,
                            billingAddress = AddressDto("", "", "", "", "", "", "", "", "", ""),
                            shippingAddress = null,
                            lineItems = emptyList(),
                            paymentMethod = null,
                            paymentMethodTitle = null,
                            customerNote = null
                        )
                        
                        orders.add(orderDto)
                        successCount++
                    }
                } catch (e: Exception) {
                    failCount++
                    Log.e("WooCommerceApiImpl", "【订单调试】手动解析单个订单失败: ${e.message}")
                }
            }
            
            Log.d("WooCommerceApiImpl", "【订单调试】手动解析完成: 成功=${successCount}, 失败=${failCount}, 总计=${orders.size}")
            
        } catch (e: Exception) {
            Log.e("WooCommerceApiImpl", "【订单调试】手动解析全部失败: ${e.message}", e)
        }
        
        return orders
    }
} 