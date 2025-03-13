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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import com.example.wooauto.data.remote.ApiError
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.math.pow
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import android.os.Build
import okhttp3.Interceptor
import javax.net.ssl.SSLSocketFactory
import java.security.KeyStore
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
    
    private lateinit var client: OkHttpClient
    
    // 添加typeRegistry字段
    private var typeRegistry: GsonTypeAdapterFactory? = null
    
    // 实现GsonTypeAdapterFactory类
    private class GsonTypeAdapterFactory(private val gson: com.google.gson.Gson) {
        fun <T> create(type: Type): T? {
            return try {
                gson.fromJson<T>("{}", type)
            } catch (e: Exception) {
                Log.e("GsonTypeAdapterFactory", "创建类型实例失败: ${e.message}")
                null
            }
        }
    }
    
    init {
        initializeClient()
        
        // 检查配置是否有效
        Log.d("WooCommerceApiImpl", "初始化WooCommerceApiImpl，配置: $config, " +
            "baseUrl: ${config.siteUrl}, 是否有效: ${config.isValid()}")
            
        if (!config.isValid()) {
            Log.w("WooCommerceApiImpl", "警告: WooCommerce配置无效，API请求可能会失败")
        }
    }
    
    private fun initializeClient() {
        Log.d("WooCommerceApiImpl", "开始初始化HTTP客户端")
        
        // 创建一个基本客户端用于初始状态
        val baseClient = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .build()
            
        // 先设置基本客户端，确保不为null
        client = baseClient
        
        // 异步初始化高级客户端，避免UI线程阻塞
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // 创建兼容所有Android版本的客户端
                val advancedClient = createSimpleClient()
                client = advancedClient
                Log.d("WooCommerceApiImpl", "已创建简化HTTP客户端")
            } catch (e: Exception) {
                Log.e("WooCommerceApiImpl", "创建高级客户端失败，使用默认配置: ${e.message}", e)
                // 已经有基本客户端，所以不需额外处理
            }
            
            try {
                typeRegistry = GsonTypeAdapterFactory(gson)
                Log.d("WooCommerceApiImpl", "已初始化类型适配器工厂")
            } catch (e: Exception) {
                Log.e("WooCommerceApiImpl", "初始化类型适配器工厂失败: ${e.message}", e)
            }
            
            Log.d("WooCommerceApiImpl", "HTTP客户端初始化完成")
        }
    }
    
    private fun buildBaseUrl(): String {
        val baseUrl = config.siteUrl.trim()
        
        // 打印详细日志
        Log.d("WooCommerceApiImpl", "构建API基础URL，原始URL: $baseUrl")
        
        // 添加合法性检查
        if (baseUrl.isBlank()) {
            Log.e("WooCommerceApiImpl", "错误: 站点URL为空")
            return "https://example.com/wp-json/wc/v3/"
        }
        
        val result = if (baseUrl.endsWith("/")) {
            "${baseUrl}wp-json/wc/v3/"
        } else {
            "$baseUrl/wp-json/wc/v3/"
        }
        
        Log.d("WooCommerceApiImpl", "最终API基础URL: $result")
        return result
    }
    
    private fun addAuthParams(urlBuilder: okhttp3.HttpUrl.Builder) {
        // 添加日志以检查认证信息
        Log.d("WooCommerceApiImpl", "添加认证参数，使用Key: ${config.consumerKey}，Secret长度: ${config.consumerSecret.length}")
        
        // 确保正确编码认证参数，避免特殊字符问题
        try {
            val encodedKey = config.consumerKey.trim()
            val encodedSecret = config.consumerSecret.trim()
            
            // 检查认证信息是否有问题
            if (encodedKey.isBlank() || encodedSecret.isBlank()) {
                Log.e("WooCommerceApiImpl", "认证参数无效：Key=${encodedKey.isBlank()}, Secret=${encodedSecret.isBlank()}")
            }
            
            // 使用明确的编码添加参数
            urlBuilder.addQueryParameter("consumer_key", encodedKey)
            urlBuilder.addQueryParameter("consumer_secret", encodedSecret)
            
            Log.d("WooCommerceApiImpl", "成功添加编码后的认证参数")
        } catch (e: Exception) {
            Log.e("WooCommerceApiImpl", "添加认证参数失败: ${e.message}", e)
            // 为避免完全失败，尝试标准添加
            urlBuilder.addQueryParameter("consumer_key", config.consumerKey)
            urlBuilder.addQueryParameter("consumer_secret", config.consumerSecret)
        }
    }
    
    private suspend inline fun <reified T> executeGetRequest(endpoint: String, queryParams: Map<String, String> = emptyMap()): T {
        val maxRetries = 3
        var currentRetry = 0
        var lastException: Exception? = null
        
        while (currentRetry < maxRetries) {
            try {
                return withContext(Dispatchers.IO) {
                    // 添加重试延迟
                    if (currentRetry > 0) {
                        val delayMs = (2.0.pow(currentRetry) * 500).toLong() // 指数退避: 500ms, 1000ms, 2000ms
                        Log.d("WooCommerceApiImpl", "重试请求 ${currentRetry+1}/${maxRetries}，延迟 $delayMs ms")
                        delay(delayMs)
                        
                        // 如果是非第一次重试，尝试创建新的client实例，避免复用可能有问题的连接
                        if (currentRetry >= 2) {
                            try {
                                Log.d("WooCommerceApiImpl", "重新创建HTTP客户端以避免连接重用问题")
                                client = createSimpleClient()
                            } catch (e: Exception) {
                                Log.e("WooCommerceApiImpl", "重新创建客户端失败: ${e.message}")
                            }
                        }
                    }
                    
                    val baseUrl = buildBaseUrl()
                    val cleanEndpoint = if (endpoint.startsWith("/")) endpoint.substring(1) else endpoint
                    val fullApiUrl = "$baseUrl$cleanEndpoint"
                    
                    val urlBuilder = try {
                        fullApiUrl.toHttpUrl().newBuilder()
                    } catch (e: IllegalArgumentException) {
                        Log.e("WooCommerceApiImpl", "无效URL: $fullApiUrl")
                        throw Exception("API URL格式无效: ${e.message}")
                    }
                    
                    // 添加查询参数和认证参数
                    queryParams.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
                    addAuthParams(urlBuilder)
                    
                    val url = urlBuilder.build()
                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .build()
                    
                    Log.d("WooCommerceApiImpl", "API请求: ${request.method} ${request.url}")
                    val startTime = System.currentTimeMillis()
                    
                    val response = try {
                        // 使用更短的超时时间进行请求
                        val requestClient = if (currentRetry > 0) {
                            // 为重试创建一个修改过超时时间的客户端
                            client.newBuilder()
                                .readTimeout(15, TimeUnit.SECONDS)
                                .connectTimeout(10, TimeUnit.SECONDS)
                                .build()
                        } else {
                            client
                        }
                        
                        requestClient.newCall(request).execute()
                    } catch (e: Exception) {
                        // 记录详细的异常信息，包括具体错误类型
                        val errorType = e.javaClass.simpleName
                        Log.e("WooCommerceApiImpl", "执行网络请求失败[$errorType]: ${e.message}")
                        if (e.message?.contains("PROTOCOL_ERROR") == true) {
                            Log.e("WooCommerceApiImpl", "HTTP/2协议错误，可能需要降级到HTTP/1.1")
                        }
                        throw e
                    }
                    
                    val duration = System.currentTimeMillis() - startTime
                    val responseBody = response.body?.string() ?: throw Exception("响应体为空")
                    
                    if (endpoint.contains("orders") || endpoint.contains("products")) {
                        Log.d("WooCommerceApiImpl", "API响应: ${endpoint} 状态码: ${response.code}, 耗时: ${duration}ms, 大小: ${responseBody.length}字节")
                    }
                    
                    if (!response.isSuccessful) {
                        Log.e("WooCommerceApiImpl", "请求失败 - 状态码: ${response.code}, 响应: $responseBody")
                        throw ApiError.fromHttpCode(response.code, responseBody)
                    }
                    
                    // 解析响应
                    try {
                        val isOrdersList = T::class.java == List::class.java && endpoint.contains("orders")
                        
                        val result = when {
                            isOrdersList -> {
                                try {
                                    gson.fromJson<T>(responseBody, orderDtoListType)
                                } catch (e: Exception) {
                                    Log.e("WooCommerceApiImpl", "标准解析订单列表失败: ${e.message}")
                                    @Suppress("UNCHECKED_CAST")
                                    extractOrdersManually(responseBody) as T
                                }
                            }
                            else -> gson.fromJson<T>(responseBody, object : TypeToken<T>() {}.type)
                        }
                        
                        if (isOrdersList) {
                            val count = (result as List<*>).size
                            Log.d("WooCommerceApiImpl", "成功获取 $count 个订单")
                        }
                        
                        result
                    } catch (e: Exception) {
                        Log.e("WooCommerceApiImpl", "解析响应失败: ${e.message}", e)
                        throw Exception("解析API响应失败: ${e.message}")
                    }
                }
            } catch (e: java.io.IOException) {
                // 细分IO异常类型，提供更详细的日志
                val isProtocolError = e.message?.contains("PROTOCOL_ERROR") == true
                val isReset = e.message?.contains("reset") == true
                val isPeerError = e.message?.contains("peer") == true
                
                if (isProtocolError) {
                    Log.e("WooCommerceApiImpl", "HTTP协议错误，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                } else if (isReset || isPeerError) {
                    // 连接重置问题可能是由服务器关闭连接引起的，使用新的客户端实例
                    Log.e("WooCommerceApiImpl", "连接被重置或对等方错误，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                    // 立即清理连接池
                    try {
                        Log.d("WooCommerceApiImpl", "立即清理连接池")
                        client.connectionPool.evictAll()
                    } catch (ex: Exception) {
                        Log.e("WooCommerceApiImpl", "清理连接池失败: ${ex.message}")
                    }
                } else {
                    Log.e("WooCommerceApiImpl", "IO错误，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                }
                
                lastException = e
                currentRetry++
                
                // 第一次重试失败后，尝试清理连接池
                if (currentRetry == 2) {
                    try {
                        Log.d("WooCommerceApiImpl", "清理连接池，尝试解决协议问题")
                        client.connectionPool.evictAll()
                    } catch (ex: Exception) {
                        Log.e("WooCommerceApiImpl", "清理连接池失败: ${ex.message}")
                    }
                }
            } catch (e: javax.net.ssl.SSLHandshakeException) {
                Log.e("WooCommerceApiImpl", "SSL握手失败，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                lastException = e
                currentRetry++
                
                // SSL握手失败，尝试使用更宽松的客户端
                if (currentRetry == 2) {
                    try {
                        Log.d("WooCommerceApiImpl", "SSL握手失败，尝试使用更宽松的SSL设置")
                        client = createSimpleClient()
                    } catch (ex: Exception) {
                        Log.e("WooCommerceApiImpl", "创建宽松SSL客户端失败: ${ex.message}")
                    }
                }
            } catch (e: javax.net.ssl.SSLException) {
                Log.e("WooCommerceApiImpl", "SSL连接异常，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                lastException = e
                currentRetry++
                
                // SSL连接异常，可能需要更宽松的设置
                if (e.message?.contains("peer") == true || e.message?.contains("reset") == true) {
                    try {
                        Log.d("WooCommerceApiImpl", "连接被重置，清理连接并创建新客户端")
                        client.connectionPool.evictAll()
                        // 短暂延迟后重新创建客户端
                        delay(300)
                        client = createSimpleClient()
                    } catch (ex: Exception) {
                        Log.e("WooCommerceApiImpl", "重置连接状态失败: ${ex.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("WooCommerceApiImpl", "请求失败，不重试: ${e.message}")
                throw e
            }
        }
        
        // 如果所有重试都失败
        Log.e("WooCommerceApiImpl", "所有重试都失败，放弃请求")
        throw lastException ?: Exception("请求失败，已尝试 $maxRetries 次")
    }
    
    private suspend inline fun <reified T> executePostRequest(endpoint: String, body: Map<String, Any>): T {
        val maxRetries = 3
        var currentRetry = 0
        var lastException: Exception? = null
        
        while (currentRetry < maxRetries) {
            try {
                return withContext(Dispatchers.IO) {
                    // 添加重试延迟
                    if (currentRetry > 0) {
                        val delayMs = (2.0.pow(currentRetry) * 500).toLong() // 指数退避: 500ms, 1000ms, 2000ms
                        Log.d("WooCommerceApiImpl", "POST请求重试 ${currentRetry+1}/${maxRetries}，延迟 $delayMs ms")
                        delay(delayMs)
                        
                        // 如果是非第一次重试，尝试创建新的client实例，避免复用可能有问题的连接
                        if (currentRetry >= 2) {
                            try {
                                Log.d("WooCommerceApiImpl", "重新创建HTTP客户端以避免POST连接重用问题")
                                client = createSimpleClient()
                            } catch (e: Exception) {
                                Log.e("WooCommerceApiImpl", "重新创建POST客户端失败: ${e.message}")
                            }
                        }
                    }
                    
                    val baseUrl = buildBaseUrl()
                    val cleanEndpoint = if (endpoint.startsWith("/")) endpoint.substring(1) else endpoint
                    val fullApiUrl = "$baseUrl$cleanEndpoint"
                    
                    val urlBuilder = try {
                        fullApiUrl.toHttpUrl().newBuilder()
                    } catch (e: IllegalArgumentException) {
                        Log.e("WooCommerceApiImpl", "无效URL: $fullApiUrl")
                        throw Exception("API URL格式无效: ${e.message}")
                    }
                    
                    // 添加认证参数
                    addAuthParams(urlBuilder)
                    
                    val jsonBody = gson.toJson(body)
                    val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                    
                    val request = Request.Builder()
                        .url(urlBuilder.build())
                        .post(requestBody)
                        .build()
                    
                    Log.d("WooCommerceApiImpl", "POST请求: ${urlBuilder.build()} - $jsonBody")
                    val startTime = System.currentTimeMillis()
                    
                    val response = try {
                        // 使用更短的超时时间进行请求
                        val requestClient = if (currentRetry > 0) {
                            // 为重试创建一个修改过超时时间的客户端
                            client.newBuilder()
                                .readTimeout(15, TimeUnit.SECONDS)
                                .connectTimeout(10, TimeUnit.SECONDS)
                                .build()
                        } else {
                            client
                        }
                        
                        requestClient.newCall(request).execute()
                    } catch (e: Exception) {
                        // 记录详细的异常信息，包括具体错误类型
                        val errorType = e.javaClass.simpleName
                        Log.e("WooCommerceApiImpl", "POST请求执行失败[$errorType]: ${e.message}")
                        if (e.message?.contains("PROTOCOL_ERROR") == true) {
                            Log.e("WooCommerceApiImpl", "POST请求HTTP/2协议错误，可能需要降级到HTTP/1.1")
                        }
                        throw e
                    }
                    
                    val duration = System.currentTimeMillis() - startTime
                    val responseBody = response.body?.string() ?: throw Exception("响应体为空")
                    
                    Log.d("WooCommerceApiImpl", "POST响应: 状态码: ${response.code}, 耗时: ${duration}ms, 大小: ${responseBody.length}字节")
                    
                    if (!response.isSuccessful) {
                        Log.e("WooCommerceApiImpl", "POST错误: ${response.code}, 响应: $responseBody")
                        throw ApiError.fromHttpCode(response.code, responseBody)
                    }
                    
                    // 使用精确的类型解析方式
                    try {
                        when {
                            T::class == OrderDto::class -> gson.fromJson(responseBody, OrderDto::class.java) as T
                            T::class == ProductDto::class -> gson.fromJson(responseBody, ProductDto::class.java) as T
                            T::class == CategoryDto::class -> gson.fromJson(responseBody, CategoryDto::class.java) as T
                            else -> gson.fromJson(responseBody, object : TypeToken<T>() {}.type)
                        }
                    } catch (e: Exception) {
                        Log.e("WooCommerceApiImpl", "POST请求解析响应失败: ${e.message}", e)
                        throw Exception("解析API响应失败: ${e.message}")
                    }
                }
            } catch (e: java.io.IOException) {
                // 细分IO异常类型，提供更详细的日志
                val isProtocolError = e.message?.contains("PROTOCOL_ERROR") == true
                val isReset = e.message?.contains("reset") == true
                val isPeerError = e.message?.contains("peer") == true
                
                if (isProtocolError) {
                    Log.e("WooCommerceApiImpl", "POST请求HTTP协议错误，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                } else if (isReset || isPeerError) {
                    // 连接重置问题可能是由服务器关闭连接引起的，使用新的客户端实例
                    Log.e("WooCommerceApiImpl", "POST请求连接被重置或对等方错误，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                    // 立即清理连接池
                    try {
                        Log.d("WooCommerceApiImpl", "立即清理POST连接池")
                        client.connectionPool.evictAll()
                    } catch (ex: Exception) {
                        Log.e("WooCommerceApiImpl", "清理POST连接池失败: ${ex.message}")
                    }
                } else {
                    Log.e("WooCommerceApiImpl", "POST请求IO错误，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                }
                
                lastException = e
                currentRetry++
                
                // 第一次重试失败后，尝试清理连接池
                if (currentRetry == 2) {
                    try {
                        Log.d("WooCommerceApiImpl", "清理POST请求连接池，尝试解决协议问题")
                        client.connectionPool.evictAll()
                    } catch (ex: Exception) {
                        Log.e("WooCommerceApiImpl", "清理连接池失败: ${ex.message}")
                    }
                }
            } catch (e: javax.net.ssl.SSLHandshakeException) {
                Log.e("WooCommerceApiImpl", "POST请求SSL握手失败，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                lastException = e
                currentRetry++
                
                // SSL握手失败，尝试使用更宽松的客户端
                if (currentRetry == 2) {
                    try {
                        Log.d("WooCommerceApiImpl", "POST请求SSL握手失败，尝试使用更宽松的SSL设置")
                        client = createSimpleClient()
                    } catch (ex: Exception) {
                        Log.e("WooCommerceApiImpl", "创建POST宽松SSL客户端失败: ${ex.message}")
                    }
                }
            } catch (e: javax.net.ssl.SSLException) {
                Log.e("WooCommerceApiImpl", "POST请求SSL连接异常，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                lastException = e
                currentRetry++
                
                // SSL连接异常，可能需要更宽松的设置
                if (e.message?.contains("peer") == true || e.message?.contains("reset") == true) {
                    try {
                        Log.d("WooCommerceApiImpl", "POST连接被重置，清理连接并创建新客户端")
                        client.connectionPool.evictAll()
                        // 短暂延迟后重新创建客户端
                        delay(300)
                        client = createSimpleClient()
                    } catch (ex: Exception) {
                        Log.e("WooCommerceApiImpl", "重置POST连接状态失败: ${ex.message}")
                    }
                }
            } catch (e: Exception) {
                if (e is ApiError) {
                    throw e
                } else {
                    Log.e("WooCommerceApiImpl", "POST请求其他错误: ${e.message}")
                    throw ApiError.fromException(e)
                }
            }
        }
        
        // 所有重试都失败
        Log.e("WooCommerceApiImpl", "POST请求所有重试都失败，放弃请求")
        throw lastException ?: Exception("POST请求失败，已尝试 $maxRetries 次")
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
        
        // 记录请求的状态，并进行处理
        val requestedStatus = status?.trim()?.lowercase()
        Log.d("WooCommerceApiImpl", "【API请求】请求订单，原始状态: $status, 处理后: $requestedStatus")
        
        // 添加状态参数到请求 - 确保格式正确（WooCommerce API期望单个字符串，不是数组）
        if (!requestedStatus.isNullOrBlank()) {
            // 检查状态是否是有效的WooCommerce状态
            val validStatuses = listOf("pending", "processing", "on-hold", "completed", "cancelled", "refunded", "failed", "trash", "any")
            
            if (validStatuses.contains(requestedStatus)) {
                Log.d("WooCommerceApiImpl", "【API请求】添加有效状态过滤: '$requestedStatus'")
                queryParams["status"] = requestedStatus  // 直接使用单个字符串状态值
            } else {
                Log.w("WooCommerceApiImpl", "【API警告】无效的状态值: '$requestedStatus'，该值不在有效状态列表中")
                Log.w("WooCommerceApiImpl", "【API警告】有效状态包括: ${validStatuses.joinToString()}")
                // 继续请求，但不添加状态参数，以避免API错误
            }
        }
        
        try {
            // 执行请求获取订单
            val result = executeGetRequest<List<OrderDto>>("orders", queryParams)
            Log.d("WooCommerceApiImpl", "【API响应】获取到 ${result.size} 个订单")
            
            // 记录返回的订单状态分布
            val statusDistribution = result.groupBy { it.status }.mapValues { it.value.size }
            Log.d("WooCommerceApiImpl", "【API响应】状态分布: $statusDistribution")
            
            // 如果指定了状态但API没有正确过滤，在客户端进行过滤
            if (!requestedStatus.isNullOrBlank() && result.isNotEmpty()) {
                // 只在请求状态是有效状态时进行本地过滤
                if (requestedStatus != "any") {
                    val matchingOrders = result.filter { it.status == requestedStatus }
                
                    // 如果过滤后没有订单，记录并返回原始结果
                    if (matchingOrders.isEmpty()) {
                        Log.w("WooCommerceApiImpl", "【API警告】未找到状态为 '$requestedStatus' 的订单，服务器可能不支持此状态过滤")
                        return result
                    }
                
                    // 否则，返回过滤后的结果
                    Log.d("WooCommerceApiImpl", "【API过滤】客户端过滤后，状态为 '$requestedStatus' 的订单数量: ${matchingOrders.size}")
                    return matchingOrders
                }
            }
            
            return result
        } catch (e: Exception) {
            // 记录详细错误信息
            Log.e("WooCommerceApiImpl", "【API错误】获取订单失败: ${e.message}")
            throw e
        }
    }
    
    override suspend fun getOrder(id: Long): OrderDto {
        Log.d("WooCommerceApiImpl", "【API请求】获取订单详情: ID=$id")
        
        // 执行请求获取订单
        val result = executeGetRequest<OrderDto>("orders/$id")
        Log.d("WooCommerceApiImpl", "【API响应】成功获取订单 #${result.number}")
        return result
    }
    
    override suspend fun updateOrder(id: Long, data: Map<String, Any>): OrderDto {
        Log.d("WooCommerceApiImpl", "【API请求】更新订单: ID=$id, 数据=$data")
        
        // 执行请求更新订单
        val result = executePostRequest<OrderDto>("orders/$id", data)
        Log.d("WooCommerceApiImpl", "【API响应】成功更新订单 #${result.number}")
        return result
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

    // 为Android 7创建兼容性客户端
    private fun createSimpleClient(): OkHttpClient {
        try {
            // 记录创建简化客户端的尝试
            Log.d("WooCommerceApiImpl", "尝试创建更宽松的HTTP客户端")
            
            // 创建一个完全信任所有证书的TrustManager
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })
            
            // 使用这个TrustManager创建一个SSLContext
            val sslContext = SSLContext.getInstance("TLSv1")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            
            // 创建一个自定义SSLSocketFactory，它会完全跳过证书验证
            val sslSocketFactory = object : SSLSocketFactory() {
                private val delegate: SSLSocketFactory = sslContext.socketFactory
                
                override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
                override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
                
                override fun createSocket(s: java.net.Socket, host: String, port: Int, autoClose: Boolean): java.net.Socket {
                    val socket = delegate.createSocket(s, host, port, autoClose) as javax.net.ssl.SSLSocket
                    try {
                        // 启用所有可用的协议
                        socket.enabledProtocols = socket.supportedProtocols
                        
                        // 启用所有可用的密码套件
                        socket.enabledCipherSuites = socket.supportedCipherSuites
                        
                        // 尝试禁用SNI，但以安全的方式
                        try {
                            val classes = socket.javaClass.classes
                            val sslParametersClass = classes.find { it.simpleName == "SSLParameters" }
                            
                            if (sslParametersClass != null) {
                                val sslParameters = socket.javaClass.getMethod("getSSLParameters").invoke(socket)
                                val setServerNamesMethod = sslParametersClass.getMethod("setServerNames", java.util.List::class.java)
                                setServerNamesMethod.invoke(sslParameters, null)
                                socket.javaClass.getMethod("setSSLParameters", sslParametersClass).invoke(socket, sslParameters)
                                Log.d("WooCommerceApiImpl", "成功禁用SNI")
                            }
                        } catch (e: NoSuchMethodException) {
                            // 可能是老系统没有这些方法，忽略
                            Log.d("WooCommerceApiImpl", "此设备不支持SNI控制")
                        } catch (e: Exception) {
                            Log.w("WooCommerceApiImpl", "尝试禁用SNI失败: ${e.message}")
                        }
                    } catch (e: Exception) {
                        Log.w("WooCommerceApiImpl", "配置SSL套接字失败，继续使用默认设置: ${e.message}")
                    }
                    return socket
                }
                
                override fun createSocket(host: String, port: Int): java.net.Socket = 
                    delegate.createSocket(host, port)
                
                override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int): java.net.Socket = 
                    delegate.createSocket(host, port, localHost, localPort)
                
                override fun createSocket(address: java.net.InetAddress, port: Int): java.net.Socket = 
                    delegate.createSocket(address, port)
                
                override fun createSocket(address: java.net.InetAddress, port: Int, localAddress: java.net.InetAddress, localPort: Int): java.net.Socket = 
                    delegate.createSocket(address, port, localAddress, localPort)
            }
            
            // 创建连接规范
            val legacySpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_0)  // 只使用TLS 1.0，这是Android 7一定支持的
                .cipherSuites(
                    // 列出所有支持的密码套件，特别关注老旧的TLS 1.0兼容的套件
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA,
                    CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_DHE_DSS_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA
                )
                .build()
                
            val clearTextSpec = ConnectionSpec.CLEARTEXT  // 允许明文也是一个后备选项
            
            // 创建请求拦截器
            val customInterceptor = Interceptor { chain ->
                val originalRequest = chain.request()
                
                // 记录请求信息
                Log.d("WooCommerceApiImpl", "开始请求 (使用超宽松SSL配置): ${originalRequest.url}")
                
                // 创建一个新的请求，强制使用HTTP/1.0
                val newRequest = originalRequest.newBuilder()
                    .header("Connection", "close")  // 禁用持久连接
                    .header("Accept-Encoding", "identity")  // 禁用压缩
                    .build()
                
                try {
                    chain.proceed(newRequest)
                } catch (e: Exception) {
                    Log.e("WooCommerceApiImpl", "请求失败: ${e.javaClass.simpleName} - ${e.message}")
                    throw e
                }
            }
            
            // 创建DNS拦截器，避免DNS解析问题
            val dnsInterceptor = Interceptor { chain ->
                val originalRequest = chain.request()
                
                // 日志DNS请求
                val host = originalRequest.url.host
                Log.d("WooCommerceApiImpl", "正在解析主机名: $host")
                
                try {
                    chain.proceed(originalRequest)
                } catch (e: Exception) {
                    Log.e("WooCommerceApiImpl", "DNS或连接失败: ${e.message}")
                    throw e
                }
            }
            
            // 创建OkHttpClient
            return OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }  // 彻底禁用主机名验证
                .connectionSpecs(listOf(legacySpec, clearTextSpec))
                .protocols(listOf(Protocol.HTTP_1_1, Protocol.HTTP_1_0))  // 支持HTTP/1.0，可能有助于兼容性
                .readTimeout(10, TimeUnit.SECONDS)  // 较短的超时可能更好
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor(customInterceptor)
                .addInterceptor(dnsInterceptor)
                .build()
        } catch (e: Exception) {
            Log.e("WooCommerceApiImpl", "创建宽松客户端失败: ${e.message}", e)
            // 基本客户端也强制使用HTTP/1.1
            return OkHttpClient.Builder()
                .protocols(listOf(Protocol.HTTP_1_1, Protocol.HTTP_1_0))
                .readTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
} 