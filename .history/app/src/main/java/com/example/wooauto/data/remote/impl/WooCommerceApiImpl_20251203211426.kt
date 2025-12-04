package com.example.wooauto.data.remote.impl

import android.util.Log
import com.example.wooauto.utils.UiLog
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
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import com.example.wooauto.data.remote.ApiError
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.math.pow
import com.example.wooauto.data.remote.interceptors.SSLErrorInterceptor
import com.example.wooauto.data.remote.ConnectionResetHandler
import com.example.wooauto.data.remote.ssl.SslUtil
import com.example.wooauto.BuildConfig

import com.example.wooauto.utils.GlobalErrorManager
import com.example.wooauto.utils.ErrorSource

class WooCommerceApiImpl(
    private val config: WooCommerceConfig,
    private val sslErrorInterceptor: SSLErrorInterceptor? = null,
    private val connectionResetHandler: ConnectionResetHandler? = null,
    private val globalErrorManager: GlobalErrorManager? = null
) : WooCommerceApi {
    
    // 创建一个List<OrderDto>的Type，用于注册类型适配器
    private val orderDtoListType = object : TypeToken<List<OrderDto>>() {}.type
    
    private val gson = GsonBuilder()
        .registerTypeAdapter(OrderDto::class.java, OrderDtoTypeAdapter())
        .registerTypeAdapter(orderDtoListType, OrderDtoListTypeAdapter(OrderDtoTypeAdapter()))
        .registerTypeAdapter(ProductDto::class.java, ProductDtoTypeAdapter())
        .registerTypeAdapter(CategoryDto::class.java, CategoryDtoTypeAdapter())
        .create()
    
    private val client: OkHttpClient
    
    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // 将日志级别从BODY改为BASIC，减少响应体和请求体的详细输出
            level = if (BuildConfig.DEBUG) {
                // 在调试版本中使用BASIC级别，只输出请求/响应行
                HttpLoggingInterceptor.Level.BASIC
            } else {
                // 在发布版本中完全禁用
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        UiLog.d("WooCommerceApiImpl", "初始化WooCommerceApiImpl的OkHttpClient，强制使用HTTP/1.1协议")
        
        // 创建信任所有证书的SSL套接字工厂
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })

        // 创建SSL上下文
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        
        // 获取TrustManager
        val trustManager = trustAllCerts[0] as javax.net.ssl.X509TrustManager
        
        val clientBuilder = OkHttpClient.Builder()
            // 【核心修复】强制使用IPv4，解决IPv6解析成功但连接超时的问题
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    return try {
                        // 获取所有IP地址
                        val allAddresses = okhttp3.Dns.SYSTEM.lookup(hostname)
                        // 过滤出IPv4地址 (Inet4Address)
                        val ipv4Addresses = allAddresses.filter { it is java.net.Inet4Address }

                        if (ipv4Addresses.isNotEmpty()) {
                            UiLog.d("WooCommerceApiImpl", "DNS解析: $hostname -> IPv4: $ipv4Addresses (过滤了IPv6)")
                            ipv4Addresses
                        } else {
                            // 如果没有IPv4，只能返回所有（通常是IPv6）
                            UiLog.w("WooCommerceApiImpl", "DNS解析: $hostname 没有IPv4地址，回退到默认结果: $allAddresses")
                            allAddresses
                        }
                    } catch (e: Exception) {
                        // DNS解析失败，抛出异常
                        UiLog.e("WooCommerceApiImpl", "DNS解析失败: $hostname, ${e.message}")
                        throw e
                    }
                }
            })
            .addInterceptor(loggingInterceptor)
            // 添加自定义User-Agent拦截器
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", "WooAuto-Android/${BuildConfig.VERSION_NAME}")
                    .header("Connection", "keep-alive")
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // 添加SSL握手超时设置
            .callTimeout(120, TimeUnit.SECONDS)
            // 强制使用HTTP/1.1协议，解决服务器端对HTTP/2的不兼容导致的500错误
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1)) 
            // 添加自定义SSL工厂以解决SSL握手问题 - 正确提供TrustManager
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .retryOnConnectionFailure(true)
            // 配置连接池，提高连接复用效率，增加最大空闲连接数
            .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
            
        // 配置TLS版本支持
        SslUtil.configureTls(clientBuilder)
            
        // 如果提供了SSL错误拦截器，添加它
        sslErrorInterceptor?.let { clientBuilder.addInterceptor(it) }
        
        client = clientBuilder.build()
            
        // 检查配置是否有效
//        Log.d("WooCommerceApiImpl", "初始化WooCommerceApiImpl，配置: $config, " +
//            "baseUrl: ${config.siteUrl}, 是否有效: ${config.isValid()}")
            
        if (!config.isValid()) {
//            Log.w("WooCommerceApiImpl", "警告: WooCommerce配置无效，API请求可能会失败")
        }
    }
    
    private fun buildBaseUrl(): String {
        val baseUrl = config.siteUrl.trim()
        
        // 注释掉详细日志
        // Log.d("WooCommerceApiImpl", "构建API基础URL，原始URL: $baseUrl")
        
        // 添加合法性检查
        if (baseUrl.isBlank()) {
//            Log.e("WooCommerceApiImpl", "错误: 站点URL为空")
            return "https://example.com/wp-json/wc/v3/"
        }
        
        val result = if (baseUrl.endsWith("/")) {
            "${baseUrl}wp-json/wc/v3/"
        } else {
            "$baseUrl/wp-json/wc/v3/"
        }
        
        // 仅在调试模式保留最终URL日志
        if (BuildConfig.DEBUG && false) { // 添加false条件使其不执行
//            Log.d("WooCommerceApiImpl", "最终API基础URL: $result")
        }
        return result
    }
    
    private fun addAuthParams(urlBuilder: okhttp3.HttpUrl.Builder) {
        // 保留错误日志，注释掉详细调试日志
        if (config.consumerKey.isBlank() || config.consumerSecret.isBlank()) {
            Log.e("WooCommerceApiImpl", "错误: 认证凭据为空，consumer_key: ${config.consumerKey.isBlank()}, consumer_secret: ${config.consumerSecret.isBlank()}")
        }
        
        // 安全地添加consumer_key
        try {
            val sanitizedKey = config.consumerKey.trim()
            // 注释掉详细日志
            // Log.d("WooCommerceApiImpl", "添加认证参数，使用Key: ${sanitizedKey}，Secret长度: ${config.consumerSecret.length}")
            
            // 检查是否是有效的consumer_key格式（通常以ck_开头）
            if (!sanitizedKey.startsWith("ck_")) {
                Log.w("WooCommerceApiImpl", "警告: consumer_key格式可能不正确，通常应以'ck_'开头")
            }
            
            // 强制URL编码参数
            urlBuilder.addEncodedQueryParameter("consumer_key", sanitizedKey)
            urlBuilder.addEncodedQueryParameter("consumer_secret", config.consumerSecret.trim())
            
            // 注释掉验证参数的详细日志
            /*
            // 验证参数是否正确添加
            val url = urlBuilder.build()
            val hasKey = url.queryParameterNames.contains("consumer_key")
            val hasSecret = url.queryParameterNames.contains("consumer_secret")
            
            Log.d("WooCommerceApiImpl", "验证认证参数: consumer_key存在=${hasKey}, consumer_secret存在=${hasSecret}")
            
            if (!hasKey || !hasSecret) {
                Log.e("WooCommerceApiImpl", "错误: 认证参数添加失败")
            }
            */
        } catch (e: Exception) {
            Log.e("WooCommerceApiImpl", "添加认证参数时出错: ${e.message}", e)
            // 仍然尝试添加
            urlBuilder.addQueryParameter("consumer_key", config.consumerKey)
            urlBuilder.addQueryParameter("consumer_secret", config.consumerSecret)
        }
    }
    
    // 辅助方法：报告 API 错误
    private fun reportApiError(endpoint: String, error: Throwable) {
        val source = when {
            endpoint.contains("orders") -> ErrorSource.API_ORDER
            endpoint.contains("products") -> ErrorSource.API_PRODUCT
            else -> ErrorSource.API
        }
        
        val title = when (source) {
            ErrorSource.API_ORDER -> "订单获取失败"
            ErrorSource.API_PRODUCT -> "产品数据同步失败"
            else -> "API 请求失败"
        }
        
        val message = if (error is ApiError) {
            "服务器返回错误: ${error.httpCode} - ${error.message}"
        } else {
            "网络请求异常: ${error.message}"
        }
        
        globalErrorManager?.reportError(
            source = source,
            title = title,
            message = message,
            debugInfo = "Endpoint: $endpoint\nError: ${error.message}"
        )
    }

    // 辅助方法：请求成功后清除错误
    private fun resolveApiError(endpoint: String) {
        val source = when {
            endpoint.contains("orders") -> ErrorSource.API_ORDER
            endpoint.contains("products") -> ErrorSource.API_PRODUCT
            else -> ErrorSource.API
        }
        globalErrorManager?.resolveError(source)
    }

    private suspend inline fun <reified T> executeGetRequest(endpoint: String, queryParams: Map<String, String> = emptyMap()): T {
        // 最大重试次数
        val maxRetries = 3
        var currentRetry = 0
        var lastException: Exception? = null
        
        while (currentRetry < maxRetries) {
            try {
                return withContext(Dispatchers.IO) {
                    try {
                        // ... (保留原有的指数退避逻辑)
                        if (currentRetry > 0) {
                            val delayMs = (2f.pow(currentRetry - 1) * 1000).toLong()
                            UiLog.d("WooCommerceApiImpl", "重试请求，延迟 $delayMs ms")
                            delay(delayMs)
                        }
                        
                        val baseUrl = buildBaseUrl()
                        // ... (保留原有的URL构建逻辑)
                        val cleanEndpoint = if (endpoint.startsWith("/")) endpoint.substring(1) else endpoint
                        val fullApiUrl = "$baseUrl$cleanEndpoint"
                        
                        val urlBuilder = fullApiUrl.toHttpUrl().newBuilder()
                        
                        // 添加认证参数
                        addAuthParams(urlBuilder)
                        
                        // 添加其他查询参数
                        queryParams.forEach { (key, value) ->
                            if (key == "status") {
                                UiLog.d("WooCommerceApiImpl", "【URL构建】添加状态参数值: '$value'")
                                urlBuilder.addQueryParameter(key, value)
                            } else {
                                urlBuilder.addQueryParameter(key, value)
                            }
                        }
                        
                        val fullUrl = urlBuilder.build().toString()
                        
                        val request = Request.Builder()
                            .url(fullUrl)
                            .cacheControl(okhttp3.CacheControl.Builder().maxAge(5, TimeUnit.MINUTES).build())
                            .build()
                        
                        val response = client.newCall(request).execute()
                        val responseBody = response.body?.string() ?: throw Exception("响应体为空")
                        
                        if (!response.isSuccessful) {
                            Log.e("WooCommerceApiImpl", "【API错误】HTTP错误: ${response.code}")
                            throw ApiError.fromHttpCode(response.code, responseBody)
                        }
                        
                        val result = try {
                            // ... (保留原有的解析逻辑)
                            when {
                                isCollectionType<T>() -> {
                                    when {
                                        isListOfOrderDto<T>() -> {
                                            UiLog.d("WooCommerceApiImpl", "解析List<OrderDto>类型响应")
                                            gson.fromJson<T>(responseBody, orderDtoListType)
                                        }
                                        else -> {
                                            gson.fromJson<T>(responseBody, object : TypeToken<T>() {}.type)
                                        }
                                    }
                                }
                                T::class == OrderDto::class -> {
                                    UiLog.d("WooCommerceApiImpl", "解析OrderDto类型响应")
                                    gson.fromJson(responseBody, OrderDto::class.java) as T
                                }
                                T::class == ProductDto::class -> {
                                    UiLog.d("WooCommerceApiImpl", "解析ProductDto类型响应")
                                    gson.fromJson(responseBody, ProductDto::class.java) as T
                                }
                                T::class == CategoryDto::class -> {
                                    UiLog.d("WooCommerceApiImpl", "解析CategoryDto类型响应")
                                    gson.fromJson(responseBody, CategoryDto::class.java) as T
                                }
                                else -> {
                                    gson.fromJson<T>(responseBody, object : TypeToken<T>() {}.type)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("WooCommerceApiImpl", "【解析错误】无法解析JSON响应: ${e.message}")
                            throw e
                        }
                        
                        // 请求成功，清除相关错误
                        resolveApiError(endpoint)
                        
                        result
                    } catch (e: Exception) {
                        Log.e("WooCommerceApiImpl", "【API错误】请求或解析出错: ${e.message}")
                        throw e
                    }
                }
            } catch (e: Exception) {
                // 捕获所有异常（包括超时、SSL、IO等）并记录为最后一次异常
                lastException = e
                
                // 针对特定异常类型的日志记录
                when (e) {
                    is ApiError -> {
                        // 核心优化：如果是 ApiError 且是 4xx 错误（客户端错误），不要重试，直接抛出
                        // 401: 认证失败, 403: 权限不足, 404: 路径错误
                        if (e.httpCode in 400..499) {
                            Log.e("WooCommerceApiImpl", "遇到 4xx 错误 (${e.httpCode})，停止重试，直接上报")
                            reportApiError(endpoint, e) // 立即上报给用户
                            throw e
                        }
                        Log.e("WooCommerceApiImpl", "API服务器错误 (${e.httpCode})，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                    }
                    is java.net.SocketTimeoutException -> Log.e("WooCommerceApiImpl", "请求超时，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                    is javax.net.ssl.SSLException -> {
                        Log.e("WooCommerceApiImpl", "SSL错误，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                        if (e.message?.contains("reset") == true || e.message?.contains("peer") == true) {
                            connectionResetHandler?.recordConnectionReset()
                        }
                    }
                    is java.io.IOException -> Log.e("WooCommerceApiImpl", "IO错误，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                    else -> Log.e("WooCommerceApiImpl", "请求异常: ${e.message}")
                }
                
                currentRetry++
                
                // 如果不是 ApiError（非业务逻辑错误），可以尝试重试
                // ApiError 通常意味着 4xx/5xx，重试可能没用，但也取决于具体策略
                // 这里为了简单，保持原有重试逻辑，但如果达到最大重试次数，在下面统一抛出
            }
        }
        
        // 如果代码运行到这里，说明所有重试都失败了
        Log.e("WooCommerceApiImpl", "所有重试都失败，放弃请求")
        val finalError = lastException ?: Exception("请求失败，已尝试 $maxRetries 次")
        
        // 上报错误到全局管理器
        reportApiError(endpoint, finalError)
        
        throw finalError
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
                
                UiLog.d("API请求", "POST ${urlBuilder.build()} - $jsonBody")
                
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
                
                // 请求成功，清除相关错误
                resolveApiError(endpoint)
                
                result
            } catch (e: Exception) {
                Log.e("API错误", "POST请求失败: ${e.message}", e)
                
                // 上报 POST 请求错误
                reportApiError(endpoint, e)
                
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
        UiLog.d("WooCommerceApiImpl", "更新产品 ID:$id 使用PATCH请求，数据: $data")
        return executePatchRequest("products/$id", data)
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
        UiLog.d("WooCommerceApiImpl", "【API请求】请求订单，原始状态: $status, 处理后: $requestedStatus")
        
        // 添加状态参数到请求 - 确保格式正确（WooCommerce API期望单个字符串，不是数组）
        if (!requestedStatus.isNullOrBlank()) {
            // 检查状态是否是有效的WooCommerce状态
            val validStatuses = listOf("pending", "processing", "on-hold", "completed", "cancelled", "refunded", "failed", "trash", "any")
            
            if (validStatuses.contains(requestedStatus)) {
                UiLog.d("WooCommerceApiImpl", "【API请求】添加有效状态过滤: '$requestedStatus'")
                queryParams["status"] = requestedStatus  // 直接使用单个字符串状态值
            } else {
                // 检查是否是中文状态，尝试映射为英文
                val statusMap = mapOf(
                    "处理中" to "processing",
                    "待付款" to "pending",
                    "已完成" to "completed",
                    "已取消" to "cancelled",
                    "已退款" to "refunded",
                    "失败" to "failed",
                    "暂挂" to "on-hold"
                )
                
                // 尝试映射中文状态
                val mappedStatus = statusMap[requestedStatus]
                if (mappedStatus != null) {
                    UiLog.d("WooCommerceApiImpl", "【API请求】将中文状态 '$requestedStatus' 映射为 '$mappedStatus'")
                    queryParams["status"] = mappedStatus
                } else {
                    Log.w("WooCommerceApiImpl", "【API请求】忽略无效的状态值: '$requestedStatus'")
                }
            }
        }

        // 添加调试日志，输出完整查询参数
        UiLog.d("WooCommerceApiImpl", "【API请求】订单查询参数: $queryParams")
        
        // 尝试不带过滤条件获取订单 - 测试用
        if (requestedStatus != null && queryParams.containsKey("status")) {
            try {
                UiLog.d("WooCommerceApiImpl", "【API请求】尝试先不带状态过滤条件获取订单")
                val basicParams = mutableMapOf(
                    "page" to page.toString(),
                    "per_page" to perPage.toString()
                )
                val result = executeGetRequest<List<OrderDto>>("orders", basicParams)
                if (result.isNotEmpty()) {
                    UiLog.d("WooCommerceApiImpl", "【API请求】不带状态参数成功获取到 ${result.size} 个订单")
                    // 在本地过滤结果
                    return result.filter { it.status == requestedStatus }
                }
            } catch (e: Exception) {
                Log.w("WooCommerceApiImpl", "【API请求】不带状态过滤尝试失败: ${e.message}")
                // 继续尝试原始请求
            }
        }
        
        return executeGetRequest("orders", queryParams)
    }
    
    override suspend fun getOrder(id: Long): OrderDto {
        return executeGetRequest("orders/$id")
    }
    
    override suspend fun updateOrder(id: Long, data: Map<String, Any>): OrderDto {
        return executePostRequest("orders/$id", data)
    }
    
    // 添加一个备用方法，在标准解析失败时尝试手动解析JSON提取订单数据
    private fun extractOrdersManually(jsonString: String): List<OrderDto> {
        UiLog.d("WooCommerceApiImpl", "【订单调试】尝试手动解析订单数据")
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
            
            UiLog.d("WooCommerceApiImpl", "【订单调试】手动解析完成: 成功=${successCount}, 失败=${failCount}, 总计=${orders.size}")
            
        } catch (e: Exception) {
            Log.e("WooCommerceApiImpl", "【订单调试】手动解析全部失败: ${e.message}", e)
        }
        
        return orders
    }
    
    /**
     * 检查一个类型是否是集合类型
     */
    private inline fun <reified T> isCollectionType(): Boolean {
        return Collection::class.java.isAssignableFrom(T::class.java) ||
               (T::class.java == Any::class.java && 
                 T::class.java.typeParameters.isNotEmpty())
    }
    
    /**
     * 检查一个类型是否是OrderDto列表
     */
    private inline fun <reified T> isListOfOrderDto(): Boolean {
        return T::class.java == List::class.java &&
               (T::class.java.genericSuperclass as? ParameterizedType)
                    ?.actualTypeArguments?.getOrNull(0)?.typeName
                    ?.contains("OrderDto") == true
    }
    
    override suspend fun getOrdersWithParams(page: Int, perPage: Int, params: Map<String, String>): List<OrderDto> {
        val queryParams = mutableMapOf(
            "page" to page.toString(),
            "per_page" to perPage.toString()
        )
        
        // 添加自定义参数，特殊处理status参数
        params.forEach { (key, value) ->
            // 检查是否是状态参数且不为空
            if (key == "status" && value.isNotEmpty()) {
                UiLog.d("WooCommerceApiImpl", "【参数修复】处理状态参数: $value")
                
                // 检查状态是否是有效的WooCommerce状态
                val validStatuses = listOf("pending", "processing", "on-hold", "completed", "cancelled", "refunded", "failed", "trash", "any")
                
                if (validStatuses.contains(value.lowercase())) {
                    // 是有效状态，正常添加
                    queryParams[key] = value.lowercase()
                    UiLog.d("WooCommerceApiImpl", "【参数修复】添加有效状态: ${value.lowercase()}")
                } else {
                    // 尝试映射中文状态
                    val statusMap = mapOf(
                        "处理中" to "processing",
                        "待付款" to "pending",
                        "已完成" to "completed",
                        "已取消" to "cancelled",
                        "已退款" to "refunded",
                        "失败" to "failed",
                        "暂挂" to "on-hold"
                    )
                    
                    val mappedStatus = statusMap[value]
                    if (mappedStatus != null) {
                        queryParams[key] = mappedStatus
                        UiLog.d("WooCommerceApiImpl", "【参数修复】将中文状态 '$value' 映射为 '$mappedStatus'")
                    } else {
                        // 如果无法识别，不添加此参数
                        Log.w("WooCommerceApiImpl", "【参数修复】忽略无效的状态值: '$value'")
                    }
                }
            } else {
                // 其他参数正常添加
                queryParams[key] = value
            }
        }
        
        UiLog.d("WooCommerceApiImpl", "【API请求】带自定义参数查询订单，最终参数: $queryParams")
        return executeGetRequest("orders", queryParams)
    }

    private suspend inline fun <reified T> executePatchRequest(endpoint: String, body: Map<String, Any>): T {
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
                    .patch(requestBody)
                    .build()
                
                UiLog.d("API请求", "PATCH ${urlBuilder.build()} - 请求体: $jsonBody")
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw Exception("响应体为空")
                
                UiLog.d("API响应", "PATCH ${urlBuilder.build()} - 状态码: ${response.code} - 响应体: $responseBody")
                
                if (!response.isSuccessful) {
                    Log.e("API错误", "HTTP错误: ${response.code} - 响应体: $responseBody")
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
                Log.e("API错误", "PATCH请求失败: ${e.message}", e)
                
                if (e is ApiError) {
                    throw e
                } else {
                    throw ApiError.fromException(e)
                }
            }
        }
    }
} 