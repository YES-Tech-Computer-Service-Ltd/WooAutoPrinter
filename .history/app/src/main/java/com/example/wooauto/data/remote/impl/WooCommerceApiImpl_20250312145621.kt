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
        
        // 记录系统信息，便于SSL调试
        val androidVersion = android.os.Build.VERSION.SDK_INT
        val deviceModel = android.os.Build.MODEL
        
        Log.d("WooCommerceApiImpl", "初始化API客户端，设备: $deviceModel, Android SDK: $androidVersion")
        Log.d("WooCommerceApiImpl", "初始化WooCommerceApiImpl的OkHttpClient，使用简化SSL配置")
        
        // 创建信任所有证书的SSL套接字工厂
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })

        // 为Android 7设备特别优化SSL配置
        val sslContext = if (androidVersion >= 24 && androidVersion <= 25) {
            Log.d("WooCommerceApiImpl", "检测到Android 7设备，使用专用TLS配置")
            // 在Android 7上特别指定使用TLSv1.2
            javax.net.ssl.SSLContext.getInstance("TLSv1.2")
        } else {
            // 其他Android版本使用通用TLS配置
            javax.net.ssl.SSLContext.getInstance("TLS")
        }
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        
        // 记录每个TLS支持情况
        val tlsVersions = listOf("TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3")
        tlsVersions.forEach { version ->
            try {
                val testContext = javax.net.ssl.SSLContext.getInstance(version)
                testContext.init(null, null, null)
                Log.d("WooCommerceApiImpl", "SSL支持检查: $version 可用")
            } catch (e: Exception) {
                Log.d("WooCommerceApiImpl", "SSL支持检查: $version 不可用 - ${e.message}")
            }
        }
        
        // 为Android 7创建特定的TLS规格
        val connectionSpecs = if (androidVersion >= 24 && androidVersion <= 25) {
            // 为Android 7创建明确的TLSv1.2连接规格
            val spec = okhttp3.ConnectionSpec.Builder(okhttp3.ConnectionSpec.MODERN_TLS)
                .tlsVersions(okhttp3.TlsVersion.TLS_1_2)
                .build()
            listOf(spec, okhttp3.ConnectionSpec.CLEARTEXT)
        } else {
            // 其他Android版本使用默认配置
            listOf(okhttp3.ConnectionSpec.MODERN_TLS, okhttp3.ConnectionSpec.CLEARTEXT)
        }
        
        Log.d("WooCommerceApiImpl", "配置的ConnectionSpecs: $connectionSpecs")
        
        // 使用系统默认的ConnectionSpec，不指定具体加密套件
        client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS) // 增加超时时间
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // 强制使用HTTP/1.1协议，解决HTTP/2 PROTOCOL_ERROR
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            // 添加自定义SSL工厂以解决SSL握手问题
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            // 使用前面配置的连接规格
            .connectionSpecs(connectionSpecs)
            .hostnameVerifier { _, _ -> true }
            .retryOnConnectionFailure(true)
            .build()
            
        Log.d("WooCommerceApiImpl", "OkHttpClient初始化完成，已配置TLS支持和SSL证书信任")
        
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
        // 最大重试次数
        val maxRetries = 3
        var currentRetry = 0
        var lastException: Exception? = null
        
        // 检查是否是订单相关请求 - 这类请求在Android 7上可能需要特殊处理
        val isOrderRequest = endpoint.contains("orders")
        val isProductRequest = endpoint.contains("products")
        
        // 记录请求类型，用于调试
        val requestType = when {
            isOrderRequest -> "订单"
            isProductRequest -> "产品"
            else -> "其他"
        }
        
        // 检测是否是Android 7设备，这可能需要特殊处理
        val isAndroid7 = android.os.Build.VERSION.SDK_INT in 24..25
        if (isAndroid7 && isOrderRequest) {
            Log.d("WooCommerceApiImpl", "【特殊处理】在Android 7设备上执行订单请求，将使用特殊处理逻辑")
            
            // 强制在Android 7上始终先尝试HTTP请求方案 - 不再检查endpoint和currentRetry
            Log.d("WooCommerceApiImpl", "【Android 7 HTTP方案】强制使用HTTP直接请求获取数据")
            
            try {
                Log.d("WooCommerceApiImpl", "【Android 7 HTTP方案】尝试使用HTTP直接请求获取订单")
                
                // 使用HTTP而不是HTTPS
                val baseUrl = buildBaseUrl().replace("https://", "http://")
                Log.d("WooCommerceApiImpl", "【Android 7 HTTP方案】已将https替换为http: $baseUrl")
                
                val cleanEndpoint = if (endpoint.startsWith("/")) endpoint.substring(1) else endpoint
                val fullApiUrl = "$baseUrl$cleanEndpoint"
                
                // 构建完整URL，包括查询参数
                val urlStrBuilder = StringBuilder(fullApiUrl)
                urlStrBuilder.append("?")
                // 添加查询参数
                for ((key, value) in queryParams) {
                    urlStrBuilder.append("$key=$value&")
                }
                // 添加认证参数
                urlStrBuilder.append("consumer_key=${config.consumerKey.trim()}&")
                urlStrBuilder.append("consumer_secret=${config.consumerSecret.trim()}")
                
                val urlStr = urlStrBuilder.toString()
                Log.d("WooCommerceApiImpl", "【Android 7 HTTP方案】准备直接请求URL: $urlStr")
                
                // 执行HTTP请求
                val url = java.net.URL(urlStr)
                val startTime = System.currentTimeMillis()
                
                Log.d("WooCommerceApiImpl", "【Android 7 HTTP方案】创建URLConnection")
                val connection = url.openConnection() as java.net.HttpURLConnection
                Log.d("WooCommerceApiImpl", "【Android 7 HTTP方案】URLConnection已创建")
                
                connection.connectTimeout = 60000  // 增加超时时间
                connection.readTimeout = 60000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Connection", "close")
                Log.d("WooCommerceApiImpl", "【Android 7 HTTP方案】请求属性已设置")
                
                try {
                    Log.d("WooCommerceApiImpl", "【Android 7 HTTP方案】开始连接...")
                    connection.connect()
                    Log.d("WooCommerceApiImpl", "【Android 7 HTTP方案】连接已建立")
                    
                    val responseCode = connection.responseCode
                    val duration = System.currentTimeMillis() - startTime
                    Log.d("WooCommerceApiImpl", "【Android 7 HTTP方案】接收到响应，状态码: $responseCode，耗时: ${duration}ms")
                    
                    if (responseCode == 200 || responseCode == 201) {
                        Log.d("WooCommerceApiImpl", "【Android 7 HTTP方案】开始读取响应内容")
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream))
                        val response = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                        reader.close()
                        
                        val responseBody = response.toString()
                        Log.d("WooCommerceApiImpl", "【Android 7 HTTP方案】成功获取响应，大小: ${responseBody.length}字节，耗时: ${duration}ms")
                        
                        if (responseBody.isNotEmpty()) {
                            // 解析响应
                            val isOrdersList = T::class.java == List::class.java && endpoint.contains("orders")
                            Log.d("WooCommerceApiImpl", "【Android 7 HTTP方案】开始解析响应，是否为订单列表: $isOrdersList")
                            
                            val result = if (isOrdersList) {
                                try {
                                    Log.d("WooCommerceApiImpl", "【解析】使用订单列表适配器解析")
                                    gson.fromJson<T>(responseBody, orderDtoListType)
                                } catch (e: Exception) {
                                    Log.e("WooCommerceApiImpl", "【解析错误】标准解析订单列表失败，尝试备用方式: ${e.message}")
                                    @Suppress("UNCHECKED_CAST")
                                    extractOrdersManually(responseBody) as T
                                }
                            } else {
                                Log.d("WooCommerceApiImpl", "【解析】使用标准适配器解析 ${requestType} 数据")
                                gson.fromJson<T>(responseBody, object : TypeToken<T>() {}.type)
                            }
                            
                            // 记录结果
                            if (isOrdersList) {
                                val count = (result as List<*>).size
                                Log.d("WooCommerceApiImpl", "【成功】通过HTTP直接请求获取 $count 个订单")
                            }
                            
                            return result
                        } else {
                            Log.e("WooCommerceApiImpl", "【Android 7 HTTP方案】响应体为空")
                        }
                    } else {
                        Log.e("WooCommerceApiImpl", "【Android 7 HTTP方案】HTTP请求失败: 状态码 $responseCode")
                        val errorReader = java.io.BufferedReader(java.io.InputStreamReader(connection.errorStream ?: java.io.ByteArrayInputStream(ByteArray(0))))
                        val errorResponse = StringBuilder()
                        var line: String?
                        while (errorReader.readLine().also { line = it } != null) {
                            errorResponse.append(line)
                        }
                        errorReader.close()
                        Log.e("WooCommerceApiImpl", "【Android 7 HTTP方案】错误响应: ${errorResponse.toString()}")
                    }
                } catch (e: Exception) {
                    Log.e("WooCommerceApiImpl", "【Android 7 HTTP方案】请求执行异常: ${e.message}", e)
                    Log.e("WooCommerceApiImpl", "【Android 7 HTTP方案】异常类型: ${e.javaClass.name}", e)
                    e.printStackTrace()
                } finally {
                    connection.disconnect()
                    Log.d("WooCommerceApiImpl", "【Android 7 HTTP方案】连接已断开")
                }
            } catch (e: Exception) {
                Log.e("WooCommerceApiImpl", "【Android 7 HTTP方案】整体异常: ${e.message}", e)
                Log.e("WooCommerceApiImpl", "【Android 7 HTTP方案】异常类型: ${e.javaClass.name}", e)
                e.printStackTrace()
            }
            
            Log.d("WooCommerceApiImpl", "【Android 7 HTTP方案】无法通过HTTP直接请求获取数据，回退到标准HTTPS方法")
        }
        
        Log.d("WooCommerceApiImpl", "【API执行】开始处理 $requestType 请求: $endpoint")
        
        while (currentRetry < maxRetries) {
            try {
                return withContext(Dispatchers.IO) {
                    try {
                        // 如果不是第一次尝试，添加延迟
                        if (currentRetry > 0) {
                            val delayMs = currentRetry * 1000L
                            Log.d("WooCommerceApiImpl", "【重试】第 ${currentRetry+1} 次尝试，延迟 $delayMs ms")
                            delay(delayMs)
                        }
                        
                        val baseUrl = buildBaseUrl()
                        // 确保不存在双斜杠
                        val cleanEndpoint = if (endpoint.startsWith("/")) endpoint.substring(1) else endpoint
                        val fullApiUrl = "$baseUrl$cleanEndpoint"
                        Log.d("WooCommerceApiImpl", "【URL构建】$requestType 请求基本URL: $baseUrl, 终端点: $cleanEndpoint")
                        Log.d("WooCommerceApiImpl", "【URL构建】完整URL: $fullApiUrl, 参数数量: ${queryParams.size}")
                        
                        val urlBuilder = try {
                            fullApiUrl.toHttpUrl().newBuilder()
                        } catch (e: IllegalArgumentException) {
                            Log.e("WooCommerceApiImpl", "【URL错误】无效URL: $fullApiUrl, 错误: ${e.message}")
                            throw Exception("API URL格式无效，请检查站点URL设置: ${e.message}")
                        }
                        
                        // 添加查询参数
                        for ((key, value) in queryParams) {
                            urlBuilder.addQueryParameter(key, value)
                        }
                        
                        // 添加认证参数
                        addAuthParams(urlBuilder)
                        
                        // 构建最终URL和请求
                        val url = urlBuilder.build()
                        
                        // 如果是Android 7设备的订单请求，尝试使用替代方法构建请求
                        val request = if (isAndroid7 && isOrderRequest) {
                            Log.d("WooCommerceApiImpl", "【Android 7】使用特殊的请求构建方法")
                            Request.Builder()
                                .url(url)
                                .get()
                                .header("Accept", "application/json")
                                .header("Connection", "close") // 禁用连接保持活动
                                .build()
                        } else {
                            Request.Builder()
                                .url(url)
                                .get()
                                .build()
                        }
                        
                        Log.d("WooCommerceApiImpl", "【API请求】${request.method} ${request.url}")
                        
                        // 记录请求开始时间
                        val startTime = System.currentTimeMillis()
                        
                        // 执行网络请求
                        val response = try {
                            val call = client.newCall(request)
                            Log.d("WooCommerceApiImpl", "【网络】开始执行 ${requestType} 请求，使用TLS自动配置")
                            
                            // 对于Android 7上的订单请求，添加额外的异常处理
                            if (isAndroid7 && isOrderRequest) {
                                try {
                                    call.execute()
                                } catch (e: javax.net.ssl.SSLException) {
                                    Log.e("WooCommerceApiImpl", "【Android 7 SSL错误】订单请求尝试特殊处理: ${e.message}")
                                    
                                    // 创建新的客户端实例并重试
                                    val retryClient = client.newBuilder()
                                        .connectTimeout(30, TimeUnit.SECONDS)
                                        .readTimeout(30, TimeUnit.SECONDS)
                                        .build()
                                    retryClient.newCall(request).execute()
                                }
                            } else {
                                // 正常执行
                                call.execute()
                            }
                        } catch (e: javax.net.ssl.SSLHandshakeException) {
                            val errorMsg = e.message ?: "未知SSL握手错误"
                            Log.e("WooCommerceApiImpl", "【SSL错误】${requestType} 请求SSL握手失败: $errorMsg", e)
                            // 记录更多上下文信息，便于诊断
                            val cause = e.cause?.message ?: "无明确原因"
                            Log.e("WooCommerceApiImpl", "【SSL详细】原因: $cause, 请求URL: ${request.url}, 重试次数: $currentRetry")
                            throw Exception("SSL安全连接失败: $errorMsg")
                        } catch (e: javax.net.ssl.SSLException) {
                            val errorMsg = e.message ?: "未知SSL连接错误"
                            Log.e("WooCommerceApiImpl", "【SSL错误】${requestType} 请求SSL连接异常: $errorMsg", e)
                            // 记录更多上下文信息，便于诊断
                            val cause = e.cause?.message ?: "无明确原因"
                            Log.e("WooCommerceApiImpl", "【SSL详细】原因: $cause, 请求URL: ${request.url}, 重试次数: $currentRetry")
                            throw Exception("SSL连接异常: $errorMsg")
                        } catch (e: java.io.IOException) {
                            Log.e("WooCommerceApiImpl", "【IO错误】${requestType} 请求IO异常: ${e.message}", e)
                            throw Exception("网络IO异常: ${e.message}")
                        } catch (e: Exception) {
                            Log.e("WooCommerceApiImpl", "【网络错误】${requestType} 请求失败: ${e.message}", e)
                            throw e
                        }
                        
                        val duration = System.currentTimeMillis() - startTime
                        
                        val responseBody = response.body?.string() ?: throw Exception("响应体为空")
                        
                        if (endpoint.contains("orders") || endpoint.contains("products")) {
                            Log.d("WooCommerceApiImpl", "【API响应】$requestType 响应状态码: ${response.code}, 耗时: ${duration}ms, 大小: ${responseBody.length}字节")
                        }
                        
                        if (!response.isSuccessful) {
                            Log.e("WooCommerceApiImpl", "【请求失败】${requestType} 请求失败 - 状态码: ${response.code}, 响应: $responseBody")
                            throw ApiError.fromHttpCode(response.code, responseBody)
                        }
                        
                        try {
                            // 确定要使用的类型和解析策略
                            val isOrdersList = T::class.java == List::class.java && endpoint.contains("orders")
                            
                            // 使用注册了自定义适配器的gson直接解析
                            val result = when {
                                isOrdersList -> {
                                    // 订单列表使用我们注册的专用适配器
                                    try {
                                        Log.d("WooCommerceApiImpl", "【解析】使用订单列表适配器解析")
                                        gson.fromJson<T>(responseBody, orderDtoListType)
                                    } catch (e: Exception) {
                                        Log.e("WooCommerceApiImpl", "【解析错误】标准解析订单列表失败，尝试备用方式: ${e.message}")
                                        // 备用解析方式
                                        @Suppress("UNCHECKED_CAST")
                                        extractOrdersManually(responseBody) as T
                                    }
                                }
                                else -> {
                                    // 其他类型使用标准解析
                                    Log.d("WooCommerceApiImpl", "【解析】使用标准适配器解析 ${requestType} 数据")
                                    gson.fromJson<T>(responseBody, object : TypeToken<T>() {}.type)
                                }
                            }
                            
                            // 只为订单列表记录简要信息
                            if (isOrdersList) {
                                val count = (result as List<*>).size
                                Log.d("WooCommerceApiImpl", "【成功】获取 $count 个订单")
                            } else if (isProductRequest) {
                                if (result is List<*>) {
                                    val count = result.size
                                    Log.d("WooCommerceApiImpl", "【成功】获取 $count 个产品")
                                } else {
                                    Log.d("WooCommerceApiImpl", "【成功】获取产品详情")
                                }
                            }
                            
                            return@withContext result
                        } catch (e: Exception) {
                            Log.e("WooCommerceApiImpl", "【解析错误】解析响应失败: ${e.message}", e)
                            throw Exception("解析API响应失败: ${e.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("WooCommerceApiImpl", "执行请求异常: ${e.message}", e)
                        throw e
                    }
                }
            } catch (e: java.io.IOException) {
                Log.e("WooCommerceApiImpl", "IO错误，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                lastException = e
                currentRetry++
            } catch (e: javax.net.ssl.SSLHandshakeException) {
                Log.e("WooCommerceApiImpl", "【SSL错误】SSL握手失败，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}", e)
                lastException = e
                currentRetry++
            } catch (e: javax.net.ssl.SSLException) {
                Log.e("WooCommerceApiImpl", "【SSL错误】SSL连接异常，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}", e)
                lastException = e
                currentRetry++
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
        // 是否是订单相关请求
        val isOrderRequest = endpoint.contains("orders")
        val isProductRequest = endpoint.contains("products")
        
        // 记录请求类型，用于调试
        val requestType = when {
            isOrderRequest -> "订单"
            isProductRequest -> "产品"
            else -> "其他"
        }
        
        // 最大重试次数
        val maxRetries = 3
        var currentRetry = 0
        var lastException: Exception? = null
        
        while (currentRetry < maxRetries) {
            try {
                return withContext(Dispatchers.IO) {
                    try {
                        // 如果不是第一次尝试，添加延迟
                        if (currentRetry > 0) {
                            val delayMs = currentRetry * 1000L
                            Log.d("WooCommerceApiImpl", "【POST请求】重试，延迟 $delayMs ms")
                            delay(delayMs)
                        }
                        
                        val baseUrl = buildBaseUrl()
                        val cleanEndpoint = if (endpoint.startsWith("/")) endpoint.substring(1) else endpoint
                        val fullApiUrl = "$baseUrl$cleanEndpoint"
                        
                        val urlBuilder = try {
                            fullApiUrl.toHttpUrl().newBuilder()
                        } catch (e: IllegalArgumentException) {
                            Log.e("WooCommerceApiImpl", "【URL错误】无效URL: $fullApiUrl")
                            throw Exception("API URL格式无效，请检查站点URL设置: ${e.message}")
                        }
                        
                        // 添加认证参数
                        addAuthParams(urlBuilder)
                        
                        val jsonBody = gson.toJson(body)
                        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                        
                        val request = Request.Builder()
                            .url(urlBuilder.build())
                            .post(requestBody)
                            .build()
                        
                        Log.d("WooCommerceApiImpl", "【API请求】POST ${urlBuilder.build()} - $jsonBody")
                        
                        // 记录请求开始时间
                        val startTime = System.currentTimeMillis()
                        
                        // 执行网络请求
                        val response = try {
                            val call = client.newCall(request)
                            Log.d("WooCommerceApiImpl", "【网络】开始执行 ${requestType} 请求，使用TLS自动配置")
                            call.execute()
                        } catch (e: javax.net.ssl.SSLHandshakeException) {
                            val errorMsg = e.message ?: "未知SSL握手错误"
                            Log.e("WooCommerceApiImpl", "【SSL错误】${requestType} 请求SSL握手失败: $errorMsg", e)
                            // 记录更多上下文信息，便于诊断
                            val cause = e.cause?.message ?: "无明确原因"
                            Log.e("WooCommerceApiImpl", "【SSL详细】原因: $cause, 请求URL: ${request.url}, 重试次数: $currentRetry")
                            throw Exception("SSL安全连接失败: $errorMsg")
                        } catch (e: javax.net.ssl.SSLException) {
                            val errorMsg = e.message ?: "未知SSL连接错误"
                            Log.e("WooCommerceApiImpl", "【SSL错误】${requestType} 请求SSL连接异常: $errorMsg", e)
                            // 记录更多上下文信息，便于诊断
                            val cause = e.cause?.message ?: "无明确原因"
                            Log.e("WooCommerceApiImpl", "【SSL详细】原因: $cause, 请求URL: ${request.url}, 重试次数: $currentRetry")
                            throw Exception("SSL连接异常: $errorMsg")
                        } catch (e: java.io.IOException) {
                            Log.e("WooCommerceApiImpl", "【IO错误】${requestType} 请求IO异常: ${e.message}", e)
                            throw Exception("网络IO异常: ${e.message}")
                        } catch (e: Exception) {
                            Log.e("WooCommerceApiImpl", "【网络错误】${requestType} 请求失败: ${e.message}", e)
                            throw e
                        }
                        
                        val duration = System.currentTimeMillis() - startTime
                        
                        val responseBody = response.body?.string() ?: throw Exception("响应体为空")
                        
                        Log.d("WooCommerceApiImpl", "【POST响应】状态码: ${response.code}, 耗时: ${duration}ms, 大小: ${responseBody.length}字节")
                        
                        if (!response.isSuccessful) {
                            Log.e("WooCommerceApiImpl", "【POST错误】HTTP错误: ${response.code}, 响应: $responseBody")
                            throw ApiError.fromHttpCode(response.code, responseBody)
                        }
                        
                        // 使用精确的类型解析方式
                        val result = try {
                            when {
                                T::class == OrderDto::class -> gson.fromJson(responseBody, OrderDto::class.java) as T
                                T::class == ProductDto::class -> gson.fromJson(responseBody, ProductDto::class.java) as T
                                T::class == CategoryDto::class -> gson.fromJson(responseBody, CategoryDto::class.java) as T
                                else -> gson.fromJson(responseBody, object : TypeToken<T>() {}.type)
                            }
                        } catch (e: Exception) {
                            Log.e("WooCommerceApiImpl", "【POST请求】解析响应失败: ${e.message}", e)
                            throw Exception("解析API响应失败: ${e.message}")
                        }
                        
                        return@withContext result
                    } catch (e: Exception) {
                        Log.e("WooCommerceApiImpl", "【POST请求】执行异常: ${e.message}", e)
                        throw e
                    }
                }
            } catch (e: java.io.IOException) {
                Log.e("WooCommerceApiImpl", "【POST请求】IO错误，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                lastException = e
                currentRetry++
            } catch (e: javax.net.ssl.SSLHandshakeException) {
                Log.e("WooCommerceApiImpl", "【POST请求】SSL握手失败，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                lastException = e
                currentRetry++
            } catch (e: javax.net.ssl.SSLException) {
                Log.e("WooCommerceApiImpl", "【POST请求】SSL连接异常，尝试重试 (${currentRetry+1}/$maxRetries): ${e.message}")
                lastException = e
                currentRetry++
            } catch (e: Exception) {
                if (e is ApiError) {
                    throw e
                } else {
                    Log.e("WooCommerceApiImpl", "【POST请求】其他错误: ${e.message}")
                    throw ApiError.fromException(e)
                }
            }
        }
        
        // 所有重试都失败
        Log.e("WooCommerceApiImpl", "【POST请求】所有重试都失败，放弃请求")
        throw lastException ?: Exception("POST请求失败，已尝试 $maxRetries 次")
    }
    
    override suspend fun getProducts(page: Int, perPage: Int): List<ProductDto> {
        val queryParams = mapOf(
            "page" to page.toString(),
            "per_page" to perPage.toString()
        )
        
        // 添加调试标志，与订单API保持一致的日志
        val isDevMode = true // 调试模式标记
        Log.d("WooCommerceApiImpl", "【API】调用getProducts方法 - 调试模式: $isDevMode")
        Log.d("WooCommerceApiImpl", "【API请求】请求产品，页码: $page, 每页数量: $perPage")
        
        // 使用与订单API相同风格的简单调用
        return try {
            Log.d("WooCommerceApiImpl", "【API】开始执行产品查询")
            val result = executeGetRequest<List<ProductDto>>("products", queryParams)
            Log.d("WooCommerceApiImpl", "【API响应】获取到 ${result.size} 个产品")
            result
        } catch (e: Exception) {
            Log.e("WooCommerceApiImpl", "【API错误】获取产品失败: ${e.message}", e)
            throw e
        }
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
        
        // 添加调试标志，便于识别方法调用来源
        val isDevMode = true // 调试模式标记
        Log.d("WooCommerceApiImpl", "【API】调用getOrders方法 - 调试模式: $isDevMode")
        
        // 记录请求的状态，并进行处理
        val requestedStatus = status?.trim()?.lowercase()
        Log.d("WooCommerceApiImpl", "【API请求】请求订单，原始状态: $status, 处理后: $requestedStatus, 页码: $page")
        
        // 添加状态参数到请求 - 确保格式正确（WooCommerce API期望单个字符串，不是数组）
        if (!requestedStatus.isNullOrBlank()) {
            Log.d("WooCommerceApiImpl", "【API请求】添加状态过滤: '$requestedStatus'")
            queryParams["status"] = requestedStatus  // 直接使用状态值
        }
        
        // 使用更简单的调用方式，与产品API保持一致
        return try {
            Log.d("WooCommerceApiImpl", "【API】开始执行订单查询")
            val result = executeGetRequest<List<OrderDto>>("orders", queryParams)
            Log.d("WooCommerceApiImpl", "【API响应】获取到 ${result.size} 个订单")
            result
        } catch (e: Exception) {
            Log.e("WooCommerceApiImpl", "【API错误】获取订单失败: ${e.message}", e)
            throw e
        }
    }
    
    override suspend fun getOrder(id: Long): OrderDto {
        Log.d("WooCommerceApiImpl", "【API请求】获取订单详情: ID=$id")
        
        // 检测是否是Android 7设备
        val isAndroid7 = android.os.Build.VERSION.SDK_INT in 24..25
        if (isAndroid7) {
            Log.d("WooCommerceApiImpl", "【Android 7】在Android 7设备上获取订单详情，将使用特殊处理逻辑")
            
            // 首先尝试使用HTTP直接请求方式获取订单详情
            try {
                Log.d("WooCommerceApiImpl", "【Android 7特殊方案】尝试使用HTTP直接请求获取订单详情")
                
                val baseUrl = buildBaseUrl().replace("https://", "http://")
                val endpoint = "orders/$id"
                val fullApiUrl = "$baseUrl$endpoint"
                
                // 构建完整URL，包括查询参数
                val urlStr = StringBuilder(fullApiUrl)
                urlStr.append("?")
                // 添加认证参数
                urlStr.append("consumer_key=${config.consumerKey.trim()}&")
                urlStr.append("consumer_secret=${config.consumerSecret.trim()}")
                
                Log.d("WooCommerceApiImpl", "【Android 7特殊方案】准备直接请求: $urlStr")
                
                // 执行HTTP请求
                val url = java.net.URL(urlStr.toString())
                val startTime = System.currentTimeMillis()
                
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Connection", "close")
                
                try {
                    val responseCode = connection.responseCode
                    val duration = System.currentTimeMillis() - startTime
                    
                    if (responseCode == 200) {
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream))
                        val response = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                        reader.close()
                        
                        val responseBody = response.toString()
                        Log.d("WooCommerceApiImpl", "【Android 7特殊方案】成功获取订单详情，响应大小: ${responseBody.length}字节，耗时: ${duration}ms")
                        
                        // 解析响应
                        val result = gson.fromJson(responseBody, OrderDto::class.java)
                        
                        Log.d("WooCommerceApiImpl", "【Android 7特殊方案】订单详情解析成功: #${result.number}")
                        return result
                    } else {
                        Log.e("WooCommerceApiImpl", "【Android 7特殊方案】HTTP请求失败: 状态码 $responseCode")
                        // 读取错误响应
                        val errorStream = connection.errorStream ?: java.io.ByteArrayInputStream(ByteArray(0))
                        val errorReader = java.io.BufferedReader(java.io.InputStreamReader(errorStream))
                        val errorResponse = StringBuilder()
                        var line: String?
                        while (errorReader.readLine().also { line = it } != null) {
                            errorResponse.append(line)
                        }
                        errorReader.close()
                        
                        Log.e("WooCommerceApiImpl", "【Android 7特殊方案】错误响应: ${errorResponse.toString()}")
                    }
                } catch (e: Exception) {
                    Log.e("WooCommerceApiImpl", "【Android 7特殊方案】请求执行异常: ${e.message}", e)
                } finally {
                    connection.disconnect()
                }
                
                Log.d("WooCommerceApiImpl", "【Android 7特殊方案】无法通过HTTP直接请求获取订单详情，回退到标准方法")
            } catch (e: Exception) {
                Log.e("WooCommerceApiImpl", "【Android 7特殊方案】整体异常: ${e.message}", e)
            }
        }
        
        // 特别处理Android 7上的SSL/TLS问题
        // 为订单详情获取添加重试和健壮性处理
        val maxRetries = 3
        var attempt = 0
        var lastException: Exception? = null
        
        while (attempt < maxRetries) {
            try {
                Log.d("WooCommerceApiImpl", "【订单详情API】尝试获取订单详情，尝试 ${attempt+1}/$maxRetries")
                
                // 使用标准executeGetRequest方法前添加延迟
                if (attempt > 0) {
                    val delayMs = 1000L * attempt
                    Log.d("WooCommerceApiImpl", "【订单详情API】重试前延迟 ${delayMs}ms")
                    delay(delayMs)
                }
                
                // 执行请求获取订单
                val result = executeGetRequest<OrderDto>("orders/$id")
                Log.d("WooCommerceApiImpl", "【API响应】成功获取订单 #${result.number}")
                return result
            } catch (e: Exception) {
                attempt++
                lastException = e
                
                // 根据不同错误类型执行不同的重试策略
                when (e) {
                    is javax.net.ssl.SSLHandshakeException -> {
                        Log.e("WooCommerceApiImpl", "【订单详情API错误】SSL握手失败 (${attempt}/$maxRetries): ${e.message}", e)
                    }
                    is javax.net.ssl.SSLException -> {
                        Log.e("WooCommerceApiImpl", "【订单详情API错误】SSL连接异常 (${attempt}/$maxRetries): ${e.message}", e)
                    }
                    is java.io.IOException -> {
                        Log.e("WooCommerceApiImpl", "【订单详情API错误】IO异常 (${attempt}/$maxRetries): ${e.message}", e)
                    }
                    else -> {
                        Log.e("WooCommerceApiImpl", "【订单详情API错误】其他错误 (${attempt}/$maxRetries): ${e.message}", e)
                        if (attempt == maxRetries) {
                            throw e // 对于其他错误，达到最大重试次数直接抛出
                        }
                    }
                }
            }
        }
        
        // 所有重试都失败，抛出最后一个异常
        throw lastException ?: Exception("获取订单详情失败，已尝试 $maxRetries 次")
    }
    
    override suspend fun updateOrder(id: Long, data: Map<String, Any>): OrderDto {
        Log.d("WooCommerceApiImpl", "【API请求】更新订单: ID=$id, 数据=$data")
        
        // 检测是否是Android 7设备
        val isAndroid7 = android.os.Build.VERSION.SDK_INT in 24..25
        if (isAndroid7) {
            Log.d("WooCommerceApiImpl", "【Android 7】在Android 7设备上更新订单，将使用特殊处理逻辑")
            
            // 首先尝试使用HTTP直接请求方式更新订单
            try {
                Log.d("WooCommerceApiImpl", "【Android 7特殊方案】尝试使用HTTP直接请求更新订单")
                
                val baseUrl = buildBaseUrl().replace("https://", "http://")
                val endpoint = "orders/$id"
                val fullApiUrl = "$baseUrl$endpoint"
                
                // 构建完整URL，包括查询参数
                val urlStr = StringBuilder(fullApiUrl)
                urlStr.append("?")
                // 添加认证参数
                urlStr.append("consumer_key=${config.consumerKey.trim()}&")
                urlStr.append("consumer_secret=${config.consumerSecret.trim()}")
                
                Log.d("WooCommerceApiImpl", "【Android 7特殊方案】准备直接请求: $urlStr")
                
                // 执行HTTP请求
                val url = java.net.URL(urlStr.toString())
                val startTime = System.currentTimeMillis()
                
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Connection", "close")
                
                try {
                    // 写入请求体
                    val jsonBody = gson.toJson(data)
                    val outputStream = connection.outputStream
                    outputStream.write(jsonBody.toByteArray())
                    outputStream.close()
                    
                    val responseCode = connection.responseCode
                    val duration = System.currentTimeMillis() - startTime
                    
                    if (responseCode == 200 || responseCode == 201) {
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream))
                        val response = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                        reader.close()
                        
                        val responseBody = response.toString()
                        Log.d("WooCommerceApiImpl", "【Android 7特殊方案】成功更新订单，响应大小: ${responseBody.length}字节，耗时: ${duration}ms")
                        
                        // 解析响应
                        val result = gson.fromJson(responseBody, OrderDto::class.java)
                        return result
                    } else {
                        Log.e("WooCommerceApiImpl", "【Android 7特殊方案】HTTP请求失败: 状态码 $responseCode")
                        // 读取错误响应
                        val errorStream = connection.errorStream ?: java.io.ByteArrayInputStream(ByteArray(0))
                        val errorReader = java.io.BufferedReader(java.io.InputStreamReader(errorStream))
                        val errorResponse = StringBuilder()
                        var line: String?
                        while (errorReader.readLine().also { line = it } != null) {
                            errorResponse.append(line)
                        }
                        errorReader.close()
                        
                        Log.e("WooCommerceApiImpl", "【Android 7特殊方案】错误响应: ${errorResponse.toString()}")
                    }
                } catch (e: Exception) {
                    Log.e("WooCommerceApiImpl", "【Android 7特殊方案】请求执行异常: ${e.message}", e)
                } finally {
                    connection.disconnect()
                }
                
                Log.d("WooCommerceApiImpl", "【Android 7特殊方案】无法通过HTTP直接请求更新订单，回退到标准方法")
            } catch (e: Exception) {
                Log.e("WooCommerceApiImpl", "【Android 7特殊方案】整体异常: ${e.message}", e)
            }
        }
        
        // 添加专门的错误处理和重试逻辑，与getOrder类似
        val maxRetries = 3
        var attempt = 0
        var lastException: Exception? = null
        
        while (attempt < maxRetries) {
            try {
                Log.d("WooCommerceApiImpl", "【订单更新API】尝试更新订单，尝试 ${attempt+1}/$maxRetries")
                
                // 使用标准executePostRequest方法前添加延迟
                if (attempt > 0) {
                    val delayMs = 1000L * attempt
                    Log.d("WooCommerceApiImpl", "【订单更新API】重试前延迟 ${delayMs}ms")
                    delay(delayMs)
                }
                
                // 执行请求更新订单
                // 对于Android 7设备，尝试使用特殊方法
                val result = if (isAndroid7 && attempt > 0) {
                    Log.d("WooCommerceApiImpl", "【Android 7】尝试使用替代方法更新订单")
                    
                    // 为Android 7构建一个特殊的请求
                    val baseUrl = buildBaseUrl()
                    val endpoint = "orders/$id"
                    val fullApiUrl = "$baseUrl$endpoint"
                    
                    val urlBuilder = fullApiUrl.toHttpUrl().newBuilder()
                    // 添加认证参数
                    addAuthParams(urlBuilder)
                    
                    val jsonBody = gson.toJson(data)
                    val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                    
                    val request = Request.Builder()
                        .url(urlBuilder.build())
                        .header("Accept", "application/json")
                        .header("Connection", "close") // 禁用连接保持活动
                        .put(requestBody) // 尝试使用PUT而不是POST
                        .build()
                    
                    // 创建特殊的OkHttpClient实例
                    val specialClient = client.newBuilder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build()
                    
                    // 执行请求
                    val response = specialClient.newCall(request).execute()
                    val responseBody = response.body?.string() ?: throw Exception("响应体为空")
                    
                    if (!response.isSuccessful) {
                        throw ApiError.fromHttpCode(response.code, responseBody)
                    }
                    
                    // 解析响应
                    gson.fromJson(responseBody, OrderDto::class.java)
                } else {
                    // 普通方法
                    executePostRequest<OrderDto>("orders/$id", data)
                }
                
                Log.d("WooCommerceApiImpl", "【API响应】成功更新订单 #${result.number}")
                return result
            } catch (e: Exception) {
                attempt++
                lastException = e
                
                // 根据不同错误类型执行不同的重试策略
                when (e) {
                    is javax.net.ssl.SSLHandshakeException -> {
                        Log.e("WooCommerceApiImpl", "【订单更新API错误】SSL握手失败 (${attempt}/$maxRetries): ${e.message}", e)
                    }
                    is javax.net.ssl.SSLException -> {
                        Log.e("WooCommerceApiImpl", "【订单更新API错误】SSL连接异常 (${attempt}/$maxRetries): ${e.message}", e)
                    }
                    is java.io.IOException -> {
                        Log.e("WooCommerceApiImpl", "【订单更新API错误】IO异常 (${attempt}/$maxRetries): ${e.message}", e)
                    }
                    else -> {
                        Log.e("WooCommerceApiImpl", "【订单更新API错误】其他错误 (${attempt}/$maxRetries): ${e.message}", e)
                        if (attempt == maxRetries) {
                            throw e // 对于其他错误，达到最大重试次数直接抛出
                        }
                    }
                }
            }
        }
        
        // 所有重试都失败，抛出最后一个异常
        throw lastException ?: Exception("更新订单失败，已尝试 $maxRetries 次")
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