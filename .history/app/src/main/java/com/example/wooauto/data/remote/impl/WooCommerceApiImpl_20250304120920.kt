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
import com.example.wooauto.data.remote.dto.AddressDto
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
                val baseUrl = buildBaseUrl()
                // 确保不存在双斜杠
                val cleanEndpoint = if (endpoint.startsWith("/")) endpoint.substring(1) else endpoint
                val fullApiUrl = "$baseUrl$cleanEndpoint"
                Log.d("WooCommerceApiImpl", "【URL构建】基本URL: $baseUrl, 终端点: $cleanEndpoint")
                Log.d("WooCommerceApiImpl", "【URL构建】合并URL: $fullApiUrl")
                
                val urlBuilder = fullApiUrl.toHttpUrl().newBuilder()
                
                // 添加认证参数
                addAuthParams(urlBuilder)
                
                // 添加其他查询参数
                queryParams.forEach { (key, value) ->
                    Log.d("WooCommerceApiImpl", "【URL构建】添加参数: $key=$value")
                    urlBuilder.addQueryParameter(key, value)
                }
                
                val fullUrl = urlBuilder.build().toString()
                Log.d("WooCommerceApiImpl", "【URL构建】最终URL: $fullUrl")
                
                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .get()
                    .build()
                
                val startTime = System.currentTimeMillis()
                val response = client.newCall(request).execute()
                val duration = System.currentTimeMillis() - startTime
                
                val responseBody = response.body?.string() ?: throw Exception("响应体为空")
                
                if (endpoint.contains("orders")) {
                    Log.d("WooCommerceApiImpl", "【状态请求】响应状态码: ${response.code}, 耗时: ${duration}ms, 大小: ${responseBody.length}字节")
                }
                
                if (!response.isSuccessful) {
                    Log.e("WooCommerceApiImpl", "请求失败 - 状态码: ${response.code}, 响应: $responseBody")
                    throw ApiError.fromHttpCode(response.code, responseBody)
                }
                
                try {
                    // 确定要使用的类型和解析策略
                    val isOrdersList = T::class.java == List::class.java && endpoint.contains("orders")
                    
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
                    
                    // 只为订单列表记录简要信息
                    if (isOrdersList && result is List<*>) {
                        val orderList = result as List<*>
                        if (endpoint.contains("orders")) {
                            Log.d("WooCommerceApiImpl", "【状态请求】成功解析订单数据，数量: ${orderList.size}")
                            
                            // 记录订单状态分布
                            if (orderList.isNotEmpty() && orderList[0] is OrderDto) {
                                val statusCounts = (orderList as List<OrderDto>).groupBy { it.status }
                                    .mapValues { it.value.size }
                                Log.d("WooCommerceApiImpl", "【状态请求】订单状态分布: $statusCounts")
                            }
                        }
                    }
                    
                    return@withContext result
                } catch (e: Exception) {
                    Log.e("WooCommerceApiImpl", "解析响应失败: ${e.message}")
                    throw Exception("解析响应失败: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e("WooCommerceApiImpl", "请求失败: ${e.message}")
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
        // 构建查询参数
        val queryParams = mutableMapOf(
            "page" to page.toString(),
            "per_page" to perPage.toString()
        )
        
        // 重要：确保状态参数正确添加到查询中
        if (!status.isNullOrBlank()) {
            // WooCommerce REST API 要求状态参数格式特殊
            // 错误信息表明参数被解释为数组形式 status[0]
            // 使用status=failed格式而不是带方括号的形式
            
            val cleanStatus = status.trim().toLowerCase()
            Log.d("WooCommerceApiImpl", "【状态请求】原始状态值: '$status', 清理后: '$cleanStatus'")
            
            // 直接添加为查询参数，不使用数组表示法
            queryParams["status"] = cleanStatus
            
            // 记录URL构建过程以便调试
            Log.d("WooCommerceApiImpl", "【状态请求】最终查询参数: $queryParams")
        } else {
            Log.d("WooCommerceApiImpl", "【状态请求】获取所有状态的订单")
        }
        
        try {
            Log.d("WooCommerceApiImpl", "【状态请求】准备调用API，参数=$queryParams")
            
            // 构建完整URL以便日志记录
            val baseUrl = buildBaseUrl()
            val urlBuilder = ("${baseUrl}orders").toHttpUrl().newBuilder()
            addAuthParams(urlBuilder)
            queryParams.forEach { (key, value) ->
                urlBuilder.addQueryParameter(key, value)
            }
            val fullUrl = urlBuilder.build().toString()
            Log.d("WooCommerceApiImpl", "【状态请求】完整URL: $fullUrl")
            
            val result = executeGetRequest<List<OrderDto>>("orders", queryParams)
            Log.d("WooCommerceApiImpl", "【状态请求】成功获取 ${result.size} 个订单")
            return result
        } catch (e: Exception) {
            Log.e("WooCommerceApiImpl", "获取订单失败: ${e.message}")
            
            // 尝试进行备用请求方式
            if (e.message?.contains("Invalid parameter") == true && !status.isNullOrBlank()) {
                Log.w("WooCommerceApiImpl", "【状态请求】主要请求失败，尝试备用方式...")
                try {
                    // 在备用方法中尝试使用其他参数形式
                    val baseUrl = buildBaseUrl()
                    // 直接在URL路径中添加状态，而不是作为查询参数
                    // WooCommerce支持 /wp-json/wc/v3/orders?status=failed 或 /wp-json/wc/v3/orders/status/failed 两种形式
                    val endpoint = "orders"
                    
                    val alternateParams = mutableMapOf(
                        "page" to page.toString(),
                        "per_page" to perPage.toString()
                    )
                    
                    // 尝试仅使用指定的有效状态值
                    if (!status.isNullOrBlank()) {
                        val validStatus = when (status.trim().toLowerCase()) {
                            "failed" -> "failed"
                            "失败" -> "failed"
                            "processing" -> "processing"
                            "处理中" -> "processing"
                            "completed" -> "completed"
                            "已完成" -> "completed"
                            "pending" -> "pending"
                            "待付款" -> "pending"
                            "cancelled" -> "cancelled" 
                            "已取消" -> "cancelled"
                            "refunded" -> "refunded"
                            "已退款" -> "refunded"
                            "on-hold" -> "on-hold"
                            "暂挂" -> "on-hold"
                            else -> null
                        }
                        
                        if (validStatus != null) {
                            alternateParams["status"] = validStatus
                            Log.d("WooCommerceApiImpl", "【状态请求】备用状态值: '$validStatus'")
                        }
                    }
                    
                    Log.d("WooCommerceApiImpl", "【状态请求】备用参数: $alternateParams")
                    
                    // 构建备用URL供日志检查
                    val altUrlBuilder = ("${baseUrl}${endpoint}").toHttpUrl().newBuilder()
                    addAuthParams(altUrlBuilder)
                    alternateParams.forEach { (key, value) ->
                        altUrlBuilder.addQueryParameter(key, value)
                    }
                    val altFullUrl = altUrlBuilder.build().toString()
                    Log.d("WooCommerceApiImpl", "【状态请求】备用完整URL: $altFullUrl")
                    
                    val backupResult = executeGetRequest<List<OrderDto>>(endpoint, alternateParams)
                    Log.d("WooCommerceApiImpl", "【状态请求】备用请求成功，获取到 ${backupResult.size} 个订单")
                    return backupResult
                } catch (backupError: Exception) {
                    Log.e("WooCommerceApiImpl", "备用请求也失败: ${backupError.message}")
                    throw backupError
                }
            }
            
            throw e
        }
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
                            billingAddress = AddressDto(
                                firstName = "",
                                lastName = "",
                                company = "",
                                address_1 = "",
                                address_2 = "",
                                city = "",
                                state = "",
                                postcode = "",
                                country = "",
                                phone = ""
                            ),
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