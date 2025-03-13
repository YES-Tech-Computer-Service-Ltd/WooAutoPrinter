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
import java.io.IOException

class WooCommerceApiImpl(
    private val config: WooCommerceConfig,
    private val sslErrorInterceptor: SSLErrorInterceptor,
    private val connectionResetHandler: ConnectionResetHandler
) : WooCommerceApi {

    companion object {
        private const val TAG = "WooCommerceApiImpl"
    }
    
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
        
        Log.d("WooCommerceApiImpl", "初始化WooCommerceApiImpl的OkHttpClient，强制使用HTTP/1.1协议")
        
        // 创建信任所有证书的SSL套接字工厂
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })

        // 创建SSL上下文
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        
        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // 添加SSL握手超时设置
            .callTimeout(60, TimeUnit.SECONDS)
            // 强制使用HTTP/1.1协议，解决HTTP/2 PROTOCOL_ERROR
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            // 添加自定义SSL工厂以解决SSL握手问题
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .retryOnConnectionFailure(true)
            // 配置连接池，提高连接复用效率
            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
            
        // 如果提供了SSL错误拦截器，添加它
        sslErrorInterceptor.let { clientBuilder.addInterceptor(it) }
        
        client = clientBuilder.build()
            
        // 检查配置是否有效
        Log.d("WooCommerceApiImpl", "初始化WooCommerceApiImpl，配置: $config, " +
            "baseUrl: ${config.siteUrl}, 是否有效: ${config.isValid()}")
            
        if (!config.isValid()) {
            Log.w("WooCommerceApiImpl", "警告: WooCommerce配置无效，API请求可能会失败")
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
        urlBuilder.addQueryParameter("consumer_key", config.consumerKey)
        urlBuilder.addQueryParameter("consumer_secret", config.consumerSecret)
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
                        // 如果不是第一次尝试，添加指数退避延迟
                        if (currentRetry > 0) {
                            // 使用指数退避延迟：1秒，2秒，4秒...
                            val delayMs = (2f.pow(currentRetry - 1) * 1000).toLong()
                            Log.d("WooCommerceApiImpl", "重试请求，延迟 $delayMs ms")
                            delay(delayMs)
                        }
                        
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
                        
                        // 特别监控状态参数
                        if (endpoint.contains("orders") && queryParams.containsKey("status")) {
                            Log.d("WooCommerceApiImpl", "【状态请求】监控 - 状态参数值: '${queryParams["status"]}', URL中的状态参数: ${fullUrl.contains("status=")}") 
                            
                            // 检查URL中的状态参数
                            val urlStatus = fullUrl.substringAfter("status=", "未找到").substringBefore("&", "未找到后缀")
                            Log.d("WooCommerceApiImpl", "【状态请求】监控 - URL中的status参数值: '$urlStatus'")
                        }
                        
                        val request = Request.Builder()
                            .url(urlBuilder.build())
                            .get()
                            // 添加缓存控制头
                            .header("Cache-Control", "public, max-age=300") // 允许缓存5分钟
                            .build()
                        
                        val startTime = System.currentTimeMillis()
                        val response = client.newCall(request).execute()
                        val duration = System.currentTimeMillis() - startTime
                        
                        val responseBody = response.body?.string() ?: throw Exception("响应体为空")
                        
                        if (endpoint.contains("orders") || endpoint.contains("products")) {
                            Log.d("WooCommerceApiImpl", "【API请求】${endpoint} 响应状态码: ${response.code}, 耗时: ${duration}ms, 大小: ${responseBody.length}字节")
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
            } catch (e: okhttp3.internal.http2.StreamResetException) {
                Log.e("WooCommerceApiImpl", "HTTP/2 流重置错误，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                lastException = e
                currentRetry++
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("WooCommerceApiImpl", "请求超时，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                lastException = e
                currentRetry++
            } catch (e: javax.net.ssl.SSLException) {
                Log.e("WooCommerceApiImpl", "SSL错误，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                lastException = e
                currentRetry++
                // 记录连接重置事件
                if (e.message?.contains("reset") == true || e.message?.contains("peer") == true) {
                    connectionResetHandler.recordConnectionReset()
                    // 分析错误原因
                    val analysis = connectionResetHandler.analyzeResetError(e) ?: "未知SSL错误"
                    Log.e("WooCommerceApiImpl", "连接重置分析: $analysis")
                }
            } catch (e: java.io.IOException) {
                Log.e("WooCommerceApiImpl", "IO错误，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                lastException = e
                currentRetry++
                // 检查是否是连接重置错误
                if (e.message?.contains("reset") == true || e.message?.contains("peer") == true) {
                    connectionResetHandler.recordConnectionReset()
                    // 分析错误原因
                    val analysis = connectionResetHandler.analyzeResetError(e) ?: "未知IO错误"
                    Log.e("WooCommerceApiImpl", "连接重置分析: $analysis")
                }
            } catch (e: Exception) {
                // 对于其他异常，直接抛出不重试
                Log.e("WooCommerceApiImpl", "请求失败，不重试: ${e.message}")
                throw e
            }
        }
        
        // 如果所有重试都失败，抛出最后捕获的异常
        Log.e("WooCommerceApiImpl", "所有重试都失败，放弃请求")
        throw lastException ?: Exception("请求失败，已尝试 $maxRetries 次")
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
    
    override suspend fun getOrders(
        page: Int,
        perPage: Int,
        status: String?
    ): List<OrderDto> {
        val apiUrl = buildApiUrl("orders", mapOf(
            "page" to page.toString(),
            "per_page" to perPage.toString(),
            "status" to status
        ))
        
        if (status != null) {
            Log.d(TAG, "【API请求】请求订单，原始状态: $status, 处理后: $status")
            
            // 状态参数监控
            if (status.isNotBlank()) {
                Log.d(TAG, "【API请求】添加有效状态过滤: '$status'")
            }
        }
        
        // 监控URL中的状态参数
        val finalUrl = apiUrl.toString()
        val hasStatusInUrl = finalUrl.contains("&status=") || finalUrl.contains("?status=")
        Log.d(TAG, "【状态请求】监控 - 状态参数值: '$status', URL中的状态参数: $hasStatusInUrl")
        
        // 提取URL中的status参数值以监控
        val statusParamValue = try {
            val regex = "[?&]status=([^&]+)".toRegex()
            val matchResult = regex.find(finalUrl)
            matchResult?.groupValues?.get(1) ?: "未找到后缀"
        } catch (e: Exception) {
            "解析错误: ${e.message}"
        }
        Log.d(TAG, "【状态请求】监控 - URL中的status参数值: '$statusParamValue'")
        
        // 使用executeWithRetry处理API调用
        return executeWithRetry(
            call = {
                val response = makeGetRequest(apiUrl)
                if (response.isSuccessful) {
                    val orders = parseOrdersResponse(response.body)
                    Log.d(TAG, "获取到${orders.size}个订单")
                    orders
                } else {
                    Log.e(TAG, "【API错误】获取订单失败，HTTP状态码: ${response.code}")
                    throw IOException("获取订单失败，HTTP状态码: ${response.code}")
                }
            },
            url = apiUrl.toString(),
            errorHandler = { e ->
                Log.e(TAG, "【API错误】获取订单失败: ${e.message}", e)
                emptyList()
            }
        )
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

    /**
     * 安全执行带有重试的API调用
     */
    private suspend fun <T> executeWithRetry(
        call: suspend () -> T,
        url: String,
        maxRetries: Int = 3,
        errorHandler: ((Exception) -> T)? = null
    ): T {
        var retryCount = 0
        var lastException: Exception? = null
        
        while (retryCount <= maxRetries) {
            try {
                return call()
            } catch (e: Exception) {
                lastException = e
                
                if (e is IOException || e is retrofit2.HttpException) {
                    // 检查是否应该重试
                    if (connectionResetHandler.shouldRetry(e, retryCount, url)) {
                        Log.e(TAG, "API调用失败 (${e.javaClass.simpleName})，准备重试 ($retryCount/$maxRetries): ${e.message}")
                        // 等待合适的时间
                        connectionResetHandler.doRetryDelay(retryCount, url)
                        retryCount++
                        continue
                    }
                }
                
                // 无法重试或非网络错误
                return errorHandler?.invoke(e) ?: throw e
            }
        }
        
        // 如果所有重试都失败
        return errorHandler?.invoke(lastException ?: IOException("重试失败")) 
            ?: throw (lastException ?: IOException("重试失败"))
    }
} 